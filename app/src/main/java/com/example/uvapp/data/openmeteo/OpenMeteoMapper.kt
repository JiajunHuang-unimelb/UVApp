package com.example.uvapp.data.openmeteo

import com.example.uvapp.domain.model.UvForecastReading
import java.time.LocalDateTime
import java.time.ZoneId

/** Strict mapper that rejects incomplete or misaligned hourly arrays. */
fun OpenMeteoResponseDto.toForecastReadings(): List<UvForecastReading> {
    val hourly = hourly
    val rowCount = hourly.time.size

    require(rowCount > 0) { "Open-Meteo returned no hourly forecast rows" }
    require(hourly.uvIndex.size == rowCount) { "uv_index length does not match time" }
    require(hourly.uvIndexClearSky.size == rowCount) {
        "uv_index_clear_sky length does not match time"
    }
    require(hourly.cloudCover.size == rowCount) { "cloud_cover length does not match time" }

    val zoneId = ZoneId.of(timezone)
    return hourly.time.indices.map { index ->
        val uvIndex = requireNotNull(hourly.uvIndex[index]) {
            "uv_index is missing at ${hourly.time[index]}"
        }
        val forecastTimeMillis =
            LocalDateTime
                .parse(hourly.time[index])
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()

        UvForecastReading(
            forecastTimeMillis = forecastTimeMillis,
            uvIndex = uvIndex,
            clearSkyUvIndex = hourly.uvIndexClearSky[index],
            cloudCoverPercent = hourly.cloudCover[index],
        )
    }
}
