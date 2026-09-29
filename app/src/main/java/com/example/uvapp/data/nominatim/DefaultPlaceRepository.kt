package com.example.uvapp.data.nominatim

import com.example.uvapp.data.db.PlaceNameDao
import com.example.uvapp.data.db.PlaceNameEntity
import com.example.uvapp.data.db.toDomain
import com.example.uvapp.data.db.toEntity
import com.example.uvapp.domain.location.distanceMeters
import com.example.uvapp.domain.model.Coordinates
import com.example.uvapp.domain.model.PlaceName
import com.example.uvapp.domain.repository.PlaceRepository
import java.util.Locale
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
    ): PlaceNameEntity? {
        val cachedDistances =
            dao.getPlaceNames().map { cached ->
                cached to
                    distanceMeters(
                        firstLatitude = coordinates.latitude,
                        firstLongitude = coordinates.longitude,
                        secondLatitude = cached.latitude,
                        secondLongitude = cached.longitude,
                    )
            }
        val nearest = cachedDistances.minByOrNull { (_, distance) -> distance } ?: return null
        val (cached, distance) = nearest

        return if (distance <= CACHE_REUSE_DISTANCE_METERS) cached else null
    }

    private fun locationKey(coordinates: Coordinates): String =
        String.format(Locale.ROOT, "%.5f,%.5f", coordinates.latitude, coordinates.longitude)

    private companion object {
        const val CACHE_REUSE_DISTANCE_METERS = 1_000.0
    }
}
