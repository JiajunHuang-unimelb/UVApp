package com.example.uvapp.domain.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepCounterTrackerTest {
    @Test
    fun `counter is rebased to monitoring session`() {
        val tracker = StepCounterTracker()

        assertEquals(
            StepActivityReading(0, 0, 0, null, StepActivity.STATIONARY),
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
            StepActivityReading(0, 0, 0, null, StepActivity.STATIONARY),
            tracker.update(2f, 2_000L),
        )
    }

    @Test
    fun `elapsed clock rollback starts a new session`() {
        val tracker = StepCounterTracker()
        tracker.update(100f, 10_000L)
        tracker.update(105f, 20_000L)

        assertEquals(
            StepActivityReading(0, 0, 0, null, StepActivity.STATIONARY),
            tracker.update(106f, 5_000L),
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
    fun `walking expires after fifteen seconds while steps remain recent`() {
        val tracker = StepCounterTracker()
        tracker.update(100f, 0L)
        tracker.update(103f, 10_000L)

        val reading = tracker.snapshot(25_001L)

        assertEquals(3, reading?.recentSteps)
        assertEquals(StepActivity.STATIONARY, reading?.activity)
    }

    @Test
    fun `separate bursts are summed inside recent window`() {
        val tracker = StepCounterTracker()
        tracker.update(100f, 0L)
        tracker.update(102f, 10_000L)
        val reading = tracker.update(105f, 20_000L)

        assertEquals(5, reading?.stepsSinceStart)
        assertEquals(5, reading?.recentSteps)
        assertEquals(StepActivity.WALKING, reading?.activity)
    }

    @Test
    fun `unrealistic average rate is capped`() {
        val tracker = StepCounterTracker()
        tracker.update(0f, 0L)

        val reading = tracker.update(10_000f, 5_000L)

        assertEquals(300, reading?.averageStepsPerMinute)
    }

    @Test
    fun `inactivity transitions to stationary without a new sensor event`() {
        val tracker = StepCounterTracker()
        tracker.update(100f, 0L)
        tracker.update(103f, 10_000L)

        assertEquals(StepActivity.WALKING, tracker.snapshot(25_000L)?.activity)
        val stationary = tracker.snapshot(25_001L)
        assertEquals(StepActivity.STATIONARY, stationary?.activity)
        assertEquals(3, stationary?.recentSteps)
        assertEquals(0, tracker.snapshot(70_001L)?.recentSteps)
    }

    @Test
    fun `no steps is stationary immediately after first valid reading`() {
        val tracker = StepCounterTracker()
        tracker.update(100f, 5_000L)

        assertEquals(StepActivity.STATIONARY, tracker.snapshot(5_000L)?.activity)
    }

    @Test
    fun `one or two incidental steps remain stationary`() {
        val tracker = StepCounterTracker()
        tracker.update(100f, 0L)

        assertEquals(StepActivity.STATIONARY, tracker.update(101f, 1_000L)?.activity)
        assertEquals(StepActivity.STATIONARY, tracker.update(102f, 2_000L)?.activity)
        assertEquals(StepActivity.WALKING, tracker.update(103f, 3_000L)?.activity)
        assertEquals(StepActivity.STATIONARY, tracker.snapshot(16_001L)?.activity)
    }

    @Test
    fun `three steps spread beyond fifteen seconds remain stationary`() {
        val tracker = StepCounterTracker()
        tracker.update(100f, 0L)
        tracker.update(101f, 1_000L)
        tracker.update(102f, 10_000L)

        val reading = tracker.update(103f, 16_001L)
        assertEquals(3, reading?.recentSteps)
        assertEquals(StepActivity.STATIONARY, reading?.activity)
    }

    @Test
    fun `step activity is unavailable before first valid reading`() {
        val tracker = StepCounterTracker()
        assertNull(tracker.snapshot(0L))
        assertNull(tracker.update(Float.NaN, 1_000L))
        assertNull(tracker.snapshot(2_000L))
    }
}
