package com.example.uvapp.domain.environment

/** Starts and stops environmental monitoring for an exposure session. */
interface ExposureMonitoringController {
    fun start()

    fun stop()
}
