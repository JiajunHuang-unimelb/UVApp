package com.example.uvapp.domain.pocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PocketDetectorTest {
    @Test
    fun `stable covered and dark samples enter the pocket state`() {
        val detector = PocketDetector()

        val candidate = detector.update(sample(near = true, lux = 2f, timeMs = 1_000L))
        val confirmed = detector.update(sample(near = true, lux = 2f, timeMs = 2_500L))

        assertEquals(PocketState.UNKNOWN, candidate.state)
        assertEquals(PocketState.IN_POCKET, confirmed.state)
        assertTrue(confirmed.isAvailable)
    }

    @Test
    fun `brief or ambiguous readings do not change the stable state`() {
        val detector = PocketDetector()
        detector.update(sample(near = false, lux = 1_000f, timeMs = 0L))
        detector.update(sample(near = false, lux = 1_000f, timeMs = 750L))

        detector.update(sample(near = true, lux = 2f, timeMs = 1_000L))
        val ambiguous = detector.update(sample(near = true, lux = 30f, timeMs = 2_000L))
        val later = detector.update(sample(near = true, lux = 2f, timeMs = 2_500L))

        assertEquals(PocketState.OUT_OF_POCKET, ambiguous.state)
        assertEquals(PocketState.OUT_OF_POCKET, later.state)
    }

    @Test
    fun `stable uncovered samples leave the pocket state`() {
        val detector = PocketDetector()
        detector.update(sample(near = true, lux = 0f, timeMs = 0L))
        detector.update(sample(near = true, lux = 0f, timeMs = 1_500L))

        val candidate = detector.update(sample(near = false, lux = 0f, timeMs = 10_000L))
        val confirmed = detector.update(sample(near = false, lux = 0f, timeMs = 10_750L))

        assertEquals(PocketState.IN_POCKET, candidate.state)
        assertEquals(PocketState.OUT_OF_POCKET, confirmed.state)
    }

    @Test
    fun `missing sensor values report unavailable without changing state`() {
        val detector = PocketDetector()
        detector.update(sample(near = true, lux = 0f, timeMs = 0L))
        detector.update(sample(near = true, lux = 0f, timeMs = 1_500L))

        val detection =
            detector.update(
                PocketSensorSample(
                    proximityNear = null,
                    ambientLux = 0f,
                    elapsedRealtimeMs = 2_000L,
                ),
            )

        assertEquals(PocketState.IN_POCKET, detection.state)
        assertFalse(detection.isAvailable)
    }

    private fun sample(
        near: Boolean,
        lux: Float,
        timeMs: Long,
    ) = PocketSensorSample(
        proximityNear = near,
        ambientLux = lux,
        elapsedRealtimeMs = timeMs,
    )
}
