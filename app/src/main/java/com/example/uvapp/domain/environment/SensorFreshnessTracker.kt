package com.example.uvapp.domain.environment

/** Detects a silent failure in a sensor that is expected to report continuously. */
class SensorFreshnessTracker(
    private val staleAfterMillis: Long,
) {
    private var lastActivityElapsedMillis: Long? = null
    private var expirationReported = false

    init {
        require(staleAfterMillis > 0L)
    }

    fun monitoringStarted(elapsedMillis: Long) {
        require(elapsedMillis >= 0L)
        lastActivityElapsedMillis = elapsedMillis
        expirationReported = false
    }

    fun onSample(elapsedMillis: Long) {
        require(elapsedMillis >= 0L)
        lastActivityElapsedMillis = elapsedMillis
        expirationReported = false
    }

    /** Returns true once for each stale period; a later sample enables detection again. */
    fun consumeExpiration(nowElapsedMillis: Long): Boolean {
        require(nowElapsedMillis >= 0L)
        val lastActivity = lastActivityElapsedMillis ?: return false
        if (nowElapsedMillis < lastActivity) {
            // A reboot/test clock reset starts a new freshness window.
            lastActivityElapsedMillis = nowElapsedMillis
            expirationReported = false
            return false
        }
        if (expirationReported || nowElapsedMillis - lastActivity < staleAfterMillis) return false

        expirationReported = true
        return true
    }

    fun markUnavailable() {
        lastActivityElapsedMillis = null
        expirationReported = false
    }
}
