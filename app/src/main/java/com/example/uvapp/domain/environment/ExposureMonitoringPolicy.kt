package com.example.uvapp.domain.environment

/** Independent capabilities used when starting background environment monitoring. */
data class ExposureMonitoringPlan(
    val startLocalSensors: Boolean,
    val startLocationMonitoring: Boolean,
)

object ExposureMonitoringPolicy {
    /** Location permission controls GPS only; local hardware sensors remain available. */
    fun plan(hasLocationPermission: Boolean) =
        ExposureMonitoringPlan(
            startLocalSensors = true,
            startLocationMonitoring = hasLocationPermission,
        )
}
