package com.example.uvapp.domain.model

/** One hourly UV forecast value used by the offline forecast data layer. */
data class UvForecastReading(
    val forecastTimeMillis: Long,
    val uvIndex: Double,
    val clearSkyUvIndex: Double?,
    val cloudCoverPercent: Int?,
)
