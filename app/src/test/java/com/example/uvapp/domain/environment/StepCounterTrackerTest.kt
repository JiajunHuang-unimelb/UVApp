package com.example.uvapp.domain.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepCounterTrackerTest {
    @Test
    fun `counter is rebased to monitoring session`() {
        val tracker = StepCounterTracker()

        assertEquals(
            StepActivityReading(0, 0, 0, null, StepActivity.UNKNOWN),
            tracker.update(1_000f, 10_000L),
        )
        assertEquals(
            StepActivityReading(20, 20, 120, 20_000L, StepActivity.WALKING),
            tracker.update(1_020f, 20_000L),
        )
    }

    @Test
    fun `counter reset starts a new session`() {
        val tracker = StepCounterTracker()
        tracker.update(100f, 1_000L)

        assertEquals(
            StepActivityReading(0, 0, 0, null, StepActivity.UNKNOWN),
            tracker.update(2f, 2_000L),
        )
    }

    @Test
    fun `invalid reading is ignored`() {
        assertNull(StepCounterTracker().update(Float.NaN, 1_000L))
    }

    @Test
    fun `three recent steps classify walking`() {
        val tracker = StepCounterTracker()
        tracker.update(100f, 0L)

        val reading = tracker.update(103f, 10_000L)

        assertEquals(3, reading?.recentSteps)
        assertEquals(10_000L, reading?.lastStepElapsedMillis)
        assertEquals(StepActivity.WALKING, reading?.activity)
    }

    @Test
    fun `inactivity transitions to stationary without a new sensor event`() {
        val tracker = StepCounterTracker()
        tracker.update(100f, 0L)
        tracker.update(103f, 10_000L)

        assertEquals(StepActivity.UNKNOWN, tracker.snapshot(69_999L)?.activity)
        val stationary = tracker.snapshot(70_000L)
        assertEquals(StepActivity.STATIONARY, stationary?.activity)
        assertEquals(3, stationary?.recentSteps)
        assertEquals(0, tracker.snapshot(70_001L)?.recentSteps)
    }

    @Test
    fun `no steps becomes stationary after initial observation window`() {
        val tracker = StepCounterTracker()
        tracker.update(100f, 5_000L)

        assertEquals(StepActivity.UNKNOWN, tracker.snapshot(64_999L)?.activity)
        assertEquals(StepActivity.STATIONARY, tracker.snapshot(65_000L)?.activity)
    }
}
