package com.example.uvapp.domain.environment

data class LocationMonitoringDecision(
    val intervalMillis: Long,
    val requestFreshFix: Boolean,
)

/** Chooses the location cadence and one-shot refreshes from saved-place and step activity. */
class LocationMonitoringPolicy {
    private var significantWalking = false
    private var burstUntilElapsedMillis = Long.MIN_VALUE
    private var lastTriggeredRefreshElapsedMillis: Long? = null

    fun evaluate(
        nowElapsedMillis: Long,
        nearIndoorLocation: Boolean,
        stepReading: StepActivityReading?,
        latestFixElapsedMillis: Long?,
    ): LocationMonitoringDecision {
        require(nowElapsedMillis >= 0L)
        val isSignificantWalking =
            stepReading?.activity == StepActivity.WALKING &&
                stepReading.stepsSinceStart > SIGNIFICANT_WALKING_STEPS
        val enteredSignificantWalking = isSignificantWalking && !significantWalking
        significantWalking = isSignificantWalking

        if (enteredSignificantWalking) {
            burstUntilElapsedMillis = nowElapsedMillis + WALKING_BURST_MILLIS
        }

        val fixIsFresh =
            latestFixElapsedMillis?.let { fixElapsed ->
                nowElapsedMillis - fixElapsed in 0..FRESH_FIX_MILLIS
            } == true
        val refreshIsThrottled =
            lastTriggeredRefreshElapsedMillis?.let { lastRefresh ->
                nowElapsedMillis - lastRefresh < TRIGGER_THROTTLE_MILLIS
            } == true
        val requestFreshFix = enteredSignificantWalking && !fixIsFresh && !refreshIsThrottled
        if (requestFreshFix) lastTriggeredRefreshElapsedMillis = nowElapsedMillis

        val intervalMillis =
            when {
                nowElapsedMillis < burstUntilElapsedMillis -> WALKING_BURST_INTERVAL_MILLIS
                nearIndoorLocation && !isSignificantWalking -> INDOOR_MINIMAL_ACTIVITY_INTERVAL_MILLIS
                else -> DEFAULT_INTERVAL_MILLIS
            }
        return LocationMonitoringDecision(intervalMillis, requestFreshFix)
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 30_000L
        const val INDOOR_MINIMAL_ACTIVITY_INTERVAL_MILLIS = 60_000L
        const val WALKING_BURST_INTERVAL_MILLIS = 5_000L
        const val WALKING_BURST_MILLIS = 30_000L
        const val TRIGGER_THROTTLE_MILLIS = 15_000L
        const val FRESH_FIX_MILLIS = 15_000L
        const val SIGNIFICANT_WALKING_STEPS = 100
    }
}
