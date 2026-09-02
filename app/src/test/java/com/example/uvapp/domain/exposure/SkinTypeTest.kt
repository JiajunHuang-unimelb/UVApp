package com.example.uvapp.domain.exposure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinTypeTest {
    @Test
    fun `skin types have the expected exposure limits`() {
        assertEquals(2.0, SkinType.TYPE_I.exposureLimitSed, 0.0)
        assertEquals(2.5, SkinType.TYPE_II.exposureLimitSed, 0.0)
        assertEquals(3.0, SkinType.TYPE_III.exposureLimitSed, 0.0)
        assertEquals(4.5, SkinType.TYPE_IV.exposureLimitSed, 0.0)
        assertEquals(6.0, SkinType.TYPE_V.exposureLimitSed, 0.0)
        assertEquals(6.0, SkinType.TYPE_VI.exposureLimitSed, 0.0)
    }

    @Test
    fun `every skin type has a positive exposure limit`() {
        assertTrue(SkinType.entries.all { it.exposureLimitSed > 0.0 })
    }
}
