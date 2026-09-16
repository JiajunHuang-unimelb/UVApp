package com.example.uvapp.domain.exposure

import com.example.uvapp.domain.model.SkinType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExposureCalculatorTest {
    @Test
    fun `personal dose limits use forty percent of MED with a cap`() {
        assertEquals(0.8, ExposureCalculator.calculatePersonalDoseLimit(SkinType.I), EPSILON)
        assertEquals(1.0, ExposureCalculator.calculatePersonalDoseLimit(SkinType.II), EPSILON)
        assertEquals(1.2, ExposureCalculator.calculatePersonalDoseLimit(SkinType.III), EPSILON)
        assertEquals(1.8, ExposureCalculator.calculatePersonalDoseLimit(SkinType.IV), EPSILON)
        assertEquals(2.4, ExposureCalculator.calculatePersonalDoseLimit(SkinType.V), EPSILON)
        assertEquals(2.4, ExposureCalculator.calculatePersonalDoseLimit(SkinType.VI), EPSILON)
    }

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
    fun `context factor scales dose and remaining time`() {
        assertEquals(1.5, ExposureCalculator.calculateDoseIncrement(10.0, 10.0), EPSILON)
        assertEquals(0.45, ExposureCalculator.calculateDoseIncrement(10.0, 10.0, 0.3), EPSILON)
        assertEquals(
            22.22222222222222,
            ExposureCalculator.calculateRemainingMinutes(1.0, 10.0, 0.3)!!,
            EPSILON,
        )
    }

    @Test
    fun `remaining dose and presentation fraction are clamped`() {
        assertEquals(2.0, ExposureCalculator.calculateRemainingDose(3.0, 1.0), EPSILON)
        assertEquals(0.0, ExposureCalculator.calculateRemainingDose(3.0, 4.0), 0.0)
        assertEquals(0.5, ExposureCalculator.calculateExposureFraction(3.0, 1.5), EPSILON)
        assertEquals(1.0, ExposureCalculator.calculateExposureFraction(3.0, 4.0), 0.0)
    }

    @Test
    fun `remaining time uses current dose rate without SPF scaling`() {
        assertEquals(
            8.333333333333334,
            ExposureCalculator.calculateRemainingMinutes(1.0, 8.0)!!,
            EPSILON,
        )
        assertEquals(500L, ExposureCalculator.calculateRemainingSeconds(1.0, 8.0))
    }

    @Test
    fun `remaining time is unavailable without a positive dose rate`() {
        assertNull(ExposureCalculator.calculateRemainingMinutes(1.0, 0.0))
        assertNull(ExposureCalculator.calculateRemainingMinutes(1.0, -1.0))
        assertNull(ExposureCalculator.calculateRemainingMinutes(1.0, 8.0, 0.0))
    }

    @Test
    fun `remaining time is zero after the threshold is reached`() {
        assertEquals(0.0, ExposureCalculator.calculateRemainingMinutes(0.0, 0.0)!!, 0.0)
    }

    @Test
    fun `remaining seconds round upward without losing a partial second`() {
        assertEquals(1L, ExposureCalculator.calculateRemainingSeconds(0.000001, 8.0))
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
