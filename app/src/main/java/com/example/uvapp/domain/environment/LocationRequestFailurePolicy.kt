package com.example.uvapp.domain.environment

/** Controls retry timing after asynchronous fused-location request failures. */
class LocationRequestFailurePolicy(
    private val retryDelayMillis: Long = DEFAULT_RETRY_DELAY_MILLIS,
) {
    private var lastFailureElapsedMillis: Long? = null

    init {
        require(retryDelayMillis > 0L)
    }

    fun recordFailure(
        error: Throwable?,
        nowElapsedMillis: Long,
    ): LocationFailureDecision {
        require(nowElapsedMillis >= 0L)
        val permissionRevoked = error is SecurityException
        lastFailureElapsedMillis = if (permissionRevoked) null else nowElapsedMillis
        return LocationFailureDecision(permissionRevoked = permissionRevoked)
    }

    fun canRequest(nowElapsedMillis: Long): Boolean {
        require(nowElapsedMillis >= 0L)
        val lastFailure = lastFailureElapsedMillis ?: return true
        if (nowElapsedMillis < lastFailure) {
            // A reboot/test clock reset must not block requests indefinitely.
            lastFailureElapsedMillis = null
            return true
        }
        return nowElapsedMillis - lastFailure >= retryDelayMillis
    }

    fun recordSuccess() {
        lastFailureElapsedMillis = null
    }

    private companion object {
        const val DEFAULT_RETRY_DELAY_MILLIS = 30_000L
    }
}

data class LocationFailureDecision(
    val permissionRevoked: Boolean,
)
