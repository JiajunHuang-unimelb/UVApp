package com.example.uvapp.domain.exposure

object ExposureCalculator {
    const val SED_PER_UVI_MINUTE = 0.015

    fun calculateDoseIncrement(
        uvIndex: Double,
        elapsedMinutes: Double,
    ): Double = SED_PER_UVI_MINUTE * uvIndex.coerceAtLeast(0.0) * elapsedMinutes.coerceAtLeast(0.0)

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
    ): Double? =
        when {
            remainingDoseSed <= 0.0 -> 0.0
            uvIndex <= 0.0 -> null
            else -> remainingDoseSed / (SED_PER_UVI_MINUTE * uvIndex)
        }
}
