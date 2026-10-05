package com.example.uvapp

import com.example.uvapp.domain.advisor.BurnCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class BurnCalculatorTest {

    @Test
    fun `format remaining always shows seconds`() {
        assertEquals("2:32:00", BurnCalculator.formatRemaining(152 * 60L))
        assertEquals("12:00", BurnCalculator.formatRemaining(12 * 60L))
        assertEquals("2:31:59", BurnCalculator.formatRemaining(152 * 60L - 1))
        assertEquals("00:00", BurnCalculator.formatRemaining(0L))
    }
}
