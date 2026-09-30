package com.example.uvapp.domain.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StepCounterTrackerTest {
    @Test
    fun `counter is rebased to monitoring session`() {
        val tracker = StepCounterTracker()

        assertEquals(StepActivityReading(0, 0), tracker.update(1_000f, 10_000L))
        assertEquals(StepActivityReading(20, 120), tracker.update(1_020f, 20_000L))
    }

    @Test
    fun `counter reset starts a new session`() {
        val tracker = StepCounterTracker()
        tracker.update(100f, 1_000L)

        assertEquals(StepActivityReading(0, 0), tracker.update(2f, 2_000L))
    }

    @Test
    fun `invalid reading is ignored`() {
        assertNull(StepCounterTracker().update(Float.NaN, 1_000L))
    }
}
