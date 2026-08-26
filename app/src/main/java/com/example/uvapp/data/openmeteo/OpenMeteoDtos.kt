package com.example.uvapp.data.openmeteo

import com.example.uvapp.domain.model.UvReading
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class OpenMeteoUvResponse(
    val current: CurrentUvDto,
)

@Serializable
internal data class CurrentUvDto(
    val time: String,
    @SerialName("uv_index") val uvIndex: Double,
)

internal fun OpenMeteoUvResponse.toDomain(): UvReading =
    UvReading(
        index = current.uvIndex,
        observedAt = current.time,
    )
