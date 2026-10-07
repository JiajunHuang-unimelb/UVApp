package com.example.uvapp.platform.environment

/**
 * Runs every resource-release action owned by the monitoring service.
 * One platform cleanup failure must not prevent the remaining resources from being released.
 */
internal class ExposureMonitoringCleanup(
    private val unregisterSensors: () -> Unit,
    private val removeLocationUpdates: () -> Unit,
    private val cancelFreshLocation: () -> Unit,
    private val cancelJobs: () -> Unit,
    private val releaseWakeLock: () -> Unit,
    private val clearPublishedContext: () -> Unit,
) {
    fun releaseAll() {
        listOf(
            unregisterSensors,
            removeLocationUpdates,
            cancelFreshLocation,
            cancelJobs,
            releaseWakeLock,
            clearPublishedContext,
        ).forEach { release -> runCatching(release) }
    }
}
