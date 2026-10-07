package com.example.uvapp.domain.environment

import kotlin.math.roundToInt

enum class StepActivity {
    STATIONARY,
    WALKING,
}

data class StepActivityReading(
    val stepsSinceStart: Int,
    val recentSteps: Int,
    val averageStepsPerMinute: Int,
    val lastStepElapsedMillis: Long?,
    val activity: StepActivity,
)

/** Converts the reboot-scoped Android step counter into session-scoped activity values. */
class StepCounterTracker {
    private var baselineSteps: Float? = null
    private var baselineElapsedMillis: Long? = null
    private var previousSteps: Int = 0
    private var lastStepElapsedMillis: Long? = null
    private val recentStepBursts = ArrayDeque<StepBurst>()

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
            previousSteps = 0
            lastStepElapsedMillis = null
            recentStepBursts.clear()
            return reading(elapsedMillis)
        }

        val steps = (cumulativeSteps - baseline).roundToInt().coerceAtLeast(0)
        val newSteps = (steps - previousSteps).coerceAtLeast(0)
        if (newSteps > 0) {
            recentStepBursts.addLast(StepBurst(elapsedMillis, newSteps))
            lastStepElapsedMillis = elapsedMillis
        }
        previousSteps = steps
        return reading(elapsedMillis)
    }

    /** Re-evaluates inactivity even when the hardware emits no new step event. */
    fun snapshot(elapsedMillis: Long): StepActivityReading? {
        val baselineTime = baselineElapsedMillis ?: return null
        if (elapsedMillis < baselineTime) return null
        return reading(elapsedMillis)
    }

    private fun reading(elapsedMillis: Long): StepActivityReading {
        val baselineTime = checkNotNull(baselineElapsedMillis)
        pruneBursts(elapsedMillis)
        val durationMillis = (elapsedMillis - baselineTime).coerceAtLeast(0L)
        val rate =
            if (durationMillis < MIN_RATE_WINDOW_MILLIS) {
                0
            } else {
                (previousSteps * MILLIS_PER_MINUTE.toDouble() / durationMillis)
                    .roundToInt()
                    .coerceIn(0, MAX_STEPS_PER_MINUTE)
            }
        val recentSteps = recentStepBursts.sumOf(StepBurst::count)
        val walkingSteps =
            recentStepBursts
                .filter { it.elapsedMillis >= elapsedMillis - WALKING_WINDOW_MILLIS }
                .sumOf(StepBurst::count)
        val lastStep = lastStepElapsedMillis
        val activity =
            if (walkingSteps >= WALKING_STEP_THRESHOLD) StepActivity.WALKING
            else StepActivity.STATIONARY
        return StepActivityReading(
            stepsSinceStart = previousSteps,
            recentSteps = recentSteps,
            averageStepsPerMinute = rate,
            lastStepElapsedMillis = lastStep,
            activity = activity,
        )
    }

    private fun pruneBursts(elapsedMillis: Long) {
        val cutoff = elapsedMillis - RECENT_STEP_WINDOW_MILLIS
        while (recentStepBursts.firstOrNull()?.elapsedMillis?.let { it < cutoff } == true) {
            recentStepBursts.removeFirst()
        }
    }

    private data class StepBurst(
        val elapsedMillis: Long,
        val count: Int,
    )

    private companion object {
        const val MIN_RATE_WINDOW_MILLIS = 5_000L
        const val MILLIS_PER_MINUTE = 60_000L
        const val MAX_STEPS_PER_MINUTE = 300
        const val RECENT_STEP_WINDOW_MILLIS = 60_000L
        const val WALKING_WINDOW_MILLIS = 15_000L
        const val WALKING_STEP_THRESHOLD = 3
    }
}
