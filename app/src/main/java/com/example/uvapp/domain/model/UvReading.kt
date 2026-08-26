package com.example.uvapp.domain.model

/** One hourly UV forecast value used by the rest of the application. */
data class UvReading(
    val forecastTimeMillis: Long,
    val uvIndex: Double,
    val clearSkyUvIndex: Double?,
    val cloudCoverPercent: Int?,
)
