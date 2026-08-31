package com.example.uvapp.domain.exposure

class ExposureSessionManager {
    private lateinit var skinType: SkinType
    private var currentUvIndex = 0.0
    private var accumulatedDoseSed = 0.0
    private var isRunning = false
    private var lastElapsedMs: Long? = null

    fun start(
        skinType: SkinType,
        uvIndex: Double,
        nowElapsedMs: Long,
    ): ExposureSnapshot {
        this.skinType = skinType
        currentUvIndex = uvIndex.coerceAtLeast(0.0)
        accumulatedDoseSed = 0.0
        isRunning = true
        lastElapsedMs = nowElapsedMs
        return snapshot()
    }

    fun refresh(nowElapsedMs: Long): ExposureSnapshot {
        settleExposure(nowElapsedMs)
        return snapshot()
    }

    fun pause(nowElapsedMs: Long): ExposureSnapshot {
        if (isRunning) {
            settleExposure(nowElapsedMs)
            isRunning = false
        } else {
            ensureStarted()
        }
        return snapshot()
    }

    fun resume(nowElapsedMs: Long): ExposureSnapshot {
        ensureStarted()
        if (isRunning) {
            settleExposure(nowElapsedMs)
        } else {
            isRunning = true
            lastElapsedMs = nowElapsedMs
        }
        return snapshot()
    }

    fun updateUvIndex(
        uvIndex: Double,
        nowElapsedMs: Long,
    ): ExposureSnapshot {
        settleExposure(nowElapsedMs)
        currentUvIndex = uvIndex.coerceAtLeast(0.0)
        return snapshot()
    }

    fun updateSkinType(
        skinType: SkinType,
        nowElapsedMs: Long,
    ): ExposureSnapshot {
        settleExposure(nowElapsedMs)
        this.skinType = skinType
        return snapshot()
    }

    fun snapshot(): ExposureSnapshot {
        ensureStarted()
        val doseLimitSed = skinType.exposureLimitSed
        val remainingDoseSed = ExposureCalculator.calculateRemainingDose(doseLimitSed, accumulatedDoseSed)
        return ExposureSnapshot(
            isRunning = isRunning,
            skinType = skinType,
            uvIndex = currentUvIndex,
            accumulatedDoseSed = accumulatedDoseSed,
            doseLimitSed = doseLimitSed,
            remainingDoseSed = remainingDoseSed,
            exposureFraction = ExposureCalculator.calculateExposureFraction(doseLimitSed, accumulatedDoseSed),
            estimatedRemainingMinutes =
                ExposureCalculator.calculateRemainingMinutes(remainingDoseSed, currentUvIndex),
        )
    }

    private fun settleExposure(nowElapsedMs: Long) {
        val previousElapsedMs = ensureStarted()
        if (!isRunning || nowElapsedMs <= previousElapsedMs) return

        val elapsedMinutes = (nowElapsedMs - previousElapsedMs) / MILLIS_PER_MINUTE
        accumulatedDoseSed += ExposureCalculator.calculateDoseIncrement(currentUvIndex, elapsedMinutes)
        lastElapsedMs = nowElapsedMs
    }

    private fun ensureStarted(): Long = checkNotNull(lastElapsedMs) { "Exposure session has not been started" }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000.0
    }
}
