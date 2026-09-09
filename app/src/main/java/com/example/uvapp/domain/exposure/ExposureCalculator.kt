package com.example.uvapp.domain.exposure

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round

object ExposureCalculator {
    /** One UVI sustained for one minute equals 0.015 standard erythemal doses. */
    const val SED_PER_UVI_MINUTE = 0.015

    /**
     * Uses 40% of MED as a personalized protective-action budget. Types V and VI share the
     * Type V ceiling, matching the study's equal daily "sunstock" values for those phototypes.
     * This is an action estimate, not a guarantee that exposure below it is free from harm.
     * Reference: https://pmc.ncbi.nlm.nih.gov/articles/PMC5749742/
     */
    fun calculatePersonalDoseLimit(skinType: SkinType): Double =
        (skinType.minimumErythemaDoseSed * PERSONAL_ACTION_MED_FRACTION)
            .coerceAtMost(MAX_PERSONAL_ACTION_DOSE_SED)

    fun calculateDoseIncrement(
        uvIndex: Double,
        elapsedMinutes: Double,
        contextFactor: Double = 1.0,
    ): Double =
        SED_PER_UVI_MINUTE *
            uvIndex.coerceAtLeast(0.0) *
            contextFactor.coerceAtLeast(0.0) *
            elapsedMinutes.coerceAtLeast(0.0)

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
    ): Double? =
        when {
            remainingDoseSed <= 0.0 -> 0.0
            uvIndex <= 0.0 || contextFactor <= 0.0 -> null
            else ->
                remainingDoseSed / (SED_PER_UVI_MINUTE * uvIndex * contextFactor)
        }

    fun calculateRemainingSeconds(
        remainingDoseSed: Double,
        uvIndex: Double,
        contextFactor: Double = 1.0,
    ): Long? =
        calculateRemainingMinutes(
            remainingDoseSed = remainingDoseSed,
            uvIndex = uvIndex,
            contextFactor = contextFactor,
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
    private const val PERSONAL_ACTION_MED_FRACTION = 0.4
    private const val MAX_PERSONAL_ACTION_DOSE_SED = 2.4
}
