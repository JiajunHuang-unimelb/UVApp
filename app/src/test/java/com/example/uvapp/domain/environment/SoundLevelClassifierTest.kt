package com.example.uvapp.domain.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundLevelClassifierTest {
    @Test
    fun `silence is classified as quiet indoor likely`() {
        val reading = SoundLevelClassifier(smoothingFactor = 1.0).classify(ShortArray(128))

        assertEquals(AcousticContext.QUIET_INDOOR_LIKELY, reading?.context)
        assertTrue(reading!!.decibelsFullScale <= -45.0)
    }

    @Test
    fun `strong waveform is classified as active outdoor likely`() {
        val samples = ShortArray(128) { index -> if (index % 2 == 0) 20_000 else -20_000 }

        val reading = SoundLevelClassifier(smoothingFactor = 1.0).classify(samples)

        assertEquals(AcousticContext.ACTIVE_OUTDOOR_LIKELY, reading?.context)
    }

    @Test
    fun `invalid sample count is ignored`() {
        assertNull(SoundLevelClassifier().classify(ShortArray(4), sampleCount = 0))
    }
}
