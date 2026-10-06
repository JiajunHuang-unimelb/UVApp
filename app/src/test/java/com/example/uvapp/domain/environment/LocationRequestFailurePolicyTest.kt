package com.example.uvapp.domain.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationRequestFailurePolicyTest {
    @Test
    fun `ordinary failure is retried after backoff boundary`() {
        val policy = LocationRequestFailurePolicy(retryDelayMillis = 30_000L)

        val decision = policy.recordFailure(IllegalStateException("unavailable"), 10_000L)

        assertFalse(decision.permissionRevoked)
        assertFalse(policy.canRequest(39_999L))
        assertTrue(policy.canRequest(40_000L))
    }

    @Test
    fun `security failure tells service to stop permission protected requests`() {
        val policy = LocationRequestFailurePolicy()

        val decision = policy.recordFailure(SecurityException("permission revoked"), 10_000L)

        assertTrue(decision.permissionRevoked)
    }

    @Test
    fun `null fix failure also receives backoff`() {
        val policy = LocationRequestFailurePolicy(retryDelayMillis = 1_000L)
        policy.recordFailure(error = null, nowElapsedMillis = 5_000L)

        assertFalse(policy.canRequest(5_999L))
        assertTrue(policy.canRequest(6_000L))
    }

    @Test
    fun `successful fix clears an earlier failure`() {
        val policy = LocationRequestFailurePolicy(retryDelayMillis = 30_000L)
        policy.recordFailure(IllegalStateException(), 10_000L)

        policy.recordSuccess()

        assertTrue(policy.canRequest(10_001L))
    }

    @Test
    fun `clock rollback clears retry deadline`() {
        val policy = LocationRequestFailurePolicy(retryDelayMillis = 30_000L)
        policy.recordFailure(IllegalStateException(), 50_000L)

        assertTrue(policy.canRequest(1_000L))
    }

    @Test
    fun `invalid retry configuration and time are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { LocationRequestFailurePolicy(0L) }
        val policy = LocationRequestFailurePolicy()
        assertThrows(IllegalArgumentException::class.java) { policy.canRequest(-1L) }
        assertThrows(IllegalArgumentException::class.java) {
            policy.recordFailure(null, -1L)
        }
    }
}
