package com.example.uvapp.domain.environment

import kotlin.math.log10
import kotlin.math.sqrt

enum class AcousticContext {
    QUIET_INDOOR_LIKELY,
    UNCERTAIN,
    ACTIVE_OUTDOOR_LIKELY,
}

data class AcousticReading(
    val decibelsFullScale: Double,
    val context: AcousticContext,
)

/** Calculates a smoothed relative sound level. Raw microphone samples are never retained. */
class SoundLevelClassifier(
    private val smoothingFactor: Double = DEFAULT_SMOOTHING_FACTOR,
) {
    private var smoothedDb: Double? = null

    init {
        require(smoothingFactor in 0.0..1.0)
    }

    fun classify(
        samples: ShortArray,
        sampleCount: Int = samples.size,
    ): AcousticReading? {
        if (sampleCount <= 0 || sampleCount > samples.size) return null

        var sumSquares = 0.0
        repeat(sampleCount) { index ->
            val normalized = samples[index].toDouble() / Short.MAX_VALUE
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / sampleCount).coerceAtLeast(MINIMUM_RMS)
        val measuredDb = (20.0 * log10(rms)).coerceIn(MINIMUM_DB, 0.0)
        val previous = smoothedDb
        val resultDb =
            if (previous == null) {
                measuredDb
            } else {
                previous + smoothingFactor * (measuredDb - previous)
            }
        smoothedDb = resultDb

        val context =
            when {
                resultDb <= QUIET_THRESHOLD_DB -> AcousticContext.QUIET_INDOOR_LIKELY
                resultDb >= ACTIVE_THRESHOLD_DB -> AcousticContext.ACTIVE_OUTDOOR_LIKELY
                else -> AcousticContext.UNCERTAIN
            }
        return AcousticReading(decibelsFullScale = resultDb, context = context)
    }

    private companion object {
        const val DEFAULT_SMOOTHING_FACTOR = 0.25
        const val MINIMUM_RMS = 0.000_001
        const val MINIMUM_DB = -120.0
        const val QUIET_THRESHOLD_DB = -45.0
        const val ACTIVE_THRESHOLD_DB = -25.0
    }
}
