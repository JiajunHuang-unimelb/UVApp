package com.example.uvapp.domain.exposure

import com.example.uvapp.domain.model.SkinType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureSessionManagerTest {
    private val session = ExposureSessionManager()

    @Test
    fun `new session is not started`() {
        val snapshot = session.snapshot()

        assertEquals(ExposureStatus.NOT_STARTED, snapshot.status)
        assertFalse(snapshot.isStarted)
        assertFalse(snapshot.isRunning)
        assertEquals(1.0, snapshot.doseLimitSed, EPSILON)
    }

    @Test
    fun `start creates an empty running session with a personalized limit`() {
        val snapshot = session.start(SkinType.II, 8.0, 0L)

        assertEquals(ExposureStatus.RUNNING, snapshot.status)
        assertTrue(snapshot.isRunning)
        assertEquals(0.0, snapshot.accumulatedDoseSed, 0.0)
        assertEquals(1.0, snapshot.doseLimitSed, EPSILON)
        assertEquals(500L, snapshot.estimatedRemainingSeconds)
    }

    @Test
    fun `refresh accumulates dose without double counting`() {
        session.start(SkinType.V, 5.0, 0L)

        assertEquals(0.375, session.refresh(300_000L).accumulatedDoseSed, EPSILON)
        assertEquals(0.75, session.refresh(600_000L).accumulatedDoseSed, EPSILON)
    }

    @Test
    fun `pause and resume exclude paused time`() {
        session.start(SkinType.V, 5.0, 0L)
        assertEquals(ExposureStatus.PAUSED, session.pause(300_000L).status)
        session.refresh(1_800_000L)
        assertEquals(ExposureStatus.RUNNING, session.resume(2_100_000L).status)

        val snapshot = session.refresh(2_400_000L)

        assertEquals(0.75, snapshot.accumulatedDoseSed, EPSILON)
    }

    @Test
    fun `pause and resume are idempotent`() {
        session.start(SkinType.V, 5.0, 0L)

        assertEquals(ExposureStatus.PAUSED, session.pause(300_000L).status)
        assertEquals(ExposureStatus.PAUSED, session.pause(600_000L).status)
        assertEquals(ExposureStatus.RUNNING, session.resume(1_800_000L).status)
        assertEquals(ExposureStatus.RUNNING, session.resume(1_800_000L).status)
    }

    @Test
    fun `threshold completion stops further accumulation`() {
        val manager = ExposureSessionManager()
    
        val start = 0L
    
        manager.start(
            skinType = SkinType.II,
            uvIndex = 10.0,
            nowElapsedMs = start,
        )
    
        // Skin type II has a 1.0 SED personal dose limit.
        // 7 minutes at UVI 10:
        // 0.015 * 10 * 7 = 1.05 SED.
        val thresholdTime = 420_000L
    
        val atThreshold = manager.refresh(thresholdTime)
    
        assertEquals(
            ExposureStatus.COMPLETE,
            atThreshold.status,
        )
        assertEquals(
            1.05,
            atThreshold.accumulatedDoseSed,
            1e-9,
        )
    
        // Exposure stops after reaching the dose limit.
        val later = manager.refresh(thresholdTime + 60_000L)
    
        assertEquals(
            ExposureStatus.COMPLETE,
            later.status,
        )
        assertEquals(
            1.05,
            later.accumulatedDoseSed,
            1e-9,
        )
    }

    @Test
    fun `clear resets the authoritative session`() {
        session.start(SkinType.V, 5.0, 0L)
        session.refresh(300_000L)

        val cleared = session.clear(300_000L)
        val later = session.refresh(1_800_000L)

        assertEquals(ExposureStatus.NOT_STARTED, cleared.status)
        assertEquals(0.0, cleared.accumulatedDoseSed, 0.0)
        assertEquals(0.0, later.accumulatedDoseSed, 0.0)
    }

    @Test
    fun `UV and context changes settle the previous intervals`() {
        session.start(SkinType.V, 4.0, 0L, ExposureContext.DIRECT_SUN)
        session.updateUvIndex(8.0, 300_000L)
        session.updateContext(ExposureContext.SHADE, 600_000L)

        val snapshot = session.refresh(900_000L)

        assertEquals(1.08, snapshot.accumulatedDoseSed, EPSILON)
        assertEquals(ExposureContext.SHADE, snapshot.context)
    }

    @Test
    fun `skin type change preserves dose and can complete the session`() {
        session.start(SkinType.VI, 8.0, 0L)
        val before = session.refresh(600_000L)

        val after = session.updateSkinType(SkinType.I, 600_000L)

        assertEquals(before.accumulatedDoseSed, after.accumulatedDoseSed, 0.0)
        assertEquals(0.8, after.doseLimitSed, EPSILON)
        assertEquals(ExposureStatus.COMPLETE, after.status)
    }

    @Test
    fun `zero UV preserves dose and makes remaining time unavailable`() {
        session.start(SkinType.V, 6.0, 0L)
        session.updateUvIndex(0.0, 300_000L)

        val snapshot = session.refresh(600_000L)

        assertEquals(0.45, snapshot.accumulatedDoseSed, EPSILON)
        assertNull(snapshot.estimatedRemainingMinutes)
    }

    @Test
    fun `an earlier timestamp cannot reduce or add dose`() {
        session.start(SkinType.V, 5.0, 600_000L)

        assertEquals(0.0, session.refresh(300_000L).accumulatedDoseSed, 0.0)
        assertEquals(0.75, session.refresh(1_200_000L).accumulatedDoseSed, EPSILON)
    }

    @Test
    fun `input updates before start do not accumulate dose`() {
        session.updateUvIndex(10.0, 600_000L)
        session.updateContext(ExposureContext.DIRECT_SUN, 1_200_000L)

        val snapshot = session.refresh(1_800_000L)

        assertEquals(ExposureStatus.NOT_STARTED, snapshot.status)
        assertEquals(0.0, snapshot.accumulatedDoseSed, 0.0)
    }

    @Test
    fun `daily allowance reduces countdown without seeding session history`() {
        val manager = ExposureSessionManager(useDailyBudget = true)
        val start = manager.start(SkinType.II, 8.0, 0L, initialDailyDoseSed = 0.4)
        assertEquals(0.0, start.accumulatedDoseSed, 0.0)
        assertEquals(0.4, start.dailyAccumulatedDoseSed!!, EPSILON)
        assertEquals(0.6, start.remainingDoseSed, EPSILON)
        assertEquals(300L, start.estimatedRemainingSeconds)
        assertEquals(500L, start.estimatedTotalSeconds)
        assertEquals(0.4, start.exposureFraction, EPSILON)
    }

    @Test
    fun `daily remaining dose is recalculated at the current shade factor and UV`() {
        val manager = ExposureSessionManager(useDailyBudget = true)
        val start = manager.start(SkinType.II, 8.0, 0L, ExposureContext.SHADE, initialDailyDoseSed = 0.4)
        assertEquals(1_000L, start.estimatedRemainingSeconds)
        val lowerUv = manager.updateUvIndex(4.0, 0L)
        assertEquals(2_000L, lowerUv.estimatedRemainingSeconds)
        assertEquals(0.6, lowerUv.remainingDoseSed, EPSILON)
    }

    @Test
    fun `exhausted daily allowance starts complete even with no UV`() {
        val manager = ExposureSessionManager(useDailyBudget = true)
        val start = manager.start(SkinType.II, 0.0, 0L, initialDailyDoseSed = 1.2)
        assertEquals(ExposureStatus.COMPLETE, start.status)
        assertEquals(0.0, start.remainingDoseSed, 0.0)
        assertEquals(0.0, manager.refresh(60_000L).accumulatedDoseSed, 0.0)
    }

    @Test
    fun `daily mode completes from daily dose and never writes it into session dose`() {
        val manager = ExposureSessionManager(useDailyBudget = true)
        manager.start(SkinType.II, 8.0, 0L, initialDailyDoseSed = 0.9)
        manager.refresh(60_000L)
        val complete = manager.updateDailyDose(1.02)
        assertEquals(ExposureStatus.COMPLETE, complete.status)
        assertEquals(0.12, complete.accumulatedDoseSed, EPSILON)
        assertEquals(0.12, manager.refresh(120_000L).accumulatedDoseSed, EPSILON)
        // A new date or deleted history does not silently restart a completed session.
        assertEquals(ExposureStatus.COMPLETE, manager.updateDailyDose(0.0).status)
    }

    @Test
    fun `daily allowance can change while session lifetime dose is preserved`() {
        val manager = ExposureSessionManager(useDailyBudget = true)
        manager.start(SkinType.II, 8.0, 0L, initialDailyDoseSed = 0.4)
        manager.refresh(60_000L)
        manager.updateDailyDose(0.52)
        val nextDay = manager.updateDailyDose(0.02)
        assertEquals(0.12, nextDay.accumulatedDoseSed, EPSILON)
        assertEquals(0.98, nextDay.remainingDoseSed, EPSILON)
        assertEquals(ExposureStatus.RUNNING, nextDay.status)
        val loading = manager.updateDailyDose(null)
        assertNull(loading.estimatedRemainingSeconds)
        assertNull(loading.estimatedTotalSeconds)
        assertEquals(ExposureStatus.RUNNING, loading.status)
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
