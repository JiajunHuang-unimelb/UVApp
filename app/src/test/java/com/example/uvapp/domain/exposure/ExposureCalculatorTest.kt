package com.example.uvapp.domain.exposure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExposureCalculatorTest {
    @Test
    fun `equivalent UVI time products add equivalent dose`() {
        assertEquals(1.5, ExposureCalculator.calculateDoseIncrement(10.0, 10.0), EPSILON)
        assertEquals(1.5, ExposureCalculator.calculateDoseIncrement(5.0, 20.0), EPSILON)
    }

    @Test
    fun `zero or negative inputs do not add dose`() {
        assertEquals(0.0, ExposureCalculator.calculateDoseIncrement(0.0, 10.0), 0.0)
        assertEquals(0.0, ExposureCalculator.calculateDoseIncrement(10.0, 0.0), 0.0)
        assertEquals(0.0, ExposureCalculator.calculateDoseIncrement(-1.0, 10.0), 0.0)
        assertEquals(0.0, ExposureCalculator.calculateDoseIncrement(10.0, -1.0), 0.0)
        assertEquals(0.0, ExposureCalculator.calculateDoseIncrement(10.0, 10.0, -1.0), 0.0)
    }

    @Test
    fun `context factor scales the dose rate`() {
        assertEquals(1.5, ExposureCalculator.calculateDoseIncrement(10.0, 10.0, 1.0), EPSILON)
        assertEquals(0.75, ExposureCalculator.calculateDoseIncrement(10.0, 10.0, 0.5), EPSILON)
        assertEquals(0.0, ExposureCalculator.calculateDoseIncrement(10.0, 10.0, 0.0), 0.0)
    }

    @Test
    fun `remaining dose cannot be negative`() {
        assertEquals(2.0, ExposureCalculator.calculateRemainingDose(3.0, 1.0), EPSILON)
        assertEquals(0.0, ExposureCalculator.calculateRemainingDose(3.0, 4.0), 0.0)
    }

    @Test
    fun `exposure fraction is clamped for presentation`() {
        assertEquals(0.5, ExposureCalculator.calculateExposureFraction(3.0, 1.5), EPSILON)
        assertEquals(1.0, ExposureCalculator.calculateExposureFraction(3.0, 4.0), 0.0)
    }

    @Test
    fun `remaining minutes use the current positive UVI`() {
        assertEquals(
            20.833333333333332,
            ExposureCalculator.calculateRemainingMinutes(2.5, 8.0)!!,
            EPSILON,
        )
    }

    @Test
    fun `shade factor increases estimated remaining minutes`() {
        assertEquals(
            41.666666666666664,
            ExposureCalculator.calculateRemainingMinutes(2.5, 8.0, 0.5)!!,
            EPSILON,
        )
    }

    @Test
    fun `remaining minutes are unavailable without positive UVI`() {
        assertNull(ExposureCalculator.calculateRemainingMinutes(2.5, 0.0))
        assertNull(ExposureCalculator.calculateRemainingMinutes(2.5, -1.0))
        assertNull(ExposureCalculator.calculateRemainingMinutes(2.5, 8.0, 0.0))
    }

    @Test
    fun `remaining minutes are zero after the threshold is reached`() {
        assertEquals(0.0, ExposureCalculator.calculateRemainingMinutes(0.0, 0.0)!!, 0.0)
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
