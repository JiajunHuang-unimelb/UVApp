package com.example.uvapp.domain.alerts

import com.example.uvapp.domain.model.UvBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SunProtectionTrackerTest {
    private val minute = 60_000L
    private val tracker = SunProtectionTracker()
    private var now = 0L

    private fun tick(
        uv: Double = 7.0,
        running: Boolean = true,
        walking: Boolean = false,
        spf: Int = 50,
        advanceMinutes: Long = 1,
        sessionActive: Boolean = true,
    ): SunProtectionAlert? {
        now += advanceMinutes * minute
        return tracker.update(now, sessionActive, running, uv, walking, spf)
    }

    /** Ticks one minute at a time and returns the first alert, if any. */
    private fun runMinutes(minutes: Int, walking: Boolean = false): SunProtectionAlert? {
        repeat(minutes) { tick(walking = walking)?.let { return it } }
        return null
    }

    @Test
    fun `low UV gives no band alert`() {
        assertNull(tick(uv = 2.0))
    }

    @Test
    fun `moderate recommends SPF 30 and high recommends SPF 50`() {
        assertEquals(30, tick(uv = 4.0)?.recommendedSpf)
        assertEquals(50, tick(uv = 7.0)?.recommendedSpf)
    }

    @Test
    fun `band alert fires once at start and again only when the band rises`() {
        assertEquals(SunProtectionAlertKind.BAND, tick(uv = 4.0)?.kind)
        assertNull(tick(uv = 4.5))
        assertNull(tick(uv = 2.0))
        assertNull(tick(uv = 4.0))
        assertEquals(UvBand.HIGH, tick(uv = 6.5)?.band)
    }

    @Test
    fun `alert is held while paused and shown on resume`() {
        assertNull(tick(uv = 7.0, running = false))
        assertEquals(SunProtectionAlertKind.BAND, tick(uv = 7.0, running = true)?.kind)
    }

    @Test
    fun `no reapply reminder before I have applied`() {
        tick()
        assertNull(runMinutes(180))
    }

    @Test
    fun `reapply reminder fires after two hours of exposure, once`() {
        tick()
        tracker.markApplied()
        assertNull(runMinutes(119))
        assertEquals(SunProtectionAlertKind.REAPPLY, tick()?.kind)
        assertNull(runMinutes(60))
    }

    @Test
    fun `paused time does not count toward the reapply timer`() {
        tick()
        tracker.markApplied()
        runMinutes(60)
        assertNull(tick(running = false, advanceMinutes = 120))
        assertNull(runMinutes(59))
        assertEquals(SunProtectionAlertKind.REAPPLY, tick()?.kind)
    }

    @Test
    fun `applying again restarts the timer`() {
        tick()
        tracker.markApplied()
        runMinutes(100)
        tracker.markApplied()
        assertEquals(120 * minute, tracker.reapplyRemainingMillis)
    }

    @Test
    fun `twenty minutes of walking brings the reminder thirty minutes forward`() {
        tick()
        tracker.markApplied()
        assertNull(runMinutes(20, walking = true))
        assertNull(runMinutes(69))
        assertEquals(SunProtectionAlertKind.ACTIVITY, tick()?.kind)
    }

    @Test
    fun `less than twenty minutes of walking keeps the two hour reminder`() {
        tick()
        tracker.markApplied()
        runMinutes(19, walking = true)
        assertNull(runMinutes(100))
        assertEquals(SunProtectionAlertKind.REAPPLY, tick()?.kind)
    }

    @Test
    fun `ending the session clears everything`() {
        tick()
        tracker.markApplied()
        tick(sessionActive = false)
        assertNull(tracker.reapplyRemainingMillis)
        assertEquals(SunProtectionAlertKind.BAND, tick()?.kind)
    }

    @Test
    fun `texts match the agreed wording`() {
        val high = SunProtectionAlert(SunProtectionAlertKind.BAND, UvBand.HIGH, 7.0, 50, 15)
        assertEquals("UV High (7.0) - apply SPF 50+", high.title())
        assertEquals("Add a hat and seek shade. Your SPF 15 is below this.", high.body())
        val moderate = SunProtectionAlert(SunProtectionAlertKind.BAND, UvBand.MODERATE, 4.0, 30, 30)
        assertEquals("Sun protection is recommended when UV is 3 or above.", moderate.body())
        val reapply = SunProtectionAlert(SunProtectionAlertKind.REAPPLY, UvBand.HIGH, 7.0, 50, 50)
        assertEquals("2 hours since you applied. UV is High (7.0).", reapply.body())
        val activity = SunProtectionAlert(SunProtectionAlertKind.ACTIVITY, UvBand.HIGH, 7.0, 50, 50)
        assertEquals("Time to reassess your sunscreen", activity.title())
    }
}
