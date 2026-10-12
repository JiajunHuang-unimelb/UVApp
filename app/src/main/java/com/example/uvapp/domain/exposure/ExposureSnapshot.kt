package com.example.uvapp.domain.exposure

import com.example.uvapp.domain.model.SkinType

data class ExposureSnapshot(
    val status: ExposureStatus,
    val skinType: SkinType,
    val uvIndex: Double,
    val context: ExposureContext,
    val accumulatedDoseSed: Double,
    val doseLimitSed: Double,
    val remainingDoseSed: Double,
    val exposureFraction: Double,
    val estimatedRemainingMinutes: Double?,
    val estimatedRemainingSeconds: Long?,
    val estimatedTotalSeconds: Long?,

    // Exposure duration breakdown.
    // activeDurationMillis is the sum of the three context durations.
    val activeDurationMillis: Long,
    val directSunDurationMillis: Long,
    val shadeDurationMillis: Long,
    val unknownDurationMillis: Long,
) {
    val isStarted: Boolean get() = status != ExposureStatus.NOT_STARTED
    val isRunning: Boolean get() = status == ExposureStatus.RUNNING
}
