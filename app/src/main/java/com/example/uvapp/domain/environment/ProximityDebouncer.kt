package com.example.uvapp.domain.environment

/** Applies an immediate covered state and a delayed clear state to proximity readings. */
class ProximityDebouncer(
    private val clearDelayMillis: Long = DEFAULT_CLEAR_DELAY_MILLIS,
) {
    private var stableValue: Boolean? = null
    private var clearRequestedElapsedMillis: Long? = null

    init {
        require(clearDelayMillis > 0L)
    }

    fun update(
        isOccluded: Boolean,
        nowElapsedMillis: Long,
    ): Boolean? {
        require(nowElapsedMillis >= 0L)
        if (isOccluded) {
            // Covered light readings are unsafe immediately; do not debounce this direction.
            stableValue = true
            clearRequestedElapsedMillis = null
        } else if (stableValue == true) {
            if (clearRequestedElapsedMillis == null) {
                clearRequestedElapsedMillis = nowElapsedMillis
            }
        } else {
            stableValue = false
            clearRequestedElapsedMillis = null
        }
        return currentValue(nowElapsedMillis)
    }

    fun currentValue(nowElapsedMillis: Long): Boolean? {
        require(nowElapsedMillis >= 0L)
        val clearRequested = clearRequestedElapsedMillis ?: return stableValue
        if (nowElapsedMillis < clearRequested) {
            // A reboot/test clock reset restarts the pending clear period.
            clearRequestedElapsedMillis = nowElapsedMillis
            return stableValue
        }
        if (nowElapsedMillis - clearRequested >= clearDelayMillis) {
            stableValue = false
            clearRequestedElapsedMillis = null
        }
        return stableValue
    }

    fun reset() {
        stableValue = null
        clearRequestedElapsedMillis = null
    }

    private companion object {
        const val DEFAULT_CLEAR_DELAY_MILLIS = 1_000L
    }
}
