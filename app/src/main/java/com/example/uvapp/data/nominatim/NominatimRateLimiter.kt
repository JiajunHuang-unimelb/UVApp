package com.example.uvapp.data.nominatim

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes public Nominatim calls and keeps their start times one second apart. */
internal class NominatimRateLimiter(
    private val monotonicMillis: () -> Long = { System.nanoTime() / NANOS_PER_MILLISECOND },
    private val wait: suspend (Long) -> Unit = { delayMillis -> delay(delayMillis) },
) {
    private val mutex = Mutex()
    private var lastRequestStartedAtMillis: Long? = null

    suspend fun <T> run(request: suspend () -> T): T =
        mutex.withLock {
            val previousStart = lastRequestStartedAtMillis
            if (previousStart != null) {
                val remainingDelay = MINIMUM_INTERVAL_MILLIS - (monotonicMillis() - previousStart)
                if (remainingDelay > 0) {
                    wait(remainingDelay)
                }
            }

            lastRequestStartedAtMillis = monotonicMillis()
            request()
        }

    companion object {
        val shared = NominatimRateLimiter()

        private const val MINIMUM_INTERVAL_MILLIS = 1_000L
        private const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
