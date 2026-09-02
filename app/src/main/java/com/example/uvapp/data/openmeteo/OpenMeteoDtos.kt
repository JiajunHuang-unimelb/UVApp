package com.example.uvapp.data.openmeteo

import com.example.uvapp.domain.model.UvReading
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Transport models matching the relevant fields returned by Open-Meteo. */
@Serializable
data class OpenMeteoResponseDto(
    val latitude: Double,
    val longitude: Double,
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

@Serializable
data class OpenMeteoUvResponse(
    val current: CurrentUvDto,
)

@Serializable
data class CurrentUvDto(
    val time: String,
    @SerialName("uv_index") val uvIndex: Double,
)

fun OpenMeteoUvResponse.toDomain(): UvReading =
    UvReading(
        index = current.uvIndex,
        observedAt = current.time,
    )
