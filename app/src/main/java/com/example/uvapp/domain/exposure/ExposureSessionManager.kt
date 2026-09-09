package com.example.uvapp.domain.exposure

class ExposureSessionManager {
    private var skinType = SkinType.TYPE_II
    private var sunscreenSpf = 1
    private var currentUvIndex = 0.0
    private var currentContext = ExposureContext.UNKNOWN
    private var accumulatedDoseSed = 0.0
    private var status = ExposureStatus.NOT_STARTED
    private var lastElapsedMs: Long? = null

    fun start(
        skinType: SkinType,
        uvIndex: Double,
        nowElapsedMs: Long,
        context: ExposureContext = ExposureContext.UNKNOWN,
        sunscreenSpf: Int = 1,
    ): ExposureSnapshot {
        this.skinType = skinType
        this.sunscreenSpf = sunscreenSpf.coerceAtLeast(1)
        currentUvIndex = uvIndex.coerceAtLeast(0.0)
        currentContext = context
        accumulatedDoseSed = 0.0
        status = ExposureStatus.RUNNING
        lastElapsedMs = nowElapsedMs
        return snapshot()
    }

    fun refresh(nowElapsedMs: Long): ExposureSnapshot {
        settleExposure(nowElapsedMs)
        return snapshot()
    }

    fun pause(nowElapsedMs: Long): ExposureSnapshot {
        if (status == ExposureStatus.RUNNING) {
            settleExposure(nowElapsedMs)
            if (status != ExposureStatus.COMPLETE) status = ExposureStatus.PAUSED
        }
        return snapshot()
    }

    fun resume(nowElapsedMs: Long): ExposureSnapshot {
        if (status == ExposureStatus.RUNNING) {
            settleExposure(nowElapsedMs)
        } else if (status == ExposureStatus.PAUSED) {
            status = ExposureStatus.RUNNING
            lastElapsedMs = nowElapsedMs
        }
        return snapshot()
    }

    fun clear(nowElapsedMs: Long): ExposureSnapshot {
        settleExposure(nowElapsedMs)
        accumulatedDoseSed = 0.0
        status = ExposureStatus.NOT_STARTED
        lastElapsedMs = null
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

    fun updateSunscreenSpf(
        sunscreenSpf: Int,
        nowElapsedMs: Long,
    ): ExposureSnapshot {
        settleExposure(nowElapsedMs)
        this.sunscreenSpf = sunscreenSpf.coerceAtLeast(1)
        return snapshot()
    }

    fun updateSkinType(
        skinType: SkinType,
        nowElapsedMs: Long,
    ): ExposureSnapshot {
        settleExposure(nowElapsedMs)
        this.skinType = skinType
        if (status == ExposureStatus.RUNNING && remainingDoseSed() == 0.0) {
            status = ExposureStatus.COMPLETE
        }
        return snapshot()
    }

    fun updateContext(
        context: ExposureContext,
        nowElapsedMs: Long,
    ): ExposureSnapshot {
        settleExposure(nowElapsedMs)
        currentContext = context
        return snapshot()
    }

    fun snapshot(): ExposureSnapshot {
        val doseLimitSed = skinType.exposureLimitSed
        val remainingDoseSed = ExposureCalculator.calculateRemainingDose(doseLimitSed, accumulatedDoseSed)
        val estimatedRemainingMinutes =
            ExposureCalculator.calculateRemainingMinutes(
                remainingDoseSed = remainingDoseSed,
                uvIndex = currentUvIndex,
                contextFactor = currentContext.doseRateFactor,
                sunscreenSpf = sunscreenSpf,
            )
        return ExposureSnapshot(
            status = status,
            skinType = skinType,
            sunscreenSpf = sunscreenSpf,
            uvIndex = currentUvIndex,
            context = currentContext,
            accumulatedDoseSed = accumulatedDoseSed,
            doseLimitSed = doseLimitSed,
            remainingDoseSed = remainingDoseSed,
            exposureFraction = ExposureCalculator.calculateExposureFraction(doseLimitSed, accumulatedDoseSed),
            estimatedRemainingMinutes = estimatedRemainingMinutes,
            estimatedRemainingSeconds =
                ExposureCalculator.calculateRemainingSeconds(
                    remainingDoseSed = remainingDoseSed,
                    uvIndex = currentUvIndex,
                    contextFactor = currentContext.doseRateFactor,
                    sunscreenSpf = sunscreenSpf,
                ),
            estimatedTotalSeconds =
                ExposureCalculator.calculateRemainingSeconds(
                    remainingDoseSed = doseLimitSed,
                    uvIndex = currentUvIndex,
                    contextFactor = currentContext.doseRateFactor,
                    sunscreenSpf = sunscreenSpf,
                ),
        )
    }

    private fun settleExposure(nowElapsedMs: Long) {
        val previousElapsedMs = lastElapsedMs ?: return
        if (status != ExposureStatus.RUNNING || nowElapsedMs <= previousElapsedMs) return

        val elapsedMinutes = (nowElapsedMs - previousElapsedMs) / MILLIS_PER_MINUTE
        accumulatedDoseSed +=
            ExposureCalculator.calculateDoseIncrement(
                currentUvIndex,
                elapsedMinutes,
                currentContext.doseRateFactor,
                sunscreenSpf,
            )
        lastElapsedMs = nowElapsedMs
        if (remainingDoseSed() == 0.0) {
            status = ExposureStatus.COMPLETE
        }
    }

    private fun remainingDoseSed(): Double =
        ExposureCalculator.calculateRemainingDose(skinType.exposureLimitSed, accumulatedDoseSed)

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000.0
    }
}
