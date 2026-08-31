package com.example.uvapp

import com.example.uvapp.domain.advisor.BurnCalculator
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.SkinType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BurnCalculatorTest {

    @Test
    fun `mockup value - Type II SPF 15 UV 8_4 direct sun burns in 2_58`() {
        val minutes = BurnCalculator.burnMinutes(SkinType.II, 15, 8.4, LightContext.DIRECT_SUN)
        // 100 x 15 / 8.4 = 178.57 -> 178 min = 2:58 (as in the high-fi mockup)
        assertEquals(178, minutes)
    }

    @Test
    fun `higher SPF extends burn time`() {
        val spf15 = BurnCalculator.burnMinutes(SkinType.III, 15, 6.0, LightContext.DIRECT_SUN)
        val spf50 = BurnCalculator.burnMinutes(SkinType.III, 50, 6.0, LightContext.DIRECT_SUN)
        assertTrue(spf50 > spf15)
    }

    @Test
    fun `indoor context pauses the timer (infinite burn time)`() {
        val minutes = BurnCalculator.burnMinutes(SkinType.I, 15, 8.0, LightContext.INDOOR)
        assertEquals(Int.MAX_VALUE, minutes)
    }

    @Test
    fun `zero UV means no burn risk`() {
        val minutes = BurnCalculator.burnMinutes(SkinType.II, 15, 0.0, LightContext.DIRECT_SUN)
        assertEquals(Int.MAX_VALUE, minutes)
    }

    @Test
    fun `format remaining shows H_MM above an hour and n min below`() {
        assertEquals("2:32", BurnCalculator.formatRemaining(152 * 60L))
        assertEquals("12 min", BurnCalculator.formatRemaining(12 * 60L))
        assertEquals("0 min", BurnCalculator.formatRemaining(0L))
    }
}
