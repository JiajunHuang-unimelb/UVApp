package com.example.uvapp.domain.environment

enum class SensorRegistrationStatus {
    REGISTERED,
    HARDWARE_MISSING,
    REGISTRATION_FAILED,
}

/** Distinguishes missing optional hardware from a listener registration failure. */
object SensorRegistrationPolicy {
    fun status(
        sensorPresent: Boolean,
        listenerRegistered: Boolean,
    ): SensorRegistrationStatus =
        when {
            !sensorPresent -> SensorRegistrationStatus.HARDWARE_MISSING
            listenerRegistered -> SensorRegistrationStatus.REGISTERED
            else -> SensorRegistrationStatus.REGISTRATION_FAILED
        }
}
