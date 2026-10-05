package com.example.uvapp.data.db

import androidx.room.Entity

/** Persisted hourly UV value for one approximate location. */
@Entity(
    tableName = "uv_readings",
    primaryKeys = ["locationKey", "forecastTimeMillis"],
)
data class UvReadingEntity(
    val locationKey: String,
    val forecastTimeMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val uvIndex: Double,
    val clearSkyUvIndex: Double?,
    val cloudCoverPercent: Int?,
    val fetchedAtMillis: Long,
)
