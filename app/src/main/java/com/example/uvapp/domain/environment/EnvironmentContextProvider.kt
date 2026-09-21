package com.example.uvapp.domain.environment

import kotlinx.coroutines.flow.StateFlow

/** Raw environmental signals. GPS/geofencing can later supply [nearIndoorLocation]. */
data class EnvironmentSample(
    val lux: Int,
    val nearIndoorLocation: Boolean,
)

/** Replaceable boundary between sensor/location implementations and exposure logic. */
interface EnvironmentContextProvider {
    val samples: StateFlow<EnvironmentSample>
}
