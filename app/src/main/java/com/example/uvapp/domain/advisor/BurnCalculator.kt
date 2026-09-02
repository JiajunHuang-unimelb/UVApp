package com.example.uvapp.domain.advisor

import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.SkinType

/**
 * Personal burn-time math (front-end mirror of the backend team's formula).
 *
 * burnMinutes = baseMinutesAtUv1(skin) x SPF / (uvIndex x contextFactor)
 * Infinite (Int.MAX_VALUE) when uv or context factor is 0 (no burn risk).
 */
object BurnCalculator {

    fun burnMinutes(skinType: SkinType, spf: Int, uvIndex: Double, context: LightContext): Int {
        val doseRate = uvIndex * context.factor
        if (doseRate <= 0.0) return Int.MAX_VALUE
        val minutes = skinType.baseMinutesAtUv1 * spf / doseRate
        return minutes.toInt()
    }

    /** Formats a minute count as "H:MM" when >= 60 minutes, otherwise "n min". */
    fun formatRemaining(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "$hours:${mins.toString().padStart(2, '0')}" else "${mins} min"
    }
}
