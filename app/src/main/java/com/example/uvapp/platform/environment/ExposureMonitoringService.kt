package com.example.uvapp.platform.environment

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.uvapp.MainActivity
import com.example.uvapp.R
import com.example.uvapp.data.preferences.DataStoreIndoorLocationRepository
import com.example.uvapp.domain.model.IndoorLocation
import com.example.uvapp.domain.model.contains
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Keeps ambient-light and saved-location proximity monitoring alive in the background. */
class ExposureMonitoringService : Service(), SensorEventListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val indoorRepository by lazy { DataStoreIndoorLocationRepository(this) }
    private val sensorManager by lazy { getSystemService(SensorManager::class.java) }
    private val lightSensor by lazy { sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT) }
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:exposure-monitoring",
        ).apply { setReferenceCounted(false) }
    }

    private var savedLocations: List<IndoorLocation> = emptyList()
    private var latestLocation: Location? = null
    private var preciseLocationAvailable = false
    private var repositoryJob: Job? = null
    private var freshnessJob: Job? = null

    private val locationCallback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                latestLocation = result.lastLocation
                publishIndoorProximity()
            }
        }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
        acquireWakeLock()
        AndroidEnvironmentContextProvider.markUnavailable()
        startLightMonitoring()
        startLocationMonitoring()
        repositoryJob =
            serviceScope.launch {
                indoorRepository.data.collectLatest { data ->
                    savedLocations = data.locations
                    publishIndoorProximity()
                }
            }
        freshnessJob =
            serviceScope.launch {
                while (isActive) {
                    delay(FRESHNESS_CHECK_MILLIS)
                    publishIndoorProximity()
                }
            }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int = START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        sensorManager?.unregisterListener(this)
        locationClient.removeLocationUpdates(locationCallback)
        repositoryJob?.cancel()
        freshnessJob?.cancel()
        serviceScope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        AndroidEnvironmentContextProvider.markUnavailable()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_LIGHT) {
            AndroidEnvironmentContextProvider.updateLux(event.values[0].roundToInt())
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) = Unit

    private fun startLightMonitoring() {
        val sensor = lightSensor ?: return
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        wakeLock.acquire()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationMonitoring() {
        val hasFine =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val hasCoarse =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        preciseLocationAvailable = hasFine
        if (!hasFine && !hasCoarse) {
            AndroidEnvironmentContextProvider.updateIndoorProximity(false)
            return
        }

        val request =
            LocationRequest
                .Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MILLIS)
                .setMinUpdateIntervalMillis(LOCATION_MIN_INTERVAL_MILLIS)
                .build()
        try {
            locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        } catch (_: SecurityException) {
            preciseLocationAvailable = false
            AndroidEnvironmentContextProvider.updateIndoorProximity(false)
        }
    }

    private fun publishIndoorProximity() {
        val location = latestLocation
        val isUsable =
            preciseLocationAvailable &&
                location != null &&
                location.hasAccuracy() &&
                location.accuracy <= MAX_LOCATION_ACCURACY_METERS &&
                System.currentTimeMillis() - location.time in 0..MAX_LOCATION_AGE_MILLIS
        val isNear =
            isUsable &&
                savedLocations.any { saved ->
                    saved.contains(location!!.latitude, location.longitude)
                }
        AndroidEnvironmentContextProvider.updateIndoorProximity(isNear)
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Exposure monitoring",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        val openApp =
            PendingIntent.getActivity(
                this,
                NOTIFICATION_ID,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return Notification
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_locate)
            .setContentTitle("UV exposure monitoring active")
            .setContentText("Using ambient light and saved indoor locations")
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "exposure_monitoring"
        const val NOTIFICATION_ID = 2003
        const val LOCATION_INTERVAL_MILLIS = 5_000L
        const val LOCATION_MIN_INTERVAL_MILLIS = 2_500L
        const val FRESHNESS_CHECK_MILLIS = 5_000L
        const val MAX_LOCATION_AGE_MILLIS = 30_000L
        const val MAX_LOCATION_ACCURACY_METERS = 50f
    }
}
