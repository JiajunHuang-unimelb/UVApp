package com.example.uvapp.domain.exposure

import com.example.uvapp.domain.model.SkinType

data class ExposureSnapshot(
    val isRunning: Boolean,
    val skinType: SkinType,
    val sunscreenSpf: Int,
    val uvIndex: Double,
    val context: ExposureContext,
    val accumulatedDoseSed: Double,
    val doseLimitSed: Double,
    val remainingDoseSed: Double,
    val exposureFraction: Double,
    val estimatedRemainingMinutes: Double?,
    val estimatedRemainingSeconds: Long?,
    val estimatedTotalSeconds: Long?,
)
