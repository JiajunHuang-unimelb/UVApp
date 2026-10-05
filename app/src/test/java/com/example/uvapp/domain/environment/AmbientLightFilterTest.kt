package com.example.uvapp.domain.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AmbientLightFilterTest {
    @Test
    fun `isolated bright spike does not replace stable low light`() {
        val filter = AmbientLightFilter(windowSize = 5)

        listOf(500f, 520f, 50_000f, 510f).forEach(filter::update)

        assertEquals(520, filter.update(530f))
    }

    @Test
    fun `sustained light transition replaces old window`() {
        val filter = AmbientLightFilter(windowSize = 5)
        repeat(5) { filter.update(500f) }

        filter.update(30_000f)
        filter.update(31_000f)

        assertEquals(30_000, filter.update(32_000f))
    }

    @Test
    fun `window discards its oldest sample`() {
        val filter = AmbientLightFilter(windowSize = 3)
        filter.update(10f)
        filter.update(20f)
        filter.update(30f)

        assertEquals(30, filter.update(40f))
    }

    @Test
    fun `invalid hardware samples are ignored without changing window`() {
        val filter = AmbientLightFilter(windowSize = 3)
        filter.update(100f)
        filter.update(200f)

        assertNull(filter.update(Float.NaN))
        assertNull(filter.update(Float.POSITIVE_INFINITY))
        assertNull(filter.update(-1f))
        assertEquals(200, filter.update(300f))
    }

    @Test
    fun `window must be positive and odd`() {
        assertThrows(IllegalArgumentException::class.java) { AmbientLightFilter(0) }
        assertThrows(IllegalArgumentException::class.java) { AmbientLightFilter(4) }
    }
}
