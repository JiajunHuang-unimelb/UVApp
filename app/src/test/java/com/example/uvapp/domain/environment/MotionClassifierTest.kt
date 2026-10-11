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

    @Test
    fun `single knock on a still phone is not moving`() {
        val classifier = MotionClassifier()
        repeat(20) { classifier.update(0f, 0f, 9.81f) }

        val readings =
            listOf(12.5f, 8.2f).map { z -> classifier.update(0f, 0f, z)!! } +
                (1..20).map { classifier.update(0f, 0f, 9.81f)!! }

        assertTrue(readings.none { it.isMoving })
    }

    @Test
    fun `sustained shaking is moving and settles quickly afterwards`() {
        val classifier = MotionClassifier()
        repeat(20) { classifier.update(0f, 0f, 9.81f) }

        val shaking = (0 until 10).map { step -> classifier.update(0f, 0f, if (step % 2 == 0) 12f else 7.5f)!! }
        assertTrue(shaking.last().isMoving)

        // Eight still samples (about 1.3 s at the service's sampling rate) push the shaking out.
        val settled = (1..8).map { classifier.update(0f, 0f, 9.81f)!! }.last()
        assertFalse(settled.isMoving)
    }

    @Test
    fun `hand tremor below the deviation threshold stays still`() {
        val classifier = MotionClassifier()

        val readings = (0 until 40).map { step -> classifier.update(0f, 0f, if (step % 2 == 0) 9.9f else 9.7f)!! }

        assertTrue(readings.none { it.isMoving })
    }
}
