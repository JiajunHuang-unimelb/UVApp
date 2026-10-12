package com.example.uvapp.platform.environment

import com.example.uvapp.domain.environment.AcousticContext
import com.example.uvapp.domain.environment.AcousticReading
import com.example.uvapp.domain.environment.DevicePosture
import com.example.uvapp.domain.environment.MotionReading
import com.example.uvapp.domain.environment.StepActivity
import com.example.uvapp.domain.environment.StepActivityReading
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidEnvironmentContextProviderTest {
    @Before
    fun resetBeforeTest() {
        AndroidEnvironmentContextProvider.markUnavailable()
    }

    @After
    fun resetAfterTest() {
        AndroidEnvironmentContextProvider.markUnavailable()
    }

    @Test
    fun `updates from different sensors are merged into one sample`() {
        AndroidEnvironmentContextProvider.updateLux(750)
        AndroidEnvironmentContextProvider.updateIndoorProximity(true)
        AndroidEnvironmentContextProvider.updateDeviceOcclusion(true)
        AndroidEnvironmentContextProvider.updateMotion(
            MotionReading(posture = DevicePosture.FACE_UP, isMoving = false),
        )
        AndroidEnvironmentContextProvider.updateSteps(
            StepActivityReading(
                stepsSinceStart = 12,
                recentSteps = 4,
                averageStepsPerMinute = 36,
                lastStepElapsedMillis = 2_000L,
                activity = StepActivity.WALKING,
            ),
        )
        AndroidEnvironmentContextProvider.updateAcoustic(
            AcousticReading(
                decibelsFullScale = -20.0,
                context = AcousticContext.UNCERTAIN,
            ),
        )

        val sample = AndroidEnvironmentContextProvider.samples.value
        assertEquals(750, sample.lux)
        assertTrue(sample.nearIndoorLocation)
        assertEquals(true, sample.deviceOccluded)
        assertEquals(DevicePosture.FACE_UP, sample.posture)
        assertEquals(false, sample.isMoving)
        assertEquals(12, sample.stepsSinceStart)
        assertEquals(4, sample.recentSteps)
        assertEquals(36, sample.stepsPerMinute)
        assertEquals(2_000L, sample.lastStepElapsedMillis)
        assertEquals(StepActivity.WALKING, sample.stepActivity)
        assertEquals(-20.0, sample.soundLevelDb!!, 0.0)
        assertEquals(AcousticContext.UNCERTAIN, sample.acousticContext)
    }

    @Test
    fun `lux values are clamped to supported range`() {
        AndroidEnvironmentContextProvider.updateLux(-1)
        assertEquals(0, AndroidEnvironmentContextProvider.samples.value.lux)

        AndroidEnvironmentContextProvider.updateLux(Int.MAX_VALUE)
        assertEquals(100_000, AndroidEnvironmentContextProvider.samples.value.lux)
    }

    @Test
    fun `light registration failure replaces stale lux with conservative fallback`() {
        AndroidEnvironmentContextProvider.updateLux(500)

        AndroidEnvironmentContextProvider.markLuxUnavailable()

        assertEquals(100_000, AndroidEnvironmentContextProvider.samples.value.lux)
    }

    @Test
    fun `unavailable state clears stale sensor evidence conservatively`() {
        AndroidEnvironmentContextProvider.updateLux(500)
        AndroidEnvironmentContextProvider.updateIndoorProximity(true)
        AndroidEnvironmentContextProvider.updateDeviceOcclusion(false)
        AndroidEnvironmentContextProvider.updateMotion(
            MotionReading(posture = DevicePosture.UPRIGHT, isMoving = true),
        )
        AndroidEnvironmentContextProvider.updateSteps(
            StepActivityReading(5, 3, 20, 1_000L, StepActivity.WALKING),
        )
        AndroidEnvironmentContextProvider.updateAcoustic(
            AcousticReading(-10.0, AcousticContext.ACTIVE_OUTDOOR_LIKELY),
        )

        AndroidEnvironmentContextProvider.markUnavailable()

        val sample = AndroidEnvironmentContextProvider.samples.value
        assertEquals(100_000, sample.lux)
        assertFalse(sample.nearIndoorLocation)
        assertNull(sample.deviceOccluded)
        assertNull(sample.posture)
        assertNull(sample.isMoving)
        assertNull(sample.stepsSinceStart)
        assertNull(sample.recentSteps)
        assertNull(sample.stepsPerMinute)
        assertNull(sample.lastStepElapsedMillis)
        assertNull(sample.stepActivity)
        assertNull(sample.soundLevelDb)
        assertNull(sample.acousticContext)
    }
}
