package com.example.uvapp.platform.environment

import com.example.uvapp.domain.environment.ExposureMonitoringPolicy
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
}
