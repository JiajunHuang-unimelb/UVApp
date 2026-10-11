package com.example.uvapp.domain.exposure

import com.example.uvapp.domain.model.SkinType

class ExposureSessionManager(private val useDailyBudget: Boolean = false) {
    private var skinType = SkinType.II
    private var currentUvIndex = 0.0
    private var currentContext = ExposureContext.UNKNOWN
    private var accumulatedDoseSed = 0.0
    private var dailyDoseSed: Double? = null

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
        initialDailyDoseSed: Double? = null,
    ): ExposureSnapshot {
        this.skinType = skinType
        currentUvIndex = uvIndex.coerceAtLeast(0.0)
        currentContext = context
        accumulatedDoseSed = 0.0
        dailyDoseSed = initialDailyDoseSed
        directSunDurationMillis = 0L
        shadeDurationMillis = 0L
        unknownDurationMillis = 0L
        status = ExposureStatus.RUNNING
        completeIfDailyLimitReached()
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
        dailyDoseSed = null
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
        if (!useDailyBudget && status == ExposureStatus.RUNNING && remainingDoseSed() <= 0.0) {
            status = ExposureStatus.COMPLETE
        }
        return snapshot()
    }

    /** Daily budget includes other records plus only today's part of this session. */
    fun updateDailyDose(doseSed: Double?): ExposureSnapshot {
        require(doseSed == null || (doseSed.isFinite() && doseSed >= 0.0))
        dailyDoseSed = doseSed
        completeIfDailyLimitReached()
        return snapshot()
    }

    private fun completeIfDailyLimitReached() {
        if (useDailyBudget && dailyDoseSed != null &&
            (status == ExposureStatus.RUNNING || status == ExposureStatus.PAUSED) &&
            remainingDoseSed() <= 0.0
        ) status = ExposureStatus.COMPLETE
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
        val doseForLimit = if (useDailyBudget) dailyDoseSed else accumulatedDoseSed
        val remainingDoseSed =
            ExposureCalculator.calculateRemainingDose(
                doseLimitSed,
                doseForLimit ?: doseLimitSed,
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
                    doseForLimit ?: 0.0,
                ),
            estimatedRemainingMinutes =
                if (doseForLimit == null) null else ExposureCalculator.calculateRemainingMinutes(
                    remainingDoseSed,
                    currentUvIndex,
                    currentContext.doseRateFactor,
                ),
            estimatedRemainingSeconds =
                if (doseForLimit == null) null else ExposureCalculator.calculateRemainingSeconds(
                    remainingDoseSed,
                    currentUvIndex,
                    currentContext.doseRateFactor,
                ),
            estimatedTotalSeconds =
                if (doseForLimit == null) null else ExposureCalculator.calculateRemainingSeconds(
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
            dailyAccumulatedDoseSed = doseForLimit,
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

        // Daily mode is finalized after the caller allocates this interval to calendar days.
        if (!useDailyBudget && remainingDoseSed() <= 0.0) {
            status = ExposureStatus.COMPLETE
        }
    }

    private fun remainingDoseSed(): Double =
        ExposureCalculator.calculateRemainingDose(
            ExposureCalculator.calculatePersonalDoseLimit(skinType),
            if (useDailyBudget) dailyDoseSed ?: 0.0 else accumulatedDoseSed,
        )

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000.0
    }
}
