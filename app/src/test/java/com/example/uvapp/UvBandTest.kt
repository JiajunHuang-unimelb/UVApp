package com.example.uvapp

import com.example.uvapp.domain.model.UvBand
import org.junit.Assert.assertEquals
import org.junit.Test

class UvBandTest {

    @Test
    fun `band boundaries follow WHO thresholds`() {
        assertEquals(UvBand.LOW, UvBand.fromIndex(0.0))
        assertEquals(UvBand.LOW, UvBand.fromIndex(2.9))
        assertEquals(UvBand.MODERATE, UvBand.fromIndex(3.0))
        assertEquals(UvBand.MODERATE, UvBand.fromIndex(5.9))
        assertEquals(UvBand.HIGH, UvBand.fromIndex(6.0))
        assertEquals(UvBand.HIGH, UvBand.fromIndex(7.9))
        assertEquals(UvBand.VERY_HIGH, UvBand.fromIndex(8.0))
        assertEquals(UvBand.VERY_HIGH, UvBand.fromIndex(10.9))
        assertEquals(UvBand.EXTREME, UvBand.fromIndex(11.0))
        assertEquals(UvBand.EXTREME, UvBand.fromIndex(13.7))
    }

    @Test
    fun `labels match the app vocabulary`() {
        assertEquals("Low", UvBand.LOW.label)
        assertEquals("Moderate", UvBand.MODERATE.label)
        assertEquals("High", UvBand.HIGH.label)
        assertEquals("Very High", UvBand.VERY_HIGH.label)
        assertEquals("Extreme", UvBand.EXTREME.label)
    }
}
