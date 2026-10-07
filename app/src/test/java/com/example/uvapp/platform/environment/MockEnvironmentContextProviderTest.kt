package com.example.uvapp.platform.environment

import com.example.uvapp.domain.environment.AcousticContext
import com.example.uvapp.domain.environment.DevicePosture
import com.example.uvapp.domain.environment.StepActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockEnvironmentContextProviderTest {
    @Test
    fun `constructor publishes requested initial sensor state`() {
        val provider =
            MockEnvironmentContextProvider(
                initialLux = 750,
                initiallyNearIndoorLocation = true,
                initiallyDeviceOccluded = null,
            )

        val sample = provider.samples.value
        assertEquals(750, sample.lux)
        assertTrue(sample.nearIndoorLocation)
        assertNull(sample.deviceOccluded)
    }

    @Test
    fun `individual setters preserve readings from other sensors`() {
        val provider = MockEnvironmentContextProvider()

        provider.setLux(900)
        provider.setNearIndoorLocation(true)
        provider.setDeviceOccluded(true)
        provider.setMotion(DevicePosture.TILTED, isMoving = false)
        provider.setStepActivity(
            stepsSinceStart = 8,
            stepsPerMinute = 24,
            recentSteps = 3,
            lastStepElapsedMillis = 5_000L,
            activity = StepActivity.WALKING,
        )
        provider.setAcoustic(-32.0, AcousticContext.UNCERTAIN)

        val sample = provider.samples.value
        assertEquals(900, sample.lux)
        assertTrue(sample.nearIndoorLocation)
        assertEquals(true, sample.deviceOccluded)
        assertEquals(DevicePosture.TILTED, sample.posture)
        assertEquals(false, sample.isMoving)
        assertEquals(8, sample.stepsSinceStart)
        assertEquals(24, sample.stepsPerMinute)
        assertEquals(3, sample.recentSteps)
        assertEquals(5_000L, sample.lastStepElapsedMillis)
        assertEquals(StepActivity.WALKING, sample.stepActivity)
        assertEquals(-32.0, sample.soundLevelDb!!, 0.0)
        assertEquals(AcousticContext.UNCERTAIN, sample.acousticContext)
    }

    @Test
    fun `mock lux is clamped like production sensor input`() {
        val provider = MockEnvironmentContextProvider()

        provider.setLux(-10)
        assertEquals(0, provider.samples.value.lux)

        provider.setLux(Int.MAX_VALUE)
        assertEquals(100_000, provider.samples.value.lux)
    }

    @Test
    fun `optional sensor setters can publish unavailable values`() {
        val provider = MockEnvironmentContextProvider(initiallyDeviceOccluded = true)
        provider.setMotion(DevicePosture.FACE_UP, isMoving = true)
        provider.setAcoustic(-10.0, AcousticContext.ACTIVE_OUTDOOR_LIKELY)

        provider.setDeviceOccluded(null)
        provider.setMotion(posture = null, isMoving = null)
        provider.setAcoustic(soundLevelDb = null, context = null)

        val sample = provider.samples.value
        assertNull(sample.deviceOccluded)
        assertNull(sample.posture)
        assertNull(sample.isMoving)
        assertNull(sample.soundLevelDb)
        assertNull(sample.acousticContext)
    }
}
