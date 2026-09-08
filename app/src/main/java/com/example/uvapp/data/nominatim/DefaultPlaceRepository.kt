package com.example.uvapp.data.nominatim

import com.example.uvapp.data.db.PlaceNameDao
import com.example.uvapp.data.db.toDomain
import com.example.uvapp.data.db.toEntity
import com.example.uvapp.domain.model.Coordinates
import com.example.uvapp.domain.model.PlaceName
import com.example.uvapp.domain.repository.PlaceRepository
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException

/** Reverse-geocodes coordinates while caching results and respecting public API limits. */
class DefaultPlaceRepository internal constructor(
    private val api: NominatimApi,
    private val dao: PlaceNameDao,
    private val rateLimiter: NominatimRateLimiter = NominatimRateLimiter.shared,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PlaceRepository {
    override suspend fun reverseGeocode(coordinates: Coordinates): Result<PlaceName> {
        return try {
            require(coordinates.latitude in -90.0..90.0) { "Latitude must be between -90 and 90" }
            require(coordinates.longitude in -180.0..180.0) { "Longitude must be between -180 and 180" }
            val locationKey = locationKey(coordinates)
            val cached = findCachedPlaceWithinReuseDistance(coordinates)
            if (cached != null) {
                return Result.success(cached.toDomain())
            }

            rateLimiter.run {
                // Another caller may have cached this location while we waited.
                val cachedAfterWait = findCachedPlaceWithinReuseDistance(coordinates)
                if (cachedAfterWait != null) {
                    return@run cachedAfterWait.toDomain()
                }

                val place =
                    api
                        .reverseGeocode(
                            latitude = coordinates.latitude,
                            longitude = coordinates.longitude,
                        ).toPlaceName()
                dao.upsert(
                    place.toEntity(
                        locationKey = locationKey,
                        latitude = coordinates.latitude,
                        longitude = coordinates.longitude,
                        fetchedAtMillis = nowMillis(),
                    ),
                )
                place
            }.let(Result.Companion::success)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private suspend fun findCachedPlaceWithinReuseDistance(
        coordinates: Coordinates,
    ) = dao
        .getPlaceNames()
        .map { cached ->
            cached to
                distanceMeters(
                    firstLatitude = coordinates.latitude,
                    firstLongitude = coordinates.longitude,
                    secondLatitude = cached.latitude,
                    secondLongitude = cached.longitude,
                )
        }.minByOrNull { (_, distance) -> distance }
        ?.takeIf { (_, distance) -> distance <= CACHE_REUSE_DISTANCE_METERS }
        ?.first

    private fun locationKey(coordinates: Coordinates): String =
        String.format(Locale.ROOT, "%.5f,%.5f", coordinates.latitude, coordinates.longitude)

    private fun distanceMeters(
        firstLatitude: Double,
        firstLongitude: Double,
        secondLatitude: Double,
        secondLongitude: Double,
    ): Double {
        val firstLatitudeRadians = Math.toRadians(firstLatitude)
        val secondLatitudeRadians = Math.toRadians(secondLatitude)
        val latitudeDelta = Math.toRadians(secondLatitude - firstLatitude)
        val longitudeDelta = Math.toRadians(secondLongitude - firstLongitude)
        val haversine = (
            sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
                cos(firstLatitudeRadians) * cos(secondLatitudeRadians) *
                sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        ).coerceIn(0.0, 1.0)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    }

    private companion object {
        const val CACHE_REUSE_DISTANCE_METERS = 1_000.0
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
