package com.example.uvapp.domain.advisor

import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.exposure.ExposureCalculator
import com.example.uvapp.domain.exposure.SkinType as ExposureSkinType

/**
 * Compatibility adapter for callers that still consume a whole-minute estimate.
 * The exposure package owns the dose calculation used by the countdown.
 */
object BurnCalculator {

    fun burnMinutes(skinType: SkinType, spf: Int, uvIndex: Double, context: LightContext): Int {
        val exposureLimitSed = skinType.toExposureSkinType().exposureLimitSed
        return ExposureCalculator.calculateRemainingMinutes(
            remainingDoseSed = exposureLimitSed,
            uvIndex = uvIndex,
            contextFactor = context.factor,
            sunscreenSpf = spf,
        )?.toInt() ?: Int.MAX_VALUE
    }

    /** Shows seconds on every countdown tick. */
    fun formatRemaining(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val hours = minutes / 60
        val mins = minutes % 60
        val seconds = (totalSeconds % 60).toString().padStart(2, '0')
        val mm = mins.toString().padStart(2, '0')
        return if (hours > 0) "$hours:$mm:$seconds" else "$mm:$seconds"
    }

    private fun SkinType.toExposureSkinType(): ExposureSkinType =
        when (this) {
            SkinType.I -> ExposureSkinType.TYPE_I
            SkinType.II -> ExposureSkinType.TYPE_II
            SkinType.III -> ExposureSkinType.TYPE_III
            SkinType.IV -> ExposureSkinType.TYPE_IV
            SkinType.V -> ExposureSkinType.TYPE_V
            SkinType.VI -> ExposureSkinType.TYPE_VI
        }
}
