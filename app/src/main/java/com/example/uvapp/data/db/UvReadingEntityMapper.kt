package com.example.uvapp.data.db

import com.example.uvapp.domain.model.UvReading

fun UvReading.toEntity(
    locationKey: String,
    latitude: Double,
    longitude: Double,
    fetchedAtMillis: Long,
): UvReadingEntity =
    UvReadingEntity(
        locationKey = locationKey,
        forecastTimeMillis = forecastTimeMillis,
        latitude = latitude,
        longitude = longitude,
        uvIndex = uvIndex,
        clearSkyUvIndex = clearSkyUvIndex,
        cloudCoverPercent = cloudCoverPercent,
        fetchedAtMillis = fetchedAtMillis,
    )

fun UvReadingEntity.toDomain(): UvReading =
    UvReading(
        forecastTimeMillis = forecastTimeMillis,
        uvIndex = uvIndex,
        clearSkyUvIndex = clearSkyUvIndex,
        cloudCoverPercent = cloudCoverPercent,
    )
