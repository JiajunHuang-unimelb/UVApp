package com.example.uvapp.domain.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionClassifierTest {
    @Test
    fun `stable positive z is face up and stationary`() {
        val classifier = MotionClassifier(windowSize = 5)

        val reading = (1..5).map { classifier.update(0f, 0f, 9.81f) }.last()

        assertEquals(DevicePosture.FACE_UP, reading?.posture)
        assertFalse(reading!!.isMoving)
    }

    @Test
    fun `varying acceleration is classified as moving`() {
        val classifier = MotionClassifier(windowSize = 5)
        val samples = listOf(9.8f, 4f, 15f, 6f, 13f)

        val reading = samples.map { z -> classifier.update(0f, 0f, z) }.last()

        assertTrue(reading!!.isMoving)
    }

    @Test
    fun `horizontal z component is upright`() {
        val reading = MotionClassifier(windowSize = 5).update(9.81f, 0f, 0f)

        assertEquals(DevicePosture.UPRIGHT, reading?.posture)
    }

    @Test
    fun `stable negative z is face down`() {
        val classifier = MotionClassifier(windowSize = 5)

        val reading = (1..5).map { classifier.update(0f, 0f, -9.81f) }.last()

        assertEquals(DevicePosture.FACE_DOWN, reading?.posture)
        assertFalse(reading!!.isMoving)
    }

    @Test
    fun `diagonal gravity vector is tilted`() {
        val reading = MotionClassifier(windowSize = 5).update(7f, 0f, 7f)

        assertEquals(DevicePosture.TILTED, reading?.posture)
    }

    @Test
    fun `rolling window returns to stationary after movement settles`() {
        val classifier = MotionClassifier(windowSize = 5)
        listOf(4f, 15f, 6f, 13f, 4f).forEach { z -> classifier.update(0f, 0f, z) }
        assertTrue(classifier.update(0f, 0f, 15f)!!.isMoving)

        val settled = (1..5).map { classifier.update(0f, 0f, 9.81f) }.last()

        assertFalse(settled!!.isMoving)
    }

    @Test
    fun `invalid sample is ignored`() {
        assertNull(MotionClassifier().update(Float.NaN, 0f, 9.81f))
        assertNull(MotionClassifier().update(0f, Float.POSITIVE_INFINITY, 9.81f))
    }
}
