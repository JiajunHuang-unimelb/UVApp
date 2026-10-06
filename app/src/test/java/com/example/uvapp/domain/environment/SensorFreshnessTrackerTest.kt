package com.example.uvapp.domain.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SensorFreshnessTrackerTest {
    @Test
    fun `continuous sensor expires at timeout boundary`() {
        val tracker = SensorFreshnessTracker(staleAfterMillis = 30_000L)
        tracker.monitoringStarted(10_000L)

        assertFalse(tracker.consumeExpiration(39_999L))
        assertTrue(tracker.consumeExpiration(40_000L))
    }

    @Test
    fun `expiration is reported once until another sample arrives`() {
        val tracker = SensorFreshnessTracker(staleAfterMillis = 30_000L)
        tracker.monitoringStarted(0L)

        assertTrue(tracker.consumeExpiration(30_000L))
        assertFalse(tracker.consumeExpiration(60_000L))

        tracker.onSample(60_000L)
        assertTrue(tracker.consumeExpiration(90_000L))
    }

    @Test
    fun `unavailable tracker does not produce repeated expirations`() {
        val tracker = SensorFreshnessTracker(staleAfterMillis = 30_000L)
        tracker.monitoringStarted(0L)
        tracker.markUnavailable()

        assertFalse(tracker.consumeExpiration(60_000L))
    }

    @Test
    fun `clock rollback starts a new freshness window`() {
        val tracker = SensorFreshnessTracker(staleAfterMillis = 30_000L)
        tracker.onSample(50_000L)

        assertFalse(tracker.consumeExpiration(1_000L))
        assertFalse(tracker.consumeExpiration(30_999L))
        assertTrue(tracker.consumeExpiration(31_000L))
    }

    @Test
    fun `invalid timeout and elapsed values are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { SensorFreshnessTracker(0L) }
        val tracker = SensorFreshnessTracker(1L)
        assertThrows(IllegalArgumentException::class.java) { tracker.monitoringStarted(-1L) }
        assertThrows(IllegalArgumentException::class.java) { tracker.onSample(-1L) }
        assertThrows(IllegalArgumentException::class.java) { tracker.consumeExpiration(-1L) }
    }
}
