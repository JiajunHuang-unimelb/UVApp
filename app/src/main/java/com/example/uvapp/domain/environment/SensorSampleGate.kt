package com.example.uvapp.domain.environment

/** Limits expensive sensor processing while allowing the first sample through immediately. */
class SensorSampleGate(
    private val minimumIntervalMillis: Long,
    private val elapsedRealtimeMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
) {
    private var lastAcceptedElapsedMillis: Long? = null

    init {
        require(minimumIntervalMillis > 0L)
    }

    fun tryAcquire(): Boolean {
        val now = elapsedRealtimeMillis()
        if (now < 0L) return false
        val previous = lastAcceptedElapsedMillis
        val accepted = previous == null || now < previous || now - previous >= minimumIntervalMillis
        if (accepted) lastAcceptedElapsedMillis = now
        return accepted
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
