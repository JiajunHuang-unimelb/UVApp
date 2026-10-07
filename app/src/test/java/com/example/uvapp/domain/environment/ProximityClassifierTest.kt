package com.example.uvapp.domain.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProximityClassifierTest {
    @Test
    fun `reading below maximum range is occluded`() {
        assertEquals(true, ProximityClassifier.isOccluded(distance = 0f, maximumRange = 5f))
    }

    @Test
    fun `reading at maximum range is clear`() {
        assertEquals(false, ProximityClassifier.isOccluded(distance = 5f, maximumRange = 5f))
    }

    @Test
    fun `reading above maximum range is treated as clear`() {
        assertEquals(false, ProximityClassifier.isOccluded(distance = 6f, maximumRange = 5f))
    }

    @Test
    fun `invalid readings are unavailable`() {
        assertNull(ProximityClassifier.isOccluded(distance = null, maximumRange = 5f))
        assertNull(ProximityClassifier.isOccluded(distance = Float.NaN, maximumRange = 5f))
        assertNull(ProximityClassifier.isOccluded(distance = Float.POSITIVE_INFINITY, maximumRange = 5f))
        assertNull(ProximityClassifier.isOccluded(distance = -1f, maximumRange = 5f))
        assertNull(ProximityClassifier.isOccluded(distance = 0f, maximumRange = 0f))
        assertNull(ProximityClassifier.isOccluded(distance = 0f, maximumRange = Float.NaN))
    }
}
