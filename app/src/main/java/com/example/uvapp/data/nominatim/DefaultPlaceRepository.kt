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
    private val mapper: NominatimMapper,
    private val dao: PlaceNameDao,
    private val rateLimiter: NominatimRateLimiter = NominatimRateLimiter.shared,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : PlaceRepository {
    override suspend fun reverseGeocode(
        coordinates: Coordinates,
        forceRefresh: Boolean,
    ): Result<PlaceName> {
        val validationError = validate(coordinates)
        if (validationError != null) {
            return Result.failure(validationError)
        }

        val locationKey = locationKey(coordinates)
        val cached = readCache(locationKey)
        if (!forceRefresh && cached != null) {
            return Result.success(cached)
        }

        return try {
            rateLimiter.run {
                val secondCacheCheck = readCache(locationKey)
                if (!forceRefresh && secondCacheCheck != null) {
                    return@run secondCacheCheck
                }

                val place =
                    mapper.toDomain(
                        api.reverseGeocode(
                            latitude = coordinates.latitude,
                            longitude = coordinates.longitude,
                        ),
                    )
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
            cached?.let(Result.Companion::success) ?: Result.failure(error)
        }
    }

    private suspend fun readCache(locationKey: String): PlaceName? =
        try {
            dao.getPlaceName(locationKey)?.toDomain()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }

    private fun validate(coordinates: Coordinates): IllegalArgumentException? =
        when {
            coordinates.latitude !in -90.0..90.0 -> IllegalArgumentException("Latitude must be between -90 and 90")
            coordinates.longitude !in -180.0..180.0 ->
                IllegalArgumentException("Longitude must be between -180 and 180")
            else -> null
        }

    private fun locationKey(coordinates: Coordinates): String =
        String.format(Locale.ROOT, "%.3f,%.3f", coordinates.latitude, coordinates.longitude)
}
