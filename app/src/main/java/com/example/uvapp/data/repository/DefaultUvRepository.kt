package com.example.uvapp.data.repository

import com.example.uvapp.data.db.UvReadingDao
import com.example.uvapp.data.db.CachedForecastLocation
import com.example.uvapp.data.db.toDomain
import com.example.uvapp.data.db.toEntity
import com.example.uvapp.data.openmeteo.OpenMeteoApi
import com.example.uvapp.data.openmeteo.toForecastReadings
import com.example.uvapp.domain.model.UvDataSource
import com.example.uvapp.domain.model.UvForecastState
import com.example.uvapp.domain.repository.UvRepository
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Offline-first repository backed by Room and refreshed from Open-Meteo. */
class DefaultUvRepository(
    private val api: OpenMeteoApi,
    private val dao: UvReadingDao,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : UvRepository {
    private val refreshStatuses = MutableStateFlow<Map<String, RefreshStatus>>(emptyMap())

    override fun observeForecast(
        latitude: Double,
        longitude: Double,
    ): Flow<UvForecastState> =
        flow {
            val locationKey = resolveLocationKey(latitude, longitude)
            emitAll(observeForecast(locationKey))
        }

    private fun observeForecast(locationKey: String): Flow<UvForecastState> {
        val refreshStatus =
            refreshStatuses
                .map { statuses -> statuses[locationKey] ?: RefreshStatus() }
                .distinctUntilChanged()

        return combine(
            dao.observeForecast(locationKey),
            refreshStatus,
        ) { entities, status ->
            val lastUpdatedMillis = entities.maxOfOrNull { it.fetchedAtMillis }
            val source =
                when {
                    entities.isEmpty() -> UvDataSource.NONE
                    status.isRefreshing || status.errorMessage != null -> UvDataSource.CACHE
                    status.lastNetworkWriteMillis == lastUpdatedMillis -> UvDataSource.NETWORK
                    else -> UvDataSource.CACHE
                }

            UvForecastState(
                readings = entities.map { entity -> entity.toDomain() },
                source = source,
                lastUpdatedMillis = lastUpdatedMillis,
                isRefreshing = status.isRefreshing,
                errorMessage = status.errorMessage,
            )
        }
            .distinctUntilChanged()
    }

    override suspend fun refresh(
        latitude: Double,
        longitude: Double,
        force: Boolean,
    ): Result<Unit> {
        val locationKey = resolveLocationKey(latitude, longitude)
        val currentTime = nowMillis()

        try {
            val latestFetchTime = dao.latestFetchTime(locationKey)
            // User-initiated refreshes must not bypass the public service limit.
            if (latestFetchTime != null && isFresh(latestFetchTime, currentTime)) {
                updateRefreshStatus(locationKey) { status ->
                    status.copy(
                        isRefreshing = false,
                        errorMessage = null,
                    )
                }
                return Result.success(Unit)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            updateRefreshStatus(locationKey) { status ->
                status.copy(errorMessage = error.message ?: "Unable to read the UV cache")
            }
            return Result.failure(error)
        }

        updateRefreshStatus(locationKey) { status ->
            status.copy(
                isRefreshing = true,
                errorMessage = null,
            )
        }

        return try {
            val response = api.getUvForecast(latitude, longitude)
            val readings = response.toForecastReadings()
            val fetchedAtMillis = nowMillis()
            val entities =
                readings.map { reading ->
                    reading.toEntity(
                        locationKey = locationKey,
                        latitude = latitude,
                        longitude = longitude,
                        fetchedAtMillis = fetchedAtMillis,
                    )
                }
            dao.replaceForecast(locationKey, entities)
            updateRefreshStatus(locationKey) { status ->
                status.copy(
                    isRefreshing = false,
                    lastNetworkWriteMillis = fetchedAtMillis,
                    errorMessage = null,
                )
            }
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            updateRefreshStatus(locationKey) { status ->
                status.copy(
                    isRefreshing = false,
                    errorMessage = error.message ?: "Unable to load UV forecast",
                )
            }
            Result.failure(error)
        }
    }

    private fun updateRefreshStatus(
        locationKey: String,
        transform: (RefreshStatus) -> RefreshStatus,
    ) {
        refreshStatuses.update { current ->
            current + (locationKey to transform(current[locationKey] ?: RefreshStatus()))
        }
    }

    private fun isFresh(
        fetchedAtMillis: Long,
        currentTimeMillis: Long,
    ): Boolean {
        val ageMillis = currentTimeMillis - fetchedAtMillis
        return ageMillis in 0 until CACHE_MAX_AGE_MILLIS
    }

    private fun locationKey(
        latitude: Double,
        longitude: Double,
    ): String = String.format(Locale.ROOT, "%.5f,%.5f", latitude, longitude)

    private suspend fun resolveLocationKey(
        latitude: Double,
        longitude: Double,
    ): String =
        dao
            .getForecastLocations()
            .map { cached ->
                cached to distanceMeters(latitude, longitude, cached)
            }.minByOrNull { (_, distance) -> distance }
            ?.takeIf { (_, distance) -> distance <= CACHE_REUSE_DISTANCE_METERS }
            ?.first
            ?.locationKey
            ?: locationKey(latitude, longitude)

    private fun distanceMeters(
        latitude: Double,
        longitude: Double,
        cached: CachedForecastLocation,
    ): Double {
        val firstLatitudeRadians = Math.toRadians(latitude)
        val secondLatitudeRadians = Math.toRadians(cached.latitude)
        val latitudeDelta = Math.toRadians(cached.latitude - latitude)
        val longitudeDelta = Math.toRadians(cached.longitude - longitude)
        val haversine = (
            sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
                cos(firstLatitudeRadians) * cos(secondLatitudeRadians) *
                sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
        ).coerceIn(0.0, 1.0)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
    }

    private data class RefreshStatus(
        val isRefreshing: Boolean = false,
        val lastNetworkWriteMillis: Long? = null,
        val errorMessage: String? = null,
    )

    private companion object {
        const val CACHE_MAX_AGE_MILLIS = 60L * 60L * 1000L
        const val CACHE_REUSE_DISTANCE_METERS = 1_000.0
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
