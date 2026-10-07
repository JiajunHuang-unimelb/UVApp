package com.example.uvapp.domain.exposure

import com.example.uvapp.domain.model.SkinType
import kotlin.math.round

object ExposureCalculator {
    /** One UVI sustained for one minute equals 0.015 standard erythemal doses. */
    const val SED_PER_UVI_MINUTE = 0.015

    /**
     * Uses 40% of MED as a protective-action budget, capped at 2.4 SED. Sunscreen SPF does not
     * extend this action estimate. Reference: https://pmc.ncbi.nlm.nih.gov/articles/PMC5749742/
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

    /**
     * Calculates the dose remaining from the personal daily dose limit.
     *
     * The result is allowed to be negative when the accumulated dose exceeds
     * the personal dose limit.
     */
    fun calculateRemainingDose(
        doseLimitSed: Double,
        accumulatedDoseSed: Double,
    ): Double = doseLimitSed - accumulatedDoseSed

    /**
     * Calculates the accumulated dose as a fraction of the personal dose limit.
     *
     * The result is allowed to exceed 1.0 when the accumulated dose exceeds
     * the personal dose limit.
     */
    fun calculateExposureFraction(
        doseLimitSed: Double,
        accumulatedDoseSed: Double,
    ): Double {
        require(doseLimitSed > 0.0) { "Dose limit must be positive" }
        return accumulatedDoseSed / doseLimitSed
    }

    /**
     * Calculates the remaining exposure time at the current UV level.
     *
     * The result is allowed to be negative when the accumulated dose has
     * exceeded the personal dose limit.
     */
    fun calculateRemainingMinutes(
        remainingDoseSed: Double,
        uvIndex: Double,
        contextFactor: Double = 1.0,
    ): Double? =
        when {
            uvIndex <= 0.0 || contextFactor <= 0.0 -> null
            else -> remainingDoseSed / (SED_PER_UVI_MINUTE * uvIndex * contextFactor)
        }

    /**
     * Calculates the remaining exposure time in seconds.
     *
     * Negative values indicate that the personal dose limit has already been exceeded.
     */
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
            round(minutes * SECONDS_PER_MINUTE).toLong()
        }

    private const val SECONDS_PER_MINUTE = 60.0
    private const val PERSONAL_ACTION_MED_FRACTION = 0.4
    private const val MAX_PERSONAL_ACTION_DOSE_SED = 2.4
}
