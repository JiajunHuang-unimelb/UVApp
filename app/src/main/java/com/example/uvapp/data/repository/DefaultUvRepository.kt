package com.example.uvapp.data.repository

import com.example.uvapp.data.openmeteo.OpenMeteoApi
import com.example.uvapp.data.openmeteo.OpenMeteoMapper
import com.example.uvapp.domain.model.UvDataSource
import com.example.uvapp.domain.model.UvForecastState
import com.example.uvapp.domain.repository.UvRepository
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Network-backed repository. Room caching is added in the database stage. */
class DefaultUvRepository(
    private val api: OpenMeteoApi,
    private val mapper: OpenMeteoMapper,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : UvRepository {
    private val states = MutableStateFlow<Map<String, UvForecastState>>(emptyMap())

    override fun observeForecast(
        latitude: Double,
        longitude: Double,
    ): Flow<UvForecastState> {
        val locationKey = locationKey(latitude, longitude)
        return states
            .map { statesByLocation -> statesByLocation[locationKey] ?: UvForecastState() }
            .distinctUntilChanged()
    }

    override suspend fun refresh(
        latitude: Double,
        longitude: Double,
        force: Boolean,
    ): Result<Unit> {
        val locationKey = locationKey(latitude, longitude)
        updateState(locationKey) { current ->
            current.copy(
                isRefreshing = true,
                errorMessage = null,
            )
        }

        return try {
            val response = api.getUvForecast(latitude, longitude)
            val readings = mapper.toDomain(response)
            updateState(locationKey) {
                UvForecastState(
                    readings = readings,
                    source = UvDataSource.NETWORK,
                    lastUpdatedMillis = nowMillis(),
                    isRefreshing = false,
                )
            }
            Result.success(Unit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            updateState(locationKey) { current ->
                current.copy(
                    isRefreshing = false,
                    errorMessage = error.message ?: "Unable to load UV forecast",
                )
            }
            Result.failure(error)
        }
    }

    private fun updateState(
        locationKey: String,
        transform: (UvForecastState) -> UvForecastState,
    ) {
        states.update { current ->
            current + (locationKey to transform(current[locationKey] ?: UvForecastState()))
        }
    }

    private fun locationKey(
        latitude: Double,
        longitude: Double,
    ): String = String.format(Locale.ROOT, "%.3f,%.3f", latitude, longitude)
}
