package com.example.uvapp.domain.exposure

data class ExposureSnapshot(
    val status: ExposureStatus,
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
) {
    val isStarted: Boolean get() = status != ExposureStatus.NOT_STARTED
    val isRunning: Boolean get() = status == ExposureStatus.RUNNING
}
