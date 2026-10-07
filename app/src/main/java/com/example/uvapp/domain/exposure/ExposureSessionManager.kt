package com.example.uvapp.domain.exposure

import com.example.uvapp.domain.model.SkinType

class ExposureSessionManager {
    private var skinType = SkinType.II
    private var currentUvIndex = 0.0
    private var currentContext = ExposureContext.UNKNOWN
    private var accumulatedDoseSed = 0.0

    private var directSunDurationMillis = 0L
    private var shadeDurationMillis = 0L
    private var unknownDurationMillis = 0L

    private var status = ExposureStatus.NOT_STARTED
    private var lastElapsedMs: Long? = null

    fun start(
        skinType: SkinType,
        uvIndex: Double,
        nowElapsedMs: Long,
        context: ExposureContext = ExposureContext.UNKNOWN,
    ): ExposureSnapshot {
        this.skinType = skinType
        currentUvIndex = uvIndex.coerceAtLeast(0.0)
        currentContext = context
        accumulatedDoseSed = 0.0
        directSunDurationMillis = 0L
        shadeDurationMillis = 0L
        unknownDurationMillis = 0L
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
        directSunDurationMillis = 0L
        shadeDurationMillis = 0L
        unknownDurationMillis = 0L
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

    fun updateSkinType(
        skinType: SkinType,
        nowElapsedMs: Long,
    ): ExposureSnapshot {
        settleExposure(nowElapsedMs)
        this.skinType = skinType
        if (status == ExposureStatus.RUNNING && remainingDoseSed() <= 0.0) {
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
        val doseLimitSed = ExposureCalculator.calculatePersonalDoseLimit(skinType)
        val remainingDoseSed =
            ExposureCalculator.calculateRemainingDose(
                doseLimitSed,
                accumulatedDoseSed,
            )

        return ExposureSnapshot(
            status = status,
            skinType = skinType,
            uvIndex = currentUvIndex,
            context = currentContext,
            accumulatedDoseSed = accumulatedDoseSed,
            doseLimitSed = doseLimitSed,
            remainingDoseSed = remainingDoseSed,
            exposureFraction =
                ExposureCalculator.calculateExposureFraction(
                    doseLimitSed,
                    accumulatedDoseSed,
                ),
            estimatedRemainingMinutes =
                ExposureCalculator.calculateRemainingMinutes(
                    remainingDoseSed,
                    currentUvIndex,
                    currentContext.doseRateFactor,
                ),
            estimatedRemainingSeconds =
                ExposureCalculator.calculateRemainingSeconds(
                    remainingDoseSed,
                    currentUvIndex,
                    currentContext.doseRateFactor,
                ),
            estimatedTotalSeconds =
                ExposureCalculator.calculateRemainingSeconds(
                    doseLimitSed,
                    currentUvIndex,
                    currentContext.doseRateFactor,
                ),
            activeDurationMillis =
                directSunDurationMillis +
                    shadeDurationMillis +
                    unknownDurationMillis,
            directSunDurationMillis = directSunDurationMillis,
            shadeDurationMillis = shadeDurationMillis,
            unknownDurationMillis = unknownDurationMillis,
        )
    }

    private fun settleExposure(nowElapsedMs: Long) {
        val previousElapsedMs = lastElapsedMs ?: return
        if (status != ExposureStatus.RUNNING || nowElapsedMs <= previousElapsedMs) {
            return
        }
        val elapsedMillis = nowElapsedMs - previousElapsedMs
        val elapsedMinutes = elapsedMillis / MILLIS_PER_MINUTE

        accumulatedDoseSed +=
            ExposureCalculator.calculateDoseIncrement(
                currentUvIndex,
                elapsedMinutes,
                currentContext.doseRateFactor,
            )

        when (currentContext) {
            ExposureContext.DIRECT_SUN -> {
                directSunDurationMillis += elapsedMillis
            }

            ExposureContext.SHADE -> {
                shadeDurationMillis += elapsedMillis
            }

            ExposureContext.UNKNOWN -> {
                unknownDurationMillis += elapsedMillis
            }

            ExposureContext.INDOOR -> {
                // Indoor exposure is paused and does not count as active exposure time.
            }
        }

        lastElapsedMs = nowElapsedMs

        if (remainingDoseSed() <= 0.0) {
            status = ExposureStatus.COMPLETE
        }
    }

    private fun remainingDoseSed(): Double =
        ExposureCalculator.calculateRemainingDose(
            ExposureCalculator.calculatePersonalDoseLimit(skinType),
            accumulatedDoseSed,
        )

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000.0
    }
}
