package com.example.uvapp.domain.exposure

data class ExposureSnapshot(
    val isRunning: Boolean,
    val skinType: SkinType,
    val uvIndex: Double,
    val context: ExposureContext,
    val accumulatedDoseSed: Double,
    val doseLimitSed: Double,
    val remainingDoseSed: Double,
    val exposureFraction: Double,
    val estimatedRemainingMinutes: Double?,
)
