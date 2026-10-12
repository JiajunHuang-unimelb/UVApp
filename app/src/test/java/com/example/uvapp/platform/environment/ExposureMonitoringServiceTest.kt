package com.example.uvapp.platform.environment

import com.example.uvapp.domain.environment.ExposureMonitoringPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests the platform-neutral decisions applied by [ExposureMonitoringService]. */
class ExposureMonitoringServiceTest {
    @Test
    fun `denied GPS permission keeps local sensors enabled`() {
        val plan = ExposureMonitoringPolicy.plan(hasLocationPermission = false)

        assertTrue(plan.startLocalSensors)
        assertFalse(plan.startLocationMonitoring)
    }

    @Test
    fun `runtime activity grant registers a previously unavailable step counter`() {
        assertFalse(
            ExposureMonitoringPolicy.shouldRegisterStepCounter(
                hasActivityRecognitionPermission = false,
                isAlreadyRegistered = false,
            ),
        )
        assertTrue(
            ExposureMonitoringPolicy.shouldRegisterStepCounter(
                hasActivityRecognitionPermission = true,
                isAlreadyRegistered = false,
            ),
        )
        assertFalse(
            ExposureMonitoringPolicy.shouldRegisterStepCounter(
                hasActivityRecognitionPermission = true,
                isAlreadyRegistered = true,
            ),
        )
    }

    @Test
    fun `revoked activity permission unregisters and clears step counter`() {
        var unregisterCalled = false
        var clearCalled = false
        val coordinator =
            StepCounterRegistrationCoordinator(
                register = { error("registration must not run after permission revocation") },
                unregister = { unregisterCalled = true },
                clearReading = { clearCalled = true },
            )

        val stillRegistered =
            coordinator.refresh(
                hasPermission = false,
                isRegistered = true,
            )

        assertFalse(stillRegistered)
        assertTrue(unregisterCalled)
        assertTrue(clearCalled)
    }

    @Test
    fun `service cleanup releases sensors location jobs and wake lock`() {
        val released = mutableListOf<String>()
        val cleanup =
            ExposureMonitoringCleanup(
                unregisterSensors = { released += "sensors" },
                removeLocationUpdates = { released += "location" },
                cancelFreshLocation = { released += "fresh-location" },
                cancelJobs = { released += "jobs" },
                releaseWakeLock = { released += "wake-lock" },
                clearPublishedContext = { released += "context" },
            )

        cleanup.releaseAll()

        assertEquals(
            listOf("sensors", "location", "fresh-location", "jobs", "wake-lock", "context"),
            released,
        )
    }
}
