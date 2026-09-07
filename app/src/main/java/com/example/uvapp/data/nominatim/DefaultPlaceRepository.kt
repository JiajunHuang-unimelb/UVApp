package com.example.uvapp.data.nominatim

import com.example.uvapp.data.db.PlaceNameDao
import com.example.uvapp.data.db.toDomain
import com.example.uvapp.data.db.toEntity
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
            val cached = dao.getPlaceName(locationKey)
            if (cached != null) {
                return Result.success(cached.toDomain())
            }

            rateLimiter.run {
                // Another caller may have cached this location while we waited.
                val cachedAfterWait = dao.getPlaceName(locationKey)
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

    private fun locationKey(coordinates: Coordinates): String =
        String.format(Locale.ROOT, "%.3f,%.3f", coordinates.latitude, coordinates.longitude)
}
