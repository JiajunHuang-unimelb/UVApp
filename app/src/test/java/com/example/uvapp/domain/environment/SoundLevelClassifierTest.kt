package com.example.uvapp.domain.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
    fun `conversational waveform remains uncertain and is not treated as loud activity`() {
        val samples = ShortArray(128) { index -> if (index % 2 == 0) 5_000 else -5_000 }

        val reading = SoundLevelClassifier(smoothingFactor = 1.0).classify(samples)

        assertEquals(AcousticContext.UNCERTAIN, reading?.context)
    }

    @Test
    fun `strong waveform must remain loud for five seconds`() {
        var now = 1_000L
        val samples = ShortArray(128) { index -> if (index % 2 == 0) 20_000 else -20_000 }
        val classifier = SoundLevelClassifier(smoothingFactor = 1.0, elapsedRealtimeMillis = { now })

        assertEquals(AcousticContext.UNCERTAIN, classifier.classify(samples)?.context)
        now += 4_999L
        assertEquals(AcousticContext.UNCERTAIN, classifier.classify(samples)?.context)
        now += 1L

        assertEquals(AcousticContext.ACTIVE_OUTDOOR_LIKELY, classifier.classify(samples)?.context)
    }

    @Test
    fun `non loud reading resets active confirmation`() {
        var now = 1_000L
        val loud = ShortArray(128) { index -> if (index % 2 == 0) 20_000 else -20_000 }
        val conversational = ShortArray(128) { index -> if (index % 2 == 0) 5_000 else -5_000 }
        val classifier = SoundLevelClassifier(smoothingFactor = 1.0, elapsedRealtimeMillis = { now })

        classifier.classify(loud)
        now += 4_000L
        classifier.classify(conversational)
        now += 2_000L

        assertEquals(AcousticContext.UNCERTAIN, classifier.classify(loud)?.context)
    }

    @Test
    fun `clock moving backwards restarts loud confirmation window`() {
        var now = 10_000L
        val loud = ShortArray(128) { index -> if (index % 2 == 0) 20_000 else -20_000 }
        val classifier = SoundLevelClassifier(smoothingFactor = 1.0, elapsedRealtimeMillis = { now })

        classifier.classify(loud)
        now = 9_000L
        assertEquals(AcousticContext.UNCERTAIN, classifier.classify(loud)?.context)
        now = 13_999L
        assertEquals(AcousticContext.UNCERTAIN, classifier.classify(loud)?.context)
        now = 14_000L

        assertEquals(AcousticContext.ACTIVE_OUTDOOR_LIKELY, classifier.classify(loud)?.context)
    }

    @Test
    fun `invalid sample count is ignored`() {
        assertNull(SoundLevelClassifier().classify(ShortArray(4), sampleCount = 0))
        assertNull(SoundLevelClassifier().classify(ShortArray(4), sampleCount = 5))
    }

    @Test
    fun `invalid smoothing factor is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SoundLevelClassifier(smoothingFactor = 1.1)
        }
    }
}
