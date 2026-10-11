package com.example.uvapp.domain.exposure

/**
 * Stops a noisy per-sample [ExposureContext] from flipping the countdown back and forth.
 *
 * Tilting or shaking the phone changes the light reading, the posture and the movement flag from
 * one sample to the next, so the raw context can alternate between shade and unknown every few
 * seconds. Because the two use different dose rates, the remaining time jumps each time. A raw
 * change is therefore only applied once it has held for a while.
 *
 * Moving to a context with a higher dose rate is confirmed quickly, since under-counting UV is the
 * riskier mistake. Moving to a lower rate needs longer, steady evidence. INDOOR is already
 * debounced by the ViewModel's indoor transition, so entering or leaving it is applied at once.
 */
class ExposureContextStabilizer(
    private val riseDelayMillis: Long = RISE_DELAY_MILLIS,
    private val fallDelayMillis: Long = FALL_DELAY_MILLIS,
) {
    private var stable: ExposureContext? = null
    private var candidate: ExposureContext? = null
    private var candidateSinceMillis = 0L

    fun update(
        raw: ExposureContext,
        nowMillis: Long,
    ): ExposureContext {
        val current = stable
        if (current == null ||
            raw == current ||
            raw == ExposureContext.INDOOR ||
            current == ExposureContext.INDOOR
        ) {
            return accept(raw)
        }

        if (raw != candidate) {
            candidate = raw
            candidateSinceMillis = nowMillis
        }
        val delayMillis = if (raw.doseRateFactor > current.doseRateFactor) riseDelayMillis else fallDelayMillis
        return if (nowMillis - candidateSinceMillis >= delayMillis) accept(raw) else current
    }

    /** Forgets the current context so the next raw value is applied immediately. */
    fun reset() {
        stable = null
        candidate = null
    }

    private fun accept(context: ExposureContext): ExposureContext {
        stable = context
        candidate = null
        return context
    }

    companion object {
        const val RISE_DELAY_MILLIS = 3_000L
        const val FALL_DELAY_MILLIS = 10_000L
    }
}
