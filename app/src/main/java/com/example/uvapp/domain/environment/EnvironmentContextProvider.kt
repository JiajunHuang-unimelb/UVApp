package com.example.uvapp.domain.environment

import kotlinx.coroutines.flow.StateFlow

/** Raw environmental signals supplied by hardware and saved-location monitoring. */
data class EnvironmentSample(
    val lux: Int,
    val nearIndoorLocation: Boolean,
    /** `null` means that the device has no physical proximity sensor. */
    val deviceOccluded: Boolean?,
    val posture: DevicePosture? = null,
    val isMoving: Boolean? = null,
    val stepsSinceStart: Int? = null,
    val stepsPerMinute: Int? = null,
)

/** Replaceable boundary between sensor/location implementations and exposure logic. */
interface EnvironmentContextProvider {
    val samples: StateFlow<EnvironmentSample>
}
