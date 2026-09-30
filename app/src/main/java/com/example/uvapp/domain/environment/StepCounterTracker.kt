package com.example.uvapp.domain.environment

import kotlin.math.roundToInt

data class StepActivityReading(
    val stepsSinceStart: Int,
    val averageStepsPerMinute: Int,
)

/** Converts the reboot-scoped Android step counter into session-scoped activity values. */
class StepCounterTracker {
    private var baselineSteps: Float? = null
    private var baselineElapsedMillis: Long? = null

    fun update(
        cumulativeSteps: Float,
        elapsedMillis: Long,
    ): StepActivityReading? {
        if (!cumulativeSteps.isFinite() || cumulativeSteps < 0f || elapsedMillis < 0L) return null

        val baseline = baselineSteps
        val baselineTime = baselineElapsedMillis
        if (baseline == null || baselineTime == null || cumulativeSteps < baseline || elapsedMillis < baselineTime) {
            baselineSteps = cumulativeSteps
            baselineElapsedMillis = elapsedMillis
            return StepActivityReading(stepsSinceStart = 0, averageStepsPerMinute = 0)
        }

        val steps = (cumulativeSteps - baseline).roundToInt().coerceAtLeast(0)
        val durationMillis = (elapsedMillis - baselineTime).coerceAtLeast(0L)
        val rate =
            if (durationMillis < MIN_RATE_WINDOW_MILLIS) {
                0
            } else {
                (steps * MILLIS_PER_MINUTE.toDouble() / durationMillis)
                    .roundToInt()
                    .coerceIn(0, MAX_STEPS_PER_MINUTE)
            }
        return StepActivityReading(stepsSinceStart = steps, averageStepsPerMinute = rate)
    }

    private companion object {
        const val MIN_RATE_WINDOW_MILLIS = 5_000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val MAX_STEPS_PER_MINUTE = 300
    }
}
