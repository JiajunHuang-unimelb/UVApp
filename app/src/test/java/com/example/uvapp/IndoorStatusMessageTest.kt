package com.example.uvapp

import com.example.uvapp.domain.exposure.ExposurePauseReason
import com.example.uvapp.ui.components.indoorStatusMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IndoorStatusMessageTest {
    @Test
    fun `automatic indoor pause says the timer was paused automatically`() {
        assertEquals(
            "Indoors detected - exposure timer paused automatically",
            indoorStatusMessage(ExposurePauseReason.INDOOR_DETECTED, true),
        )
    }

    @Test
    fun `indoors with the timer not paused by indoor detection only says indoors detected`() {
        assertEquals("Indoors detected", indoorStatusMessage(null, true))
    }

    @Test
    fun `manual pause made indoors is not described as automatic`() {
        assertEquals("Indoors detected", indoorStatusMessage(ExposurePauseReason.MANUAL, true))
    }

    @Test
    fun `no message when outdoors`() {
        assertNull(indoorStatusMessage(null, false))
        assertNull(indoorStatusMessage(ExposurePauseReason.MANUAL, false))
    }
}
