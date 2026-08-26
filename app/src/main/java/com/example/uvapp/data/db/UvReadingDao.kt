package com.example.uvapp.data.db

import kotlinx.coroutines.flow.Flow

/** Database boundary for cached hourly UV forecasts. */
interface UvReadingDao {
    fun observeForecast(locationKey: String): Flow<List<UvReadingEntity>>

    suspend fun replaceForecast(
        locationKey: String,
        readings: List<UvReadingEntity>,
    )

    suspend fun latestFetchTime(locationKey: String): Long?
}
