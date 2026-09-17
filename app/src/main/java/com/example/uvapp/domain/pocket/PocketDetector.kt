package com.example.uvapp.domain.pocket

/** Stabilizes raw proximity and ambient-light samples into a pocket state. */
class PocketDetector(
    private val enterLuxThreshold: Float = DEFAULT_ENTER_LUX_THRESHOLD,
    private val exitLuxThreshold: Float = DEFAULT_EXIT_LUX_THRESHOLD,
    private val enterConfirmationMs: Long = DEFAULT_ENTER_CONFIRMATION_MS,
    private val exitConfirmationMs: Long = DEFAULT_EXIT_CONFIRMATION_MS,
) {
    private var state = PocketState.UNKNOWN
    private var pendingState: PocketState? = null
    private var pendingSinceMs = 0L

    fun update(sample: PocketSensorSample): PocketDetection {
        val proximityNear = sample.proximityNear
        val ambientLux = sample.ambientLux
        if (proximityNear == null || ambientLux == null) {
            pendingState = null
            return PocketDetection(state = state, isAvailable = false)
        }

        val candidate =
            when {
                proximityNear && ambientLux <= enterLuxThreshold -> PocketState.IN_POCKET
                !proximityNear || ambientLux >= exitLuxThreshold -> PocketState.OUT_OF_POCKET
                else -> null
            }

        if (candidate == null || candidate == state) {
            pendingState = null
            return PocketDetection(state = state, isAvailable = true)
        }

        if (pendingState != candidate) {
            pendingState = candidate
            pendingSinceMs = sample.elapsedRealtimeMs
            return PocketDetection(state = state, isAvailable = true)
        }

        val confirmationMs =
            if (candidate == PocketState.IN_POCKET) enterConfirmationMs else exitConfirmationMs
        if (sample.elapsedRealtimeMs - pendingSinceMs >= confirmationMs) {
            state = candidate
            pendingState = null
        }

        return PocketDetection(state = state, isAvailable = true)
    }

    companion object {
        const val DEFAULT_ENTER_CONFIRMATION_MS = 1_500L
        const val DEFAULT_EXIT_CONFIRMATION_MS = 750L
        private const val DEFAULT_ENTER_LUX_THRESHOLD = 10f
        private const val DEFAULT_EXIT_LUX_THRESHOLD = 50f
    }
}
