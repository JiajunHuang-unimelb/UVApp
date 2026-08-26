package com.example.uvapp.domain.repository

import com.example.uvapp.domain.model.UvForecastState
import kotlinx.coroutines.flow.Flow

/** Provides UV forecasts without exposing network or database implementation details. */
interface UvRepository {
    fun observeForecast(
        latitude: Double,
        longitude: Double,
    ): Flow<UvForecastState>

    suspend fun refresh(
        latitude: Double,
        longitude: Double,
        force: Boolean = false,
    ): Result<Unit>
}
