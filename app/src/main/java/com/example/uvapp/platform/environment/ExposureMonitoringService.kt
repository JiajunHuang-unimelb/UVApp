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
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.uvapp.MainActivity
import com.example.uvapp.R
import com.example.uvapp.data.preferences.DataStoreIndoorLocationRepository
import com.example.uvapp.domain.environment.AmbientLightFilter
import com.example.uvapp.domain.environment.ExposureMonitoringPolicy
import com.example.uvapp.domain.environment.LocationFixValidator
import com.example.uvapp.domain.environment.LocationMonitoringPolicy
import com.example.uvapp.domain.environment.LocationRequestFailurePolicy
import com.example.uvapp.domain.environment.MotionClassifier
import com.example.uvapp.domain.environment.ProximityClassifier
import com.example.uvapp.domain.environment.ProximityDebouncer
import com.example.uvapp.domain.environment.SensorRegistrationPolicy
import com.example.uvapp.domain.environment.SensorRegistrationStatus
import com.example.uvapp.domain.environment.SensorFreshnessTracker
import com.example.uvapp.domain.environment.StepActivityReading
import com.example.uvapp.domain.environment.StepCounterTracker
import com.example.uvapp.domain.model.IndoorLocation
import com.example.uvapp.domain.model.contains
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Keeps ambient-light, physical-proximity, and saved-location monitoring alive in the background. */
class ExposureMonitoringService : Service(), SensorEventListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private val indoorRepository by lazy { DataStoreIndoorLocationRepository(this) }
    private val sensorManager by lazy { getSystemService(SensorManager::class.java) }
    private val lightSensor by lazy { sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT) }
    private val proximitySensor by lazy { sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY) }
    private val accelerometerSensor by lazy { sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    private val stepCounterSensor by lazy { sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) }
    private val ambientLightFilter = AmbientLightFilter()
    private val proximityDebouncer = ProximityDebouncer(PROXIMITY_CLEAR_DELAY_MILLIS)
    private val motionClassifier = MotionClassifier()
    private val motionFreshnessTracker = SensorFreshnessTracker(MOTION_STALE_AFTER_MILLIS)
    private val stepCounterTracker = StepCounterTracker()
    private val locationMonitoringPolicy = LocationMonitoringPolicy()
    private val locationFailurePolicy = LocationRequestFailurePolicy()
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "$packageName:exposure-monitoring",
        ).apply { setReferenceCounted(false) }
    }

    private var savedLocations: List<IndoorLocation> = emptyList()
    private var latestLocation: Location? = null
    private var latestStepReading: StepActivityReading? = null
    private var nearIndoorLocation = false
    private var preciseLocationAvailable = false
    private var locationPermissionAvailable = false
    private var currentLocationIntervalMillis: Long? = null
    private var pendingLocationIntervalMillis: Long? = null
    private var locationRequestGeneration = 0L
    private var freshLocationGeneration = 0L
    private var freshLocationCancellation: CancellationTokenSource? = null
    private var repositoryJob: Job? = null
    private var freshnessJob: Job? = null
    private var proximityClearJob: Job? = null
    private var stepCounterRegistered = false

    private val locationCallback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                latestLocation = result.lastLocation
                publishIndoorProximity(SystemClock.elapsedRealtime())
            }
        }

    override fun onCreate() {
        super.onCreate()
        startMonitoringForeground()
        acquireWakeLock()
        AndroidEnvironmentContextProvider.markUnavailable()
        startSensorMonitoring()
        startLocationMonitoring()
        repositoryJob =
            serviceScope.launch {
                indoorRepository.data.collectLatest { data ->
                    savedLocations = data.locations
                    if (savedLocations.isEmpty()) {
                        stopUnusedLocationMonitoring()
                    } else {
                        reconfigureLocationUpdates(LocationMonitoringPolicy.DEFAULT_INTERVAL_MILLIS)
                        publishIndoorProximity(SystemClock.elapsedRealtime())
                    }
                }
            }
        freshnessJob =
            serviceScope.launch {
                while (isActive) {
                    delay(FRESHNESS_CHECK_MILLIS)
                    val nowElapsedMillis = SystemClock.elapsedRealtime()
                    latestStepReading = stepCounterTracker.snapshot(nowElapsedMillis)
                    AndroidEnvironmentContextProvider.updateSteps(latestStepReading)
                    if (motionFreshnessTracker.consumeExpiration(nowElapsedMillis)) {
                        // Accelerometer is continuous; silence means its listener is no longer trustworthy.
                        AndroidEnvironmentContextProvider.updateMotion(null)
                        restartAccelerometerMonitoring()
                    }
                    publishIndoorProximity(nowElapsedMillis)
                }
            }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // onStartCommand runs again when a permission grant refreshes the active service.
        refreshStepCounterMonitoring()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        locationRequestGeneration++
        freshLocationGeneration++
        sensorManager?.unregisterListener(this)
        locationClient.removeLocationUpdates(locationCallback)
        freshLocationCancellation?.cancel()
        repositoryJob?.cancel()
        freshnessJob?.cancel()
        proximityClearJob?.cancel()
        serviceScope.cancel()
        if (wakeLock.isHeld) wakeLock.release()
        AndroidEnvironmentContextProvider.markUnavailable()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                // A rolling median rejects brief shadows from hands, clothing, or sensor noise.
                event.values.firstOrNull()
                    ?.let(ambientLightFilter::update)
                    ?.let(AndroidEnvironmentContextProvider::updateLux)
            }

            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values.firstOrNull()
                val isOccluded = ProximityClassifier.isOccluded(distance, event.sensor.maximumRange)
                updateProximity(isOccluded, event.timestamp / NANOS_PER_MILLISECOND)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val values = event.values
                val reading =
                    if (values.size >= 3) {
                        motionClassifier.update(values[0], values[1], values[2])
                    } else {
                        null
                    }
                AndroidEnvironmentContextProvider.updateMotion(reading)
                if (reading != null) {
                    motionFreshnessTracker.onSample(event.timestamp / NANOS_PER_MILLISECOND)
                }
            }

            Sensor.TYPE_STEP_COUNTER -> {
                val cumulativeSteps = event.values.firstOrNull()
                val reading =
                    cumulativeSteps?.let {
                        stepCounterTracker.update(it, event.timestamp / NANOS_PER_MILLISECOND)
                    }
                latestStepReading = reading
                AndroidEnvironmentContextProvider.updateSteps(reading)
                applyLocationPolicy(SystemClock.elapsedRealtime())
            }
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) {
        if (sensor == null || accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE) return

        // Do not retain a value after Android reports that its source is unreliable.
        when (sensor.type) {
            Sensor.TYPE_LIGHT -> {
                ambientLightFilter.reset()
                AndroidEnvironmentContextProvider.markLuxUnavailable()
            }

            Sensor.TYPE_PROXIMITY -> markProximityUnavailable()
            Sensor.TYPE_ACCELEROMETER -> {
                motionFreshnessTracker.markUnavailable()
                AndroidEnvironmentContextProvider.updateMotion(null)
            }
            Sensor.TYPE_STEP_COUNTER -> {
                latestStepReading = null
                AndroidEnvironmentContextProvider.updateSteps(null)
            }
        }
    }

    private fun startSensorMonitoring() {
        registerSensor(lightSensor) {
            ambientLightFilter.reset()
            AndroidEnvironmentContextProvider.markLuxUnavailable()
        }
        registerSensor(proximitySensor) {
            markProximityUnavailable()
        }
        registerAccelerometer()
        refreshStepCounterMonitoring()
    }

    private fun refreshStepCounterMonitoring() {
        val hasPermission = hasActivityRecognitionPermission()
        if (ExposureMonitoringPolicy.shouldRegisterStepCounter(hasPermission, stepCounterRegistered)) {
            stepCounterRegistered =
                registerSensor(stepCounterSensor) {
                    latestStepReading = null
                    AndroidEnvironmentContextProvider.updateSteps(null)
                }
        } else if (!hasPermission) {
            if (stepCounterRegistered) {
                stepCounterSensor?.let { sensor -> sensorManager?.unregisterListener(this, sensor) }
            }
            stepCounterRegistered = false
            latestStepReading = null
            AndroidEnvironmentContextProvider.updateSteps(null)
        }
    }

    private fun registerSensor(
        sensor: Sensor?,
        onRegistered: () -> Unit = {},
        onUnavailable: () -> Unit,
    ): Boolean {
        val registered =
            sensor?.let { availableSensor ->
                sensorManager?.registerListener(
                    this,
                    availableSensor,
                    SensorManager.SENSOR_DELAY_NORMAL,
                ) == true
            } == true
        val status = SensorRegistrationPolicy.status(sensor != null, registered)
        if (status == SensorRegistrationStatus.REGISTERED) onRegistered() else onUnavailable()
        return status == SensorRegistrationStatus.REGISTERED
    }

    private fun registerAccelerometer(): Boolean =
        registerSensor(
            sensor = accelerometerSensor,
            onUnavailable = {
                motionFreshnessTracker.markUnavailable()
                AndroidEnvironmentContextProvider.updateMotion(null)
            },
            onRegistered = {
                motionFreshnessTracker.monitoringStarted(SystemClock.elapsedRealtime())
            },
        )

    private fun restartAccelerometerMonitoring() {
        accelerometerSensor?.let { sensor -> sensorManager?.unregisterListener(this, sensor) }
        registerAccelerometer()
    }

    private fun updateProximity(
        isOccluded: Boolean?,
        sampleElapsedMillis: Long,
    ) {
        if (isOccluded == null) {
            markProximityUnavailable()
            return
        }

        if (isOccluded) {
            proximityClearJob?.cancel()
            proximityClearJob = null
        }

        val debounced = proximityDebouncer.update(isOccluded, sampleElapsedMillis)
        AndroidEnvironmentContextProvider.updateDeviceOcclusion(debounced)
        if (!isOccluded && debounced == true && proximityClearJob?.isActive != true) {
            proximityClearJob =
                serviceScope.launch {
                    delay(PROXIMITY_CLEAR_DELAY_MILLIS)
                    AndroidEnvironmentContextProvider.updateDeviceOcclusion(
                        proximityDebouncer.currentValue(SystemClock.elapsedRealtime()),
                    )
                    proximityClearJob = null
                }
        }
    }

    private fun markProximityUnavailable() {
        proximityClearJob?.cancel()
        proximityClearJob = null
        proximityDebouncer.reset()
        AndroidEnvironmentContextProvider.updateDeviceOcclusion(null)
    }

    private fun hasActivityRecognitionPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    private fun startMonitoringForeground() {
        val hasLocationPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        val serviceType =
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasLocationPermission ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

                else -> 0
            }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, createNotification(), serviceType)
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
        locationPermissionAvailable = hasFine || hasCoarse
        if (!hasFine && !hasCoarse) {
            AndroidEnvironmentContextProvider.updateIndoorProximity(false)
            return
        }

        if (savedLocations.isNotEmpty()) {
            reconfigureLocationUpdates(LocationMonitoringPolicy.DEFAULT_INTERVAL_MILLIS)
        }
    }

    @SuppressLint("MissingPermission")
    private fun reconfigureLocationUpdates(intervalMillis: Long) {
        val nowElapsedMillis = SystemClock.elapsedRealtime()
        if (
            !locationPermissionAvailable ||
            savedLocations.isEmpty() ||
            currentLocationIntervalMillis == intervalMillis ||
            pendingLocationIntervalMillis == intervalMillis ||
            !locationFailurePolicy.canRequest(nowElapsedMillis)
        ) {
            return
        }
        val request =
            LocationRequest
                .Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMillis)
                .setMinUpdateIntervalMillis(intervalMillis)
                .build()
        val generation = ++locationRequestGeneration
        pendingLocationIntervalMillis = intervalMillis
        currentLocationIntervalMillis = null
        try {
            locationClient.removeLocationUpdates(locationCallback)
            locationClient
                .requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                .addOnSuccessListener {
                    if (generation != locationRequestGeneration) return@addOnSuccessListener
                    pendingLocationIntervalMillis = null
                    currentLocationIntervalMillis = intervalMillis
                    locationFailurePolicy.recordSuccess()
                }.addOnFailureListener { error ->
                    if (generation != locationRequestGeneration) return@addOnFailureListener
                    pendingLocationIntervalMillis = null
                    handleLocationFailure(error, continuousRegistrationFailed = true)
                }
        } catch (error: RuntimeException) {
            if (generation == locationRequestGeneration) {
                pendingLocationIntervalMillis = null
                handleLocationFailure(error, continuousRegistrationFailed = true)
            }
        }
    }

    /** GPS cannot contribute to saved-place detection when the user has no saved places. */
    private fun stopUnusedLocationMonitoring() {
        locationRequestGeneration++
        freshLocationGeneration++
        if (currentLocationIntervalMillis != null || pendingLocationIntervalMillis != null) {
            locationClient.removeLocationUpdates(locationCallback)
        }
        freshLocationCancellation?.cancel()
        freshLocationCancellation = null
        currentLocationIntervalMillis = null
        pendingLocationIntervalMillis = null
        latestLocation = null
        nearIndoorLocation = false
        AndroidEnvironmentContextProvider.updateIndoorProximity(false)
    }

    private fun publishIndoorProximity(nowElapsedMillis: Long) {
        val location = latestLocation
        val fixElapsedMillis =
            location
                ?.elapsedRealtimeNanos
                ?.takeIf { it > 0L }
                ?.div(NANOS_PER_MILLISECOND)
        val isUsable =
            preciseLocationAvailable &&
                location != null &&
                location.hasAccuracy() &&
                LocationFixValidator.isUsable(
                    accuracyMeters = location.accuracy,
                    fixElapsedMillis = fixElapsedMillis,
                    nowElapsedMillis = nowElapsedMillis,
                    maxAccuracyMeters = MAX_LOCATION_ACCURACY_METERS,
                    maxAgeMillis = MAX_LOCATION_AGE_MILLIS,
                )
        nearIndoorLocation =
            isUsable &&
                savedLocations.any { saved ->
                    saved.contains(location!!.latitude, location.longitude)
                }
        AndroidEnvironmentContextProvider.updateIndoorProximity(nearIndoorLocation)
        applyLocationPolicy(nowElapsedMillis)
    }

    private fun applyLocationPolicy(nowElapsedMillis: Long) {
        if (savedLocations.isEmpty()) return
        val latestFixElapsedMillis =
            latestLocation
                ?.elapsedRealtimeNanos
                ?.takeIf { it > 0L }
                ?.div(NANOS_PER_MILLISECOND)
        val decision =
            locationMonitoringPolicy.evaluate(
                nowElapsedMillis = nowElapsedMillis,
                nearIndoorLocation = nearIndoorLocation,
                stepReading = latestStepReading,
                latestFixElapsedMillis = latestFixElapsedMillis,
            )
        reconfigureLocationUpdates(decision.intervalMillis)
        if (decision.requestFreshFix) requestFreshLocation()
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation() {
        val nowElapsedMillis = SystemClock.elapsedRealtime()
        if (
            !preciseLocationAvailable ||
            savedLocations.isEmpty() ||
            !locationFailurePolicy.canRequest(nowElapsedMillis)
        ) {
            return
        }
        val generation = ++freshLocationGeneration
        freshLocationCancellation?.cancel()
        val cancellation = CancellationTokenSource()
        freshLocationCancellation = cancellation
        try {
            locationClient
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                .addOnSuccessListener { location ->
                    if (generation != freshLocationGeneration) return@addOnSuccessListener
                    if (location != null) {
                        locationFailurePolicy.recordSuccess()
                        latestLocation = location
                        publishIndoorProximity(SystemClock.elapsedRealtime())
                    } else {
                        handleLocationFailure(error = null, continuousRegistrationFailed = false)
                    }
                }.addOnFailureListener { error ->
                    if (generation != freshLocationGeneration) return@addOnFailureListener
                    handleLocationFailure(error, continuousRegistrationFailed = false)
                }.addOnCompleteListener {
                    if (
                        generation == freshLocationGeneration &&
                        freshLocationCancellation === cancellation
                    ) {
                        freshLocationCancellation = null
                    }
                }
        } catch (error: RuntimeException) {
            if (generation == freshLocationGeneration) {
                handleLocationFailure(error, continuousRegistrationFailed = false)
                if (freshLocationCancellation === cancellation) freshLocationCancellation = null
            }
        }
    }

    /** Clears stale indoor evidence after both synchronous and asynchronous location failures. */
    private fun handleLocationFailure(
        error: Throwable?,
        continuousRegistrationFailed: Boolean,
    ) {
        val decision =
            locationFailurePolicy.recordFailure(
                error = error,
                nowElapsedMillis = SystemClock.elapsedRealtime(),
            )
        if (continuousRegistrationFailed) currentLocationIntervalMillis = null
        if (decision.permissionRevoked) {
            preciseLocationAvailable = false
            locationPermissionAvailable = false
            currentLocationIntervalMillis = null
            pendingLocationIntervalMillis = null
            locationRequestGeneration++
            locationClient.removeLocationUpdates(locationCallback)
        }
        latestLocation = null
        nearIndoorLocation = false
        AndroidEnvironmentContextProvider.updateIndoorProximity(false)
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
            .setContentText("Using light, proximity, and saved indoor locations")
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "exposure_monitoring"
        const val NOTIFICATION_ID = 2003
        const val FRESHNESS_CHECK_MILLIS = 5_000L
        const val MOTION_STALE_AFTER_MILLIS = 30_000L
        const val PROXIMITY_CLEAR_DELAY_MILLIS = 1_000L
        const val MAX_LOCATION_AGE_MILLIS = 75_000L
        const val MAX_LOCATION_ACCURACY_METERS = 50f
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
