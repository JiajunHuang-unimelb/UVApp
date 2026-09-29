package com.example.uvapp.platform.environment

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.uvapp.domain.environment.ExposureMonitoringController

class AndroidExposureMonitoringController(
    context: Context,
) : ExposureMonitoringController {
    private val applicationContext = context.applicationContext

    override fun start() {
        val hasLocationPermission =
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        if (!hasLocationPermission) return

        ContextCompat.startForegroundService(
            applicationContext,
            Intent(applicationContext, ExposureMonitoringService::class.java),
        )
    }

    override fun stop() {
        applicationContext.stopService(
            Intent(applicationContext, ExposureMonitoringService::class.java),
        )
    }
}
