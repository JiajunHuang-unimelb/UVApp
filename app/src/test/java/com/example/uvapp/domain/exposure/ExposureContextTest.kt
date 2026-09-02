package com.example.uvapp.domain.exposure

import org.junit.Assert.assertEquals
import org.junit.Test

class ExposureContextTest {
    @Test
    fun `contexts have the expected dose rate factors`() {
        assertEquals(1.0, ExposureContext.DIRECT_SUN.doseRateFactor, 0.0)
        assertEquals(0.5, ExposureContext.SHADE.doseRateFactor, 0.0)
        assertEquals(0.0, ExposureContext.INDOOR.doseRateFactor, 0.0)
        assertEquals(1.0, ExposureContext.UNKNOWN.doseRateFactor, 0.0)
    }
}
