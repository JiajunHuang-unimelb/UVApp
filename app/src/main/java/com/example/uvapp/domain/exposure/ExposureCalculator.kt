package com.example.uvapp.domain.exposure

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round

object ExposureCalculator {
    /** One UVI sustained for one minute equals 0.015 standard erythemal doses. */
    const val SED_PER_UVI_MINUTE = 0.015

    fun calculateDoseIncrement(
        uvIndex: Double,
        elapsedMinutes: Double,
        contextFactor: Double = 1.0,
        sunscreenSpf: Int = 1,
    ): Double =
        // SPF is an idealized protection factor; callers must not treat the result as a safety guarantee.
        SED_PER_UVI_MINUTE *
            uvIndex.coerceAtLeast(0.0) *
            contextFactor.coerceAtLeast(0.0) *
            elapsedMinutes.coerceAtLeast(0.0) /
            sunscreenSpf.coerceAtLeast(1)

    fun calculateRemainingDose(
        doseLimitSed: Double,
        accumulatedDoseSed: Double,
    ): Double = (doseLimitSed - accumulatedDoseSed.coerceAtLeast(0.0)).coerceAtLeast(0.0)

    fun calculateExposureFraction(
        doseLimitSed: Double,
        accumulatedDoseSed: Double,
    ): Double {
        require(doseLimitSed > 0.0) { "Dose limit must be positive" }
        return (accumulatedDoseSed.coerceAtLeast(0.0) / doseLimitSed).coerceAtMost(1.0)
    }

    fun calculateRemainingMinutes(
        remainingDoseSed: Double,
        uvIndex: Double,
        contextFactor: Double = 1.0,
        sunscreenSpf: Int = 1,
    ): Double? =
        when {
            remainingDoseSed <= 0.0 -> 0.0
            uvIndex <= 0.0 || contextFactor <= 0.0 -> null
            else ->
                remainingDoseSed * sunscreenSpf.coerceAtLeast(1) /
                    (SED_PER_UVI_MINUTE * uvIndex * contextFactor)
        }

    fun calculateRemainingSeconds(
        remainingDoseSed: Double,
        uvIndex: Double,
        contextFactor: Double = 1.0,
        sunscreenSpf: Int = 1,
    ): Long? =
        calculateRemainingMinutes(
            remainingDoseSed = remainingDoseSed,
            uvIndex = uvIndex,
            contextFactor = contextFactor,
            sunscreenSpf = sunscreenSpf,
        )?.let { minutes ->
            val seconds = minutes * SECONDS_PER_MINUTE
            val nearestWholeSecond = round(seconds)
            if (abs(seconds - nearestWholeSecond) < ROUNDING_EPSILON) {
                nearestWholeSecond.toLong()
            } else {
                ceil(seconds).toLong()
            }
        }

    private const val SECONDS_PER_MINUTE = 60.0
    private const val ROUNDING_EPSILON = 1e-9
}
