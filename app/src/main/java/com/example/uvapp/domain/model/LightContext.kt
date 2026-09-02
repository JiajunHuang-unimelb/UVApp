package com.example.uvapp.domain.model

/**
 * Sensor-fused exposure context. factor scales the UV dose rate:
 * 1.0 direct sun, 0.3 shade, 0.0 indoor (timer paused).
 */
enum class LightContext(val label: String, val factor: Double) {
    INDOOR("Indoors", 0.0),
    SHADE("In shade", 0.3),
    DIRECT_SUN("Direct sun", 1.0);

    companion object {
        /** Provisional lux thresholds for the mock (the backend calibrates these). */
        const val INDOOR_MAX_LUX = 1_000
        const val SHADE_MAX_LUX = 20_000

        /** Maps a lux reading to the context it would be recognised as. */
        fun fromLux(lux: Int): LightContext = when {
            lux < INDOOR_MAX_LUX -> INDOOR
            lux < SHADE_MAX_LUX -> SHADE
            else -> DIRECT_SUN
        }
    }
}
