package com.example.uvapp.domain.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFixValidatorTest {
    @Test
    fun `accurate fix at age boundary is usable`() {
        assertTrue(isUsable(accuracyMeters = 50f, fixElapsedMillis = 25_000L, nowElapsedMillis = 100_000L))
    }

    @Test
    fun `stale or future fix is rejected`() {
        assertFalse(isUsable(accuracyMeters = 10f, fixElapsedMillis = 24_999L, nowElapsedMillis = 100_000L))
        assertFalse(isUsable(accuracyMeters = 10f, fixElapsedMillis = 100_001L, nowElapsedMillis = 100_000L))
    }

    @Test
    fun `missing monotonic timestamp is rejected`() {
        assertFalse(isUsable(accuracyMeters = 10f, fixElapsedMillis = null, nowElapsedMillis = 100_000L))
    }

    @Test
    fun `invalid or inaccurate readings are rejected`() {
        assertFalse(isUsable(accuracyMeters = 50.1f, fixElapsedMillis = 90_000L, nowElapsedMillis = 100_000L))
        assertFalse(isUsable(accuracyMeters = -1f, fixElapsedMillis = 90_000L, nowElapsedMillis = 100_000L))
        assertFalse(isUsable(accuracyMeters = Float.NaN, fixElapsedMillis = 90_000L, nowElapsedMillis = 100_000L))
    }

    private fun isUsable(
        accuracyMeters: Float,
        fixElapsedMillis: Long?,
        nowElapsedMillis: Long,
    ): Boolean =
        LocationFixValidator.isUsable(
            accuracyMeters = accuracyMeters,
            fixElapsedMillis = fixElapsedMillis,
            nowElapsedMillis = nowElapsedMillis,
            maxAccuracyMeters = 50f,
            maxAgeMillis = 75_000L,
        )
}
