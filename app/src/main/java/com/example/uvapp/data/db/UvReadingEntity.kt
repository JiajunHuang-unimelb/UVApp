package com.example.uvapp.data.db

/** Persisted hourly UV value. Room annotations will be added with the Room dependency. */
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
