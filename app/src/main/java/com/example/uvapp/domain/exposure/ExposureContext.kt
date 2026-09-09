package com.example.uvapp.domain.exposure

enum class ExposureContext(
    val doseRateFactor: Double,
) {
    DIRECT_SUN(1.0),
    SHADE(0.3),
    INDOOR(0.0),
    UNKNOWN(1.0),
}
