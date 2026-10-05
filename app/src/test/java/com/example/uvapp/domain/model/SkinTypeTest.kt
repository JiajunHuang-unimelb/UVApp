package com.example.uvapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkinTypeTest {
    @Test
    fun `skin types have the expected minimum erythemal doses`() {
        assertEquals(2.0, SkinType.I.minimumErythemaDoseSed, 0.0)
        assertEquals(2.5, SkinType.II.minimumErythemaDoseSed, 0.0)
        assertEquals(3.0, SkinType.III.minimumErythemaDoseSed, 0.0)
        assertEquals(4.5, SkinType.IV.minimumErythemaDoseSed, 0.0)
        assertEquals(6.0, SkinType.V.minimumErythemaDoseSed, 0.0)
        assertEquals(10.0, SkinType.VI.minimumErythemaDoseSed, 0.0)
    }

    @Test
    fun `every skin type has a positive minimum erythemal dose`() {
        assertTrue(SkinType.entries.all { it.minimumErythemaDoseSed > 0.0 })
    }
}
