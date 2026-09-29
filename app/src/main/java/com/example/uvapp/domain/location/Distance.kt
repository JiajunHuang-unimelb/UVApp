package com.example.uvapp.domain.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle distance used by the UV and place-name caches. */
internal fun distanceMeters(
    firstLatitude: Double,
    firstLongitude: Double,
    secondLatitude: Double,
    secondLongitude: Double,
): Double {
    val firstLatitudeRadians = Math.toRadians(firstLatitude)
    val secondLatitudeRadians = Math.toRadians(secondLatitude)
    val latitudeDelta = Math.toRadians(secondLatitude - firstLatitude)
    val longitudeDelta = Math.toRadians(secondLongitude - firstLongitude)
    val haversine = (
        sin(latitudeDelta / 2) * sin(latitudeDelta / 2) +
            cos(firstLatitudeRadians) * cos(secondLatitudeRadians) *
            sin(longitudeDelta / 2) * sin(longitudeDelta / 2)
    ).coerceIn(0.0, 1.0)
    return 6_371_000.0 * 2 * atan2(sqrt(haversine), sqrt(1 - haversine))
}
