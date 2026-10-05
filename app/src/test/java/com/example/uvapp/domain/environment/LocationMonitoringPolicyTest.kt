package com.example.uvapp.domain.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationMonitoringPolicyTest {
    @Test
    fun `outside locations uses thirty second updates`() {
        val decision = LocationMonitoringPolicy().evaluate(0L, false, stationary(0), null)

        assertEquals(30_000L, decision.intervalMillis)
        assertFalse(decision.requestFreshFix)
    }

    @Test
    fun `inside with stationary or minimal walking uses sixty second updates`() {
        val policy = LocationMonitoringPolicy()

        assertEquals(60_000L, policy.evaluate(0L, true, stationary(0), null).intervalMillis)
        assertEquals(60_000L, policy.evaluate(1_000L, true, walking(100), null).intervalMillis)
    }

    @Test
    fun `significant walking requests a fix and starts five second burst`() {
        val policy = LocationMonitoringPolicy()

        val entered = policy.evaluate(20_000L, true, walking(101), latestFixElapsedMillis = 0L)
        assertEquals(5_000L, entered.intervalMillis)
        assertTrue(entered.requestFreshFix)

        val duringBurst = policy.evaluate(49_999L, true, walking(120), latestFixElapsedMillis = 20_000L)
        assertEquals(5_000L, duringBurst.intervalMillis)
        assertFalse(duringBurst.requestFreshFix)

        val afterBurst = policy.evaluate(50_000L, true, walking(130), latestFixElapsedMillis = 20_000L)
        assertEquals(30_000L, afterBurst.intervalMillis)
    }

    @Test
    fun `fresh existing fix suppresses triggered request but keeps burst`() {
        val decision =
            LocationMonitoringPolicy().evaluate(
                nowElapsedMillis = 20_000L,
                nearIndoorLocation = true,
                stepReading = walking(101),
                latestFixElapsedMillis = 10_000L,
            )

        assertEquals(5_000L, decision.intervalMillis)
        assertFalse(decision.requestFreshFix)
    }

    @Test
    fun `triggered requests are throttled across walking transitions`() {
        val policy = LocationMonitoringPolicy()
        assertTrue(policy.evaluate(20_000L, true, walking(101), null).requestFreshFix)
        policy.evaluate(25_000L, true, stationary(101), null)

        assertFalse(policy.evaluate(30_000L, true, walking(102), null).requestFreshFix)
        policy.evaluate(35_000L, true, stationary(102), null)
        assertTrue(policy.evaluate(35_001L, true, walking(103), null).requestFreshFix)
    }

    private fun stationary(steps: Int) =
        StepActivityReading(steps, 0, 0, null, StepActivity.STATIONARY)

    private fun walking(steps: Int) =
        StepActivityReading(steps, 3, 20, 0L, StepActivity.WALKING)
}
