package com.example.uvapp.domain.environment

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorRegistrationPolicyTest {
    @Test
    fun `missing optional hardware is distinguished from listener failure`() {
        assertEquals(
            SensorRegistrationStatus.HARDWARE_MISSING,
            SensorRegistrationPolicy.status(sensorPresent = false, listenerRegistered = false),
        )
    }

    @Test
    fun `present sensor with rejected listener is a registration failure`() {
        assertEquals(
            SensorRegistrationStatus.REGISTRATION_FAILED,
            SensorRegistrationPolicy.status(sensorPresent = true, listenerRegistered = false),
        )
    }

    @Test
    fun `present sensor with accepted listener is registered`() {
        assertEquals(
            SensorRegistrationStatus.REGISTERED,
            SensorRegistrationPolicy.status(sensorPresent = true, listenerRegistered = true),
        )
    }
}
