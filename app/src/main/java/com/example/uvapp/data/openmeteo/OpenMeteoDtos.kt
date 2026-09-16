package com.example.uvapp.data.openmeteo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Transport models matching the relevant fields returned by Open-Meteo. */
@Serializable
data class OpenMeteoResponseDto(
    val timezone: String,
    val hourly: OpenMeteoHourlyDto,
)

@Serializable
data class OpenMeteoHourlyDto(
    val time: List<String>,
    @SerialName("uv_index")
    val uvIndex: List<Double?>,
    @SerialName("uv_index_clear_sky")
    val uvIndexClearSky: List<Double?>,
    @SerialName("cloud_cover")
    val cloudCover: List<Int?>,
)
