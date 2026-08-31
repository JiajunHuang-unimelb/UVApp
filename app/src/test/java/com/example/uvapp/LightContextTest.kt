package com.example.uvapp

import com.example.uvapp.domain.model.LightContext
import org.junit.Assert.assertEquals
import org.junit.Test

class LightContextTest {

    @Test
    fun `lux ranges map to the expected context`() {
        assertEquals(LightContext.INDOOR, LightContext.fromLux(0))
        assertEquals(LightContext.INDOOR, LightContext.fromLux(500))
        assertEquals(LightContext.INDOOR, LightContext.fromLux(999))
        assertEquals(LightContext.SHADE, LightContext.fromLux(1_000))
        assertEquals(LightContext.SHADE, LightContext.fromLux(8_000))
        assertEquals(LightContext.SHADE, LightContext.fromLux(19_999))
        assertEquals(LightContext.DIRECT_SUN, LightContext.fromLux(20_000))
        assertEquals(LightContext.DIRECT_SUN, LightContext.fromLux(38_200))
        assertEquals(LightContext.DIRECT_SUN, LightContext.fromLux(120_000))
    }
}
