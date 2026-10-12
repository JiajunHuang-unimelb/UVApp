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


    @Test
    fun `precise fix replaces the held one`() {
        assertTrue(LocationFixValidator.shouldReplace(accuracyMeters = 9.7f, maxAccuracyMeters = 50f))
        assertTrue(LocationFixValidator.shouldReplace(accuracyMeters = 50f, maxAccuracyMeters = 50f))
    }

    @Test
    fun `coarse or missing accuracy never replaces the held fix`() {
        assertFalse(LocationFixValidator.shouldReplace(accuracyMeters = 206.8f, maxAccuracyMeters = 50f))
        assertFalse(LocationFixValidator.shouldReplace(accuracyMeters = null, maxAccuracyMeters = 50f))
        assertFalse(LocationFixValidator.shouldReplace(accuracyMeters = Float.NaN, maxAccuracyMeters = 50f))
    }

    @Test
    fun `kept precise fix still expires after the age limit`() {
        // A coarse fix arriving later is ignored, so the precise fix taken at 10 s stays in use.
        assertTrue(isUsable(accuracyMeters = 15f, fixElapsedMillis = 10_000L, nowElapsedMillis = 85_000L))
        assertFalse(isUsable(accuracyMeters = 15f, fixElapsedMillis = 10_000L, nowElapsedMillis = 85_001L))
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
