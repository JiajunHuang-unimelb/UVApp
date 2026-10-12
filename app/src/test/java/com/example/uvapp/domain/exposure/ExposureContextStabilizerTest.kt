package com.example.uvapp.domain.exposure

import org.junit.Assert.assertEquals
import org.junit.Test

class ExposureContextStabilizerTest {
    private val stabilizer = ExposureContextStabilizer(riseDelayMillis = 3_000L, fallDelayMillis = 10_000L)

    @Test
    fun `first value is applied immediately`() {
        assertEquals(ExposureContext.SHADE, stabilizer.update(ExposureContext.SHADE, 0L))
    }

    @Test
    fun `lower dose rate waits for the fall delay`() {
        stabilizer.update(ExposureContext.UNKNOWN, 0L)

        assertEquals(ExposureContext.UNKNOWN, stabilizer.update(ExposureContext.SHADE, 1_000L))
        assertEquals(ExposureContext.UNKNOWN, stabilizer.update(ExposureContext.SHADE, 10_999L))
        assertEquals(ExposureContext.SHADE, stabilizer.update(ExposureContext.SHADE, 11_000L))
    }

    @Test
    fun `higher dose rate waits for the shorter rise delay`() {
        stabilizer.update(ExposureContext.SHADE, 0L)

        assertEquals(ExposureContext.SHADE, stabilizer.update(ExposureContext.UNKNOWN, 1_000L))
        assertEquals(ExposureContext.SHADE, stabilizer.update(ExposureContext.UNKNOWN, 3_999L))
        assertEquals(ExposureContext.UNKNOWN, stabilizer.update(ExposureContext.UNKNOWN, 4_000L))
    }

    @Test
    fun `brief flicker back to the stable value cancels the change`() {
        stabilizer.update(ExposureContext.SHADE, 0L)
        stabilizer.update(ExposureContext.UNKNOWN, 1_000L)
        stabilizer.update(ExposureContext.SHADE, 2_000L)

        // The pending UNKNOWN restarted its timer, so 3 s after the first blip is not enough.
        assertEquals(ExposureContext.SHADE, stabilizer.update(ExposureContext.UNKNOWN, 4_500L))
        assertEquals(ExposureContext.UNKNOWN, stabilizer.update(ExposureContext.UNKNOWN, 7_500L))
    }

    @Test
    fun `constant tilting never flips the context back to shade`() {
        stabilizer.update(ExposureContext.SHADE, 0L)
        stabilizer.update(ExposureContext.UNKNOWN, 100L)
        assertEquals(ExposureContext.UNKNOWN, stabilizer.update(ExposureContext.UNKNOWN, 3_100L))

        // Evidence alternates every second; the shade side never holds for the full 10 s.
        var now = 3_200L
        repeat(30) { step ->
            val raw = if (step % 2 == 0) ExposureContext.SHADE else ExposureContext.UNKNOWN
            assertEquals(ExposureContext.UNKNOWN, stabilizer.update(raw, now))
            now += 1_000L
        }
    }

    @Test
    fun `indoor transitions are applied immediately`() {
        stabilizer.update(ExposureContext.SHADE, 0L)
        assertEquals(ExposureContext.INDOOR, stabilizer.update(ExposureContext.INDOOR, 1L))
        assertEquals(ExposureContext.DIRECT_SUN, stabilizer.update(ExposureContext.DIRECT_SUN, 2L))
    }

    @Test
    fun `reset applies the next value immediately`() {
        stabilizer.update(ExposureContext.UNKNOWN, 0L)
        stabilizer.reset()

        assertEquals(ExposureContext.SHADE, stabilizer.update(ExposureContext.SHADE, 1L))
    }
}
