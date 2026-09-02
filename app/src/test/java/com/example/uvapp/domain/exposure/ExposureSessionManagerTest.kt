package com.example.uvapp.domain.exposure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureSessionManagerTest {
    private val session = ExposureSessionManager()

    @Test
    fun `start creates an empty running session for the selected skin type`() {
        val snapshot = session.start(SkinType.TYPE_II, 8.0, 0L)

        assertTrue(snapshot.isRunning)
        assertEquals(SkinType.TYPE_II, snapshot.skinType)
        assertEquals(ExposureContext.UNKNOWN, snapshot.context)
        assertEquals(0.0, snapshot.accumulatedDoseSed, 0.0)
        assertEquals(2.5, snapshot.doseLimitSed, 0.0)
        assertEquals(20.833333333333332, snapshot.estimatedRemainingMinutes!!, EPSILON)
    }

    @Test
    fun `refresh accumulates dose without double counting`() {
        session.start(SkinType.TYPE_III, 10.0, 0L)

        assertEquals(0.75, session.refresh(300_000L).accumulatedDoseSed, EPSILON)
        assertEquals(1.5, session.refresh(600_000L).accumulatedDoseSed, EPSILON)
    }

    @Test
    fun `an earlier timestamp cannot reduce or add dose`() {
        session.start(SkinType.TYPE_III, 10.0, 600_000L)

        assertEquals(0.0, session.refresh(300_000L).accumulatedDoseSed, 0.0)
        assertEquals(1.5, session.refresh(1_200_000L).accumulatedDoseSed, EPSILON)
    }

    @Test
    fun `paused time contributes no dose`() {
        session.start(SkinType.TYPE_III, 10.0, 0L)
        session.pause(300_000L)
        session.refresh(1_800_000L)
        session.resume(2_100_000L)

        val snapshot = session.refresh(2_400_000L)

        assertEquals(1.5, snapshot.accumulatedDoseSed, EPSILON)
        assertTrue(snapshot.isRunning)
    }

    @Test
    fun `pause and resume are idempotent`() {
        session.start(SkinType.TYPE_III, 10.0, 0L)

        assertFalse(session.pause(300_000L).isRunning)
        assertFalse(session.pause(600_000L).isRunning)
        assertTrue(session.resume(1_800_000L).isRunning)
        assertTrue(session.resume(1_800_000L).isRunning)
        assertEquals(1.5, session.refresh(2_100_000L).accumulatedDoseSed, EPSILON)
    }

    @Test
    fun `UV changes settle the previous interval before using the new value`() {
        session.start(SkinType.TYPE_III, 5.0, 0L)
        session.updateUvIndex(10.0, 600_000L)

        val snapshot = session.refresh(1_200_000L)

        assertEquals(2.25, snapshot.accumulatedDoseSed, EPSILON)
        assertEquals(10.0, snapshot.uvIndex, 0.0)
    }

    @Test
    fun `context changes split intervals without resetting accumulated dose`() {
        session.start(
            skinType = SkinType.TYPE_III,
            uvIndex = 10.0,
            nowElapsedMs = 0L,
            context = ExposureContext.DIRECT_SUN,
        )
        session.updateContext(ExposureContext.SHADE, 600_000L)
        session.updateContext(ExposureContext.INDOOR, 1_200_000L)

        val snapshot = session.refresh(1_800_000L)

        assertEquals(2.25, snapshot.accumulatedDoseSed, EPSILON)
        assertEquals(ExposureContext.INDOOR, snapshot.context)
        assertNull(snapshot.estimatedRemainingMinutes)
    }

    @Test
    fun `unknown context uses the conservative full exposure rate`() {
        session.start(SkinType.TYPE_III, 10.0, 0L)

        val snapshot = session.refresh(600_000L)

        assertEquals(1.5, snapshot.accumulatedDoseSed, EPSILON)
        assertEquals(ExposureContext.UNKNOWN, snapshot.context)
    }

    @Test
    fun `context can change while paused without adding dose`() {
        session.start(
            skinType = SkinType.TYPE_III,
            uvIndex = 10.0,
            nowElapsedMs = 0L,
            context = ExposureContext.DIRECT_SUN,
        )
        session.pause(300_000L)

        val snapshot = session.updateContext(ExposureContext.SHADE, 1_800_000L)

        assertEquals(0.75, snapshot.accumulatedDoseSed, EPSILON)
        assertEquals(ExposureContext.SHADE, snapshot.context)
        assertFalse(snapshot.isRunning)
    }

    @Test
    fun `zero UV preserves dose and makes remaining time unavailable`() {
        session.start(SkinType.TYPE_III, 6.0, 0L)
        session.updateUvIndex(0.0, 600_000L)

        val snapshot = session.refresh(1_200_000L)

        assertEquals(0.9, snapshot.accumulatedDoseSed, EPSILON)
        assertNull(snapshot.estimatedRemainingMinutes)
    }

    @Test
    fun `skin type changes preserve accumulated dose and replace the limit`() {
        session.start(SkinType.TYPE_IV, 8.0, 0L)
        val beforeChange = session.refresh(600_000L)

        val afterChange = session.updateSkinType(SkinType.TYPE_II, 600_000L)

        assertEquals(beforeChange.accumulatedDoseSed, afterChange.accumulatedDoseSed, 0.0)
        assertEquals(2.5, afterChange.doseLimitSed, 0.0)
        assertEquals(1.3, afterChange.remainingDoseSed, EPSILON)
    }

    @Test
    fun `skin type can change while paused without adding dose`() {
        session.start(SkinType.TYPE_II, 8.0, 0L)
        session.pause(300_000L)

        val snapshot = session.updateSkinType(SkinType.TYPE_V, 1_800_000L)

        assertEquals(0.6, snapshot.accumulatedDoseSed, EPSILON)
        assertEquals(6.0, snapshot.doseLimitSed, 0.0)
        assertFalse(snapshot.isRunning)
    }

    @Test
    fun `normal burndown scenario exposes stable derived values`() {
        session.start(SkinType.TYPE_III, 6.0, 0L)
        session.updateUvIndex(9.0, 600_000L)

        val snapshot = session.refresh(1_200_000L)

        assertEquals(2.25, snapshot.accumulatedDoseSed, EPSILON)
        assertEquals(0.75, snapshot.remainingDoseSed, EPSILON)
        assertEquals(0.75, snapshot.exposureFraction, EPSILON)
        assertEquals(5.555555555555555, snapshot.estimatedRemainingMinutes!!, EPSILON)
    }

    @Test
    fun `derived remaining values are clamped after the threshold`() {
        session.start(SkinType.TYPE_I, 10.0, 0L)

        val snapshot = session.refresh(1_200_000L)

        assertEquals(3.0, snapshot.accumulatedDoseSed, EPSILON)
        assertEquals(0.0, snapshot.remainingDoseSed, 0.0)
        assertEquals(1.0, snapshot.exposureFraction, 0.0)
        assertEquals(0.0, snapshot.estimatedRemainingMinutes!!, 0.0)
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
