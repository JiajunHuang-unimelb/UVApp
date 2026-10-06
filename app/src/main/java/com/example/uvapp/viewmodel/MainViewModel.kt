package com.example.uvapp.viewmodel

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uvapp.domain.alerts.ExposureAlertGateway
import com.example.uvapp.domain.alerts.SunProtectionAlert
import com.example.uvapp.domain.alerts.SunProtectionAlertKind
import com.example.uvapp.domain.alerts.SunProtectionTracker
import com.example.uvapp.domain.environment.AcousticContext
import com.example.uvapp.domain.environment.DevicePosture
import com.example.uvapp.domain.environment.EnvironmentContextProvider
import com.example.uvapp.domain.environment.EnvironmentSample
import com.example.uvapp.domain.environment.ExposureMonitoringController
import com.example.uvapp.domain.environment.StepActivity
import com.example.uvapp.domain.exposure.ExposureContext
import com.example.uvapp.domain.exposure.ExposurePauseReason
import com.example.uvapp.domain.exposure.ExposureSessionManager
import com.example.uvapp.domain.exposure.ExposureSnapshot
import com.example.uvapp.domain.exposure.ExposureStatus
import com.example.uvapp.domain.location.CurrentLocationProvider
import com.example.uvapp.domain.location.LocationResult
import com.example.uvapp.domain.model.ApiStatus
import com.example.uvapp.domain.model.Coordinates
import com.example.uvapp.domain.model.ExposureDayTotal
import com.example.uvapp.domain.model.ExposureRecord
import com.example.uvapp.domain.model.ExposureRecordStatus
import com.example.uvapp.domain.model.ExposureWeeklySummary
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.LocationFix
import com.example.uvapp.domain.model.PlaceSearchResult
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.model.UvBand
import com.example.uvapp.domain.model.UvDataSource
import com.example.uvapp.domain.model.UvForecastReading
import com.example.uvapp.domain.repository.ExposureHistoryRepository
import com.example.uvapp.domain.repository.PlaceRepository
import com.example.uvapp.domain.repository.UvRepository as ForecastUvRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Weeks the Sun log can show: the current week plus the three before it. */
const val SUN_LOG_WEEK_COUNT = 4

/** Bottom-navigation destinations. */
enum class Tab { HOME, FORECAST, SUN_LOG, SETTINGS }

/** Developer-mode override switches (mirrors the high-fi debug card). */
data class DevUiState(
    val speed60x: Boolean = false,
    val overrideUv: Boolean = false,
    val uvOverride: Double = 0.0, //still 8.4 with this turned to 0.0
    val overrideLight: Boolean = false,
    val lightOverride: LightContext = LightContext.DIRECT_SUN,
    val overrideAudio: Boolean = false,
    val simulateOccluded: Boolean = false,
    val forceOffline: Boolean = false,
    val overrideLocation: Boolean = false,
    val simulateActive: Boolean = false,
)

private val LightContext.mockLux: Int
    get() =
        when (this) {
            LightContext.INDOOR -> 500
            LightContext.SHADE -> 8_000
            LightContext.DIRECT_SUN -> 38_200
        }

/** Immutable snapshot of everything the Home page (and shared chrome) renders. */
data class MainUiState(
    val selectedTab: Tab = Tab.HOME,
    val showSearchDialog: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<PlaceSearchResult> = emptyList(),
    /** "Searching...", "No places found" or an error; null when results (or nothing) show. */
    val searchStatus: String? = null,
    val uvIndex: Double = 0.0,
    val uvAvailable: Boolean = false,
    val forecastReadings: List<UvForecastReading> = emptyList(),
    val placeName: String = "Locating…",
    val remainingSeconds: Long = Long.MAX_VALUE,
    val totalBurnSeconds: Long = Long.MAX_VALUE,
    val exposureStatus: ExposureStatus = ExposureStatus.NOT_STARTED,
    val pauseReason: ExposurePauseReason? = null,
    val exposureSessionId: Long = 0L,
    val exposureStarted: Boolean = false,
    val exposureRunning: Boolean = false,
    val accumulatedDoseSed: Double = 0.0,
    val doseLimitSed: Double = 1.0,
    val remainingDoseSed: Double = 1.0,
    val exposureFraction: Double = 0.0,
    val estimatedExposureMinutes: Double? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCached: Boolean = false,
    val locationFix: LocationFix? = null,
    val lux: Int = 38_200,
    /** Manual lux override from the slidable exposure indicator (testing). */
    val luxOverride: Int? = null,
    val nearIndoorLocation: Boolean? = false,
    val deviceOccluded: Boolean? = null,
    val devicePosture: DevicePosture? = null,
    val isMoving: Boolean? = null,
    val stepsSinceStart: Int? = null,
    val recentSteps: Int? = null,
    val stepsPerMinute: Int? = null,
    val lastStepElapsedMillis: Long? = null,
    val stepActivity: StepActivity? = null,
    val soundLevelDb: Double? = null,
    val acousticContext: AcousticContext? = null,
    val indoorDetected: Boolean = false,
    val apiStatuses: List<ApiStatus> = emptyList(),
    val skinType: SkinType = SkinType.II,
    val spf: Int = 15,
    val devModeEnabled: Boolean = false,
    val dev: DevUiState = DevUiState(),
    /** Sun log weeks, oldest first, current week last; empty until the first read arrives. */
    val sunLogWeeks: List<ExposureWeeklySummary> = emptyList(),
    /** Sun log page (index into [sunLogWeeks]) last settled on; starts on the current week. */
    val sunLogPage: Int = SUN_LOG_WEEK_COUNT - 1,
    /** Sun log chart numbers: false = % of daily limit, true = time in the sun. */
    val sunLogShowsTime: Boolean = false,
    val sunscreenRemindersEnabled: Boolean = true,
    /** Exposure time left until the reapply reminder; null until the user taps "I've applied". */
    val sunscreenReapplyRemainingMillis: Long? = null,
) {
    /** UV shown on the hero (dev override wins). */
    val displayUv: Double get() = if (dev.overrideUv) dev.uvOverride else uvIndex
    val band: UvBand get() = UvBand.fromIndex(displayUv)

    /** Lux used by indoor detection. Explicit developer light simulation wins. */
    val displayLux: Int get() = when {
        dev.overrideLight -> dev.lightOverride.mockLux
        luxOverride != null -> luxOverride
        else -> lux
    }

    /** Light-only classification shown by the lux card before saved-location confirmation. */
    val lightReadingContext: LightContext get() = LightContext.fromLux(displayLux)

    /** Physical proximity reading; developer simulation wins when enabled. */
    val effectiveDeviceOccluded: Boolean? get() =
        if (dev.simulateOccluded) true else deviceOccluded

    val effectiveIsMoving: Boolean? get() =
        if (dev.simulateActive) true else isMoving

    val effectiveAcousticContext: AcousticContext? get() =
        if (dev.overrideAudio) AcousticContext.ACTIVE_OUTDOOR_LIKELY else acousticContext

    /** Auto-pause requires authoritative proximity to a user-confirmed indoor place. */
    val isWithinSavedIndoorLocation: Boolean get() = nearIndoorLocation == true

    /** Low light is only classified as indoor after location-aware debounce confirms it. */
    val displayContext: LightContext get() = when {
        indoorDetected -> LightContext.INDOOR
        displayLux < LightContext.SHADE_MAX_LUX -> LightContext.SHADE
        else -> LightContext.DIRECT_SUN
    }

    /**
     * Context used by the dose model. A covered phone cannot provide a trustworthy ambient-light
     * reading: the user may still be standing in direct sun while the phone is in a pocket or bag.
     * Treat that case as UNKNOWN, whose conservative dose factor is 1.0, unless saved-location and
     * low-light evidence has already confirmed that the user is indoors.
     */
    val exposureContext: ExposureContext get() = when {
        indoorDetected -> ExposureContext.INDOOR
        effectiveDeviceOccluded == true -> ExposureContext.UNKNOWN
        displayLux < LightContext.SHADE_MAX_LUX -> ExposureContext.SHADE
        else -> ExposureContext.DIRECT_SUN
    }

    val isTimerFinite: Boolean get() = totalBurnSeconds < Long.MAX_VALUE
    val isWarning: Boolean get() =
        exposureStatus == ExposureStatus.RUNNING && estimatedExposureMinutes?.let { it < 15.0 } == true
}

/**
 * Home + shared chrome state. Owns the countdown ticker, current forecast data,
 * search dialog visibility and all developer-mode overrides.
 */
class MainViewModel(
    settingsViewModel: SettingsViewModel,
    private val locationProvider: CurrentLocationProvider? = null,
    private val forecastRepository: ForecastUvRepository? = null,
    private val placeRepository: PlaceRepository? = null,
    private val environmentContextProvider: EnvironmentContextProvider? = null,
    private val monitoringController: ExposureMonitoringController? = null,
    private val alertGateway: ExposureAlertGateway? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
    private val historyRepository: ExposureHistoryRepository? = null,
    /** Called after each successful history save (refreshes the weekly widget). */
    private val onHistorySaved: (suspend () -> Unit)? = null,
    /** Shows a sun protection notification (US-17 / US-18). */
    private val onSunProtectionAlert: ((SunProtectionAlert) -> Unit)? = null,
    /** "I've applied" taps from the notification. */
    private val sunscreenAppliedEvents: Flow<Unit>? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private val exposureSession = ExposureSessionManager()
    private val sunProtection = SunProtectionTracker()
    private var exposureClockMillis = elapsedRealtimeMillis()

    private var lastSkinType: SkinType = SkinType.II
    private var locationJob: Job? = null
    private var forecastObservationJob: Job? = null
    private var forecastRefreshJob: Job? = null
    private var placeLookupJob: Job? = null
    private var placeSearchJob: Job? = null
    private var indoorTransitionJob: Job? = null
    private var pendingIndoorTarget: Boolean? = null

    // Exposure history (see docs/exposure-tracking-api.md for the save contract).
    private val historySaveMutex = Mutex()
    private var sunLogWeeksJob: Job? = null
    private var historySessionId: String? = null
    private var historyStartedAtMillis = 0L
    private var historyZoneId: ZoneId = ZoneId.systemDefault()
    private val historyDays = mutableMapOf<LocalDate, ExposureDayTotal>()
    private var lastHistoryStatus = ExposureStatus.NOT_STARTED
    private var lastHistoryContext = ExposureContext.UNKNOWN
    private var lastHistoryWallMillis = 0L
    private var lastHistoryDoseSed = 0.0
    private var lastHistorySaveWallMillis = 0L
    private var lastRecordedThroughMillis = 0L

    fun onIndoorProximity(near: Boolean?) {
        _state.update { it.copy(nearIndoorLocation = if (it.dev.overrideLocation) true else near) }
        evaluateIndoorTransition()
    }
    private var latestEnvironmentSample =
        EnvironmentSample(
            lux = DEFAULT_LUX,
            nearIndoorLocation = false,
            deviceOccluded = null,
        )

    init {
        publishExposure(exposureSession.snapshot())

        // Mirror settings changes (skin type / SPF / dev mode) into the UI state.
        viewModelScope.launch {
            settingsViewModel.state.collect { s ->
                val skinTypeChanged = s.skinType != lastSkinType
                lastSkinType = s.skinType
                _state.update {
                    it.copy(
                        skinType = s.skinType,
                        spf = s.spf,
                        devModeEnabled = s.devModeEnabled,
                        sunscreenRemindersEnabled = s.sunscreenRemindersEnabled,
                    )
                }
                if (skinTypeChanged) syncExposure()
            }
        }

        sunscreenAppliedEvents?.let { events ->
            viewModelScope.launch { events.collect { onSunscreenApplied() } }
        }

        environmentContextProvider?.let { provider ->
            viewModelScope.launch {
                provider.samples.collect { sample ->
                    latestEnvironmentSample = sample
                    val previousExposureContext = _state.value.exposureContext
                    _state.update { state ->
                        state.copy(
                            lux = sample.lux.coerceIn(0, MAX_LUX),
                            nearIndoorLocation = if (state.dev.overrideLocation) true else sample.nearIndoorLocation,
                            deviceOccluded = sample.deviceOccluded,
                            devicePosture = sample.posture,
                            isMoving = sample.isMoving,
                            stepsSinceStart = sample.stepsSinceStart,
                            recentSteps = sample.recentSteps,
                            stepsPerMinute = sample.stepsPerMinute,
                            lastStepElapsedMillis = sample.lastStepElapsedMillis,
                            stepActivity = sample.stepActivity,
                            soundLevelDb = sample.soundLevelDb,
                            acousticContext = sample.acousticContext,
                        )
                    }
                    evaluateIndoorTransition()
                    if (_state.value.exposureContext != previousExposureContext) syncExposure()
                }
            }
        }

        // Countdown ticker — 1 real second per tick, 60 per tick in dev 60x mode.
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                _state.update { st ->
                    st.copy(
                        uvIndex = st.forecastReadings.nearestTo(nowMillis())?.uvIndex ?: st.uvIndex,
                    )
                }
                val step = if (_state.value.dev.speed60x) 60L else 1L
                syncExposure(
                    minimumAdvanceMillis = step * 1_000L,
                    forceMinimumAdvance = step > 1L,
                )
            }
        }

        // Production builds use the location-aware forecast pipeline. Tests that
        // exercise unrelated state may omit these dependencies.
        if (locationProvider != null && forecastRepository != null) {
            locate()
        }

        observeSunLogWeeks()
    }

    // ---- User actions -------------------------------------------------------

    fun onTabSelected(tab: Tab) = _state.update { it.copy(selectedTab = tab) }

    fun onSunLogShowTime(showTime: Boolean) = _state.update { it.copy(sunLogShowsTime = showTime) }

    /** Remembers the week the Sun log pager settled on, so it survives tab switches. */
    fun onSunLogPageSettled(page: Int) = _state.update { it.copy(sunLogPage = page) }

    fun onSearchClick() = _state.update { it.copy(showSearchDialog = true) }

    fun onSearchDismiss() {
        placeSearchJob?.cancel()
        _state.update {
            it.copy(showSearchDialog = false, searchQuery = "", searchResults = emptyList(), searchStatus = null)
        }
    }

    fun onQueryChange(query: String) = _state.update { it.copy(searchQuery = query) }

    /** Runs one Nominatim search for the submitted query (never per keystroke). */
    fun onSearchSubmit() {
        val repository = placeRepository ?: return
        val query = _state.value.searchQuery
        if (query.isBlank()) return
        placeSearchJob?.cancel()
        placeSearchJob =
            viewModelScope.launch {
                _state.update { it.copy(searchResults = emptyList(), searchStatus = "Searching...") }
                val result =
                    try {
                        repository.searchPlaces(query)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                val places = result.getOrNull()
                _state.update {
                    it.copy(
                        searchResults = places.orEmpty(),
                        searchStatus =
                            when {
                                places == null -> "Search failed. Check your connection."
                                places.isEmpty() -> "No places found"
                                else -> null
                            },
                    )
                }
            }
    }

    fun onPlaceSelected(place: PlaceSearchResult) {
        cancelLocationWork()
        placeSearchJob?.cancel()
        // Approximate, so indoor-location logic never treats a searched place as the user's position.
        val fix =
            LocationFix(
                latitude = place.coordinates.latitude,
                longitude = place.coordinates.longitude,
                accuracyMeters = 0f,
                capturedAtMillis = nowMillis(),
                isApproximate = true,
                isMock = false,
            )
        _state.update {
            it.copy(
                placeName = place.name,
                showSearchDialog = false,
                searchQuery = "",
                searchResults = emptyList(),
                searchStatus = null,
                locationFix = fix,
                errorMessage = null,
                isCached = false,
            )
        }
        val repository = forecastRepository ?: return
        observeForecast(fix, repository)
        refreshForecast(fix)
    }

    /** Called only after the UI has granted a foreground location permission. */
    fun onUseCurrentLocation() = locate()

    fun onLocationPermissionDenied(permanentlyDenied: Boolean) {
        locationJob?.cancel()
        _state.update {
            it.copy(
                isLoading = false,
                showSearchDialog = false,
                errorMessage =
                    if (permanentlyDenied) {
                        "Location permission is disabled. Tap the locate button to open Settings."
                    } else {
                        "Location permission was denied. You can retry or choose a place manually."
                    },
            )
        }
    }

    fun onRefresh() {
        val fix = _state.value.locationFix
        if (fix != null && forecastRepository != null) {
            refreshForecast(fix)
        } else {
            locate()
        }
    }

    fun onStartExposure() {
        restartExposureSession()
        pauseNewSessionIfAlreadyIndoor()
    }

    fun onPauseExposure() {
        if (!_state.value.exposureStarted) return
        syncExposure()
        publishExposure(exposureSession.pause(exposureClockMillis), ExposurePauseReason.MANUAL)
    }

    fun onResumeExposure() {
        if (_state.value.indoorDetected) return
        if (!_state.value.exposureStarted) {
            restartExposureSession()
            return
        }
        monitoringController?.start()
        syncExposure()
        publishExposure(exposureSession.resume(exposureClockMillis))
    }

    /** Starts (or restarts) the two-hour reapply timer. */
    fun onSunscreenApplied() {
        sunProtection.markApplied()
        _state.update { it.copy(sunscreenReapplyRemainingMillis = sunProtection.reapplyRemainingMillis) }
    }

    /** The first band alert fires before the permission prompt is answered; let it fire again. */
    fun onSunscreenNotificationsAllowed() = sunProtection.reset()

    fun onResetTimer() {
        restartExposureSession()
        pauseNewSessionIfAlreadyIndoor()
    }

    // ---- Exposure indicator (slidable lux, for testing) ----------------------

    /** Dragging the lux bar overrides the sensor reading and re-derives context. */
    fun onLuxChange(lux: Int) {
        if (_state.value.exposureStarted) return
        val clamped = lux.coerceIn(0, MAX_LUX)
        val previous = _state.value.exposureContext
        _state.update { it.copy(luxOverride = clamped) }
        evaluateIndoorTransition()
        if (_state.value.exposureContext != previous) syncExposure()
    }

    // ---- Developer-mode overrides -------------------------------------------

    fun onSpeedToggle() = _state.update { it.copy(dev = it.dev.copy(speed60x = !it.dev.speed60x)) }

    fun onOverrideUvToggle() = _state.update {
        it.copy(dev = it.dev.copy(overrideUv = !it.dev.overrideUv))
    }.also { syncExposure() }

    fun onUvOverride(value: Double) = _state.update {
        it.copy(dev = it.dev.copy(uvOverride = value.coerceIn(0.0, 12.0)))
    }.also { syncExposure() }

    fun onOverrideLightToggle() {
        val previous = _state.value.exposureContext
        _state.update { it.copy(dev = it.dev.copy(overrideLight = !it.dev.overrideLight)) }
        evaluateIndoorTransition()
        if (_state.value.exposureContext != previous) syncExposure()
    }

    fun onLightOverride(context: LightContext) {
        val previous = _state.value.exposureContext
        _state.update { it.copy(dev = it.dev.copy(lightOverride = context)) }
        evaluateIndoorTransition()
        if (_state.value.exposureContext != previous) syncExposure()
    }

    fun onAudioToggle() = _state.update { it.copy(dev = it.dev.copy(overrideAudio = !it.dev.overrideAudio)) }

    fun onOccludedToggle() {
        val previous = _state.value.exposureContext
        _state.update { it.copy(dev = it.dev.copy(simulateOccluded = !it.dev.simulateOccluded)) }
        evaluateIndoorTransition()
        if (_state.value.exposureContext != previous) syncExposure()
    }

    fun onOfflineToggle() = _state.update { it.copy(dev = it.dev.copy(forceOffline = !it.dev.forceOffline)) }

    fun onLocationToggle() {
        _state.update { state ->
            val overrideLocation = !state.dev.overrideLocation
            state.copy(
                dev = state.dev.copy(overrideLocation = overrideLocation),
                nearIndoorLocation = if (overrideLocation) true else latestEnvironmentSample.nearIndoorLocation,
            )
        }
        evaluateIndoorTransition()
    }

    fun onActiveToggle() = _state.update { it.copy(dev = it.dev.copy(simulateActive = !it.dev.simulateActive)) }

    fun onTestReapplyAlert() {
        alertGateway?.previewReapplyReminder()
    }

    fun onTestBandWarning() {
        alertGateway?.previewBandWarning()
    }

    // ---- Internals -----------------------------------------------------------

    private fun locate() {
        val provider = locationProvider
        val repository = forecastRepository
        if (provider == null || repository == null) {
            _state.update {
                it.copy(errorMessage = "Current-location data is not configured in this build.")
            }
            return
        }

        locationJob?.cancel()
        forecastObservationJob?.cancel()
        forecastRefreshJob?.cancel()
        placeLookupJob?.cancel()
        locationJob =
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        isLoading = true,
                        showSearchDialog = false,
                        errorMessage = null,
                    )
                }

                val result =
                    try {
                        provider.getCurrentLocation()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        finishLocationFailure("Current location is unavailable. Try again or choose a place manually.")
                        return@launch
                    }

                when (result) {
                    is LocationResult.Success -> {
                        val fix = result.fix
                        _state.update {
                            it.copy(
                                locationFix = fix,
                                placeName = fix.coordinateLabel(),
                            )
                        }
                        observeForecast(fix, repository)
                        refreshForecast(fix)
                        loadPlaceName(fix)
                    }

                    LocationResult.PermissionDenied -> finishLocationFailure(
                        "Location permission is required. Tap the locate button to grant it.",
                    )

                    LocationResult.LocationDisabled -> finishLocationFailure(
                        "Location is turned off. Enable it in system settings and try again.",
                    )

                    LocationResult.Timeout -> finishLocationFailure(
                        "Location request timed out. Move near a window or try again.",
                    )

                    LocationResult.Unavailable -> finishLocationFailure(
                        "Current location is unavailable. Try again or choose a place manually.",
                    )
                }
            }
    }

    private fun observeForecast(
        fix: LocationFix,
        repository: ForecastUvRepository,
    ) {
        forecastObservationJob?.cancel()
        forecastObservationJob =
            viewModelScope.launch {
                repository
                    .observeForecast(fix.latitude, fix.longitude)
                    .collect { forecast ->
                        val currentReading = forecast.readings.nearestTo(nowMillis())
                        _state.update { current ->
                            current.copy(
                                uvIndex = currentReading?.uvIndex ?: current.uvIndex,
                                uvAvailable = currentReading != null,
                                forecastReadings = forecast.readings,
                                isLoading =
                                    when {
                                        forecast.isRefreshing -> true
                                        currentReading != null -> false
                                        forecast.errorMessage != null -> false
                                        else -> current.isLoading
                                    },
                                errorMessage = forecast.errorMessage,
                                isCached = forecast.source == UvDataSource.CACHE,
                            )
                        }
                        if (currentReading != null) syncExposure()
                    }
            }
    }

    private fun refreshForecast(
        fix: LocationFix,
    ) {
        val repository = forecastRepository ?: return
        forecastRefreshJob?.cancel()
        forecastRefreshJob =
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    val result = repository.refresh(fix.latitude, fix.longitude)
                    val error = result.exceptionOrNull()
                    _state.update {
                        if (error != null) {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Unable to update UV data.",
                                isCached = true,
                            )
                        } else {
                            it.copy(isLoading = false)
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Unable to update UV data.",
                            isCached = true,
                        )
                    }
                }
            }
    }

    private fun finishLocationFailure(message: String) {
        _state.update {
            it.copy(
                isLoading = false,
                errorMessage = message,
                isCached = it.locationFix != null,
            )
        }
    }

    private fun cancelLocationWork() {
        locationJob?.cancel()
        forecastObservationJob?.cancel()
        forecastRefreshJob?.cancel()
        placeLookupJob?.cancel()
    }

    private fun loadPlaceName(fix: LocationFix) {
        val repository = placeRepository ?: return
        placeLookupJob?.cancel()
        placeLookupJob =
            viewModelScope.launch {
                val result =
                    try {
                        repository.reverseGeocode(
                            Coordinates(
                                latitude = fix.latitude,
                                longitude = fix.longitude,
                            ),
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        return@launch
                    }

                result.getOrNull()?.label?.takeIf(String::isNotBlank)?.let { label ->
                    if (_state.value.locationFix == fix) {
                        _state.update { it.copy(placeName = label) }
                    }
                }
            }
    }

    private fun restartExposureSession() {
        closeHistorySession()
        monitoringController?.start()
        _state.update {
            it.copy(
                exposureSessionId = it.exposureSessionId + 1,
                luxOverride = null,
            )
        }
        advanceExposureClock()
        val state = _state.value
        publishExposure(
            exposureSession.start(
                skinType = state.skinType,
                uvIndex = state.displayUv,
                nowElapsedMs = exposureClockMillis,
                context = state.exposureContext,
            ),
        )
    }

    /** Applies the two-threshold, location-aware debounce without coupling it to GPS. */
    private fun evaluateIndoorTransition() {
        val state = _state.value
        val target =
            when {
                !state.indoorDetected &&
                    state.isWithinSavedIndoorLocation &&
                    state.displayLux < INDOOR_ENTER_LUX -> true

                state.indoorDetected &&
                    (!state.isWithinSavedIndoorLocation || state.displayLux > INDOOR_EXIT_LUX) -> false

                else -> null
            }

        if (target != null && target == pendingIndoorTarget && indoorTransitionJob?.isActive == true) return

        indoorTransitionJob?.cancel()
        indoorTransitionJob = null
        pendingIndoorTarget = target
        if (target == null) return

        indoorTransitionJob =
            viewModelScope.launch {
                delay(INDOOR_TRANSITION_DELAY_MILLIS)
                pendingIndoorTarget = null
                if (target) {
                    confirmIndoorIfStillValid()
                } else {
                    confirmOutdoorIfStillValid()
                }
            }
    }

    private fun confirmIndoorIfStillValid() {
        val state = _state.value
        if (state.indoorDetected || !state.isWithinSavedIndoorLocation || state.displayLux >= INDOOR_ENTER_LUX) return

        _state.update { it.copy(indoorDetected = true) }
        syncExposure()
        autoPauseForIndoor(sendAlert = true)
    }

    private fun confirmOutdoorIfStillValid() {
        val state = _state.value
        val exitStillValid = !state.isWithinSavedIndoorLocation || state.displayLux > INDOOR_EXIT_LUX
        if (!state.indoorDetected || !exitStillValid) return

        val shouldResume = state.pauseReason == ExposurePauseReason.INDOOR_DETECTED
        _state.update { it.copy(indoorDetected = false) }
        syncExposure()
        if (shouldResume) {
            publishExposure(exposureSession.resume(exposureClockMillis))
        }
    }

    private fun pauseNewSessionIfAlreadyIndoor() {
        if (_state.value.indoorDetected) autoPauseForIndoor(sendAlert = false)
    }

    private fun autoPauseForIndoor(sendAlert: Boolean) {
        if (_state.value.exposureStatus != ExposureStatus.RUNNING) return

        syncExposure()
        publishExposure(exposureSession.pause(exposureClockMillis), ExposurePauseReason.INDOOR_DETECTED)
        if (sendAlert) alertGateway?.notifyIndoorAutoPause()
    }

    private fun syncExposure(
        minimumAdvanceMillis: Long = 0L,
        forceMinimumAdvance: Boolean = false,
    ) {
        advanceExposureClock(minimumAdvanceMillis, forceMinimumAdvance)
        val state = _state.value
        var snapshot = exposureSession.snapshot()

        if (snapshot.skinType != state.skinType) {
            snapshot = exposureSession.updateSkinType(state.skinType, exposureClockMillis)
        }
        if (snapshot.uvIndex != state.displayUv) {
            snapshot = exposureSession.updateUvIndex(state.displayUv, exposureClockMillis)
        }
        val exposureContext = state.exposureContext
        if (snapshot.context != exposureContext) {
            exposureSession.updateContext(exposureContext, exposureClockMillis)
        }

        publishExposure(exposureSession.refresh(exposureClockMillis))
    }

    private fun advanceExposureClock(
        minimumAdvanceMillis: Long = 0L,
        forceMinimumAdvance: Boolean = false,
    ) {
        val observedElapsedMillis = elapsedRealtimeMillis()
        val observedAdvance = (observedElapsedMillis - exposureClockMillis).coerceAtLeast(0L)
        exposureClockMillis +=
            when {
                forceMinimumAdvance -> maxOf(observedAdvance, minimumAdvanceMillis)
                observedAdvance > 0L -> observedAdvance
                else -> minimumAdvanceMillis
            }
    }

    private fun publishExposure(snapshot: ExposureSnapshot, reason: ExposurePauseReason? = _state.value.pauseReason) {
        val previousStatus = _state.value.exposureStatus
        _state.update { state ->
            val doseComplete = snapshot.status == ExposureStatus.COMPLETE
            state.copy(
                exposureStatus = snapshot.status,
                pauseReason = if (snapshot.status == ExposureStatus.PAUSED) reason else null,
                exposureStarted = snapshot.isStarted,
                exposureRunning = snapshot.isRunning,
                accumulatedDoseSed = snapshot.accumulatedDoseSed,
                doseLimitSed = snapshot.doseLimitSed,
                remainingDoseSed = snapshot.remainingDoseSed,
                exposureFraction = snapshot.exposureFraction,
                estimatedExposureMinutes = snapshot.estimatedRemainingMinutes,
                remainingSeconds =
                    when {
                        !snapshot.isStarted -> 0L
                        doseComplete -> 0L
                        snapshot.estimatedRemainingSeconds == null -> Long.MAX_VALUE
                        else -> snapshot.estimatedRemainingSeconds
                    },
                totalBurnSeconds =
                    when {
                        !snapshot.isStarted -> 0L
                        snapshot.estimatedTotalSeconds == null -> Long.MAX_VALUE
                        else -> snapshot.estimatedTotalSeconds
                    },
            )
        }
        if (snapshot.status == ExposureStatus.COMPLETE && previousStatus != ExposureStatus.COMPLETE) {
            alertGateway?.notifyExposureLimitReached()
        }
        if (snapshot.status == ExposureStatus.COMPLETE || snapshot.status == ExposureStatus.NOT_STARTED) {
            monitoringController?.stop()
        }
        recordHistory()
        evaluateSunProtection(snapshot)
    }

    /** Runs after every publishExposure(), so band changes, pauses and ticks reach the tracker. */
    private fun evaluateSunProtection(snapshot: ExposureSnapshot) {
        val state = _state.value
        val alert =
            sunProtection.update(
                nowMillis = exposureClockMillis,
                sessionActive = state.sunscreenRemindersEnabled && snapshot.isStarted && snapshot.status != ExposureStatus.COMPLETE,
                running = snapshot.isRunning,
                uvIndex = state.displayUv,
                walking = state.stepActivity == StepActivity.WALKING || state.dev.simulateActive,
                userSpf = state.spf,
            )
        val remaining = sunProtection.reapplyRemainingMillis
        if (remaining != state.sunscreenReapplyRemainingMillis) {
            _state.update { it.copy(sunscreenReapplyRemainingMillis = remaining) }
        }
        if (alert == null) return
        if (alert.kind == SunProtectionAlertKind.BAND) alertGateway?.previewBandWarning() else alertGateway?.previewReapplyReminder()
        onSunProtectionAlert?.invoke(alert)
    }

    // ---- Exposure history -----------------------------------------------------

    /**
     * Runs after every publishExposure(), so each start, pause, resume, context change and
     * tick settles the segment before it under that segment's own status and context.
     * Saves on start, on any status change and every minute while running.
     */
    private fun recordHistory() {
        if (historyRepository == null) return
        val snapshot = exposureSession.snapshot()
        val now = nowMillis()
        val opened = historySessionId == null
        if (opened) {
            if (snapshot.status == ExposureStatus.NOT_STARTED) return
            openHistorySession(now)
        } else {
            addHistoryDelta(snapshot.accumulatedDoseSed, now)
        }
        val statusChanged = opened || snapshot.status != lastHistoryStatus
        val checkpointDue =
            snapshot.status == ExposureStatus.RUNNING &&
                now - lastHistorySaveWallMillis >= HISTORY_CHECKPOINT_MILLIS
        lastHistoryStatus = snapshot.status
        lastHistoryContext = snapshot.context
        if (statusChanged || checkpointDue) saveHistory(toRecordStatus(snapshot.status), now)
    }

    private fun openHistorySession(now: Long) {
        historySessionId = UUID.randomUUID().toString()
        historyStartedAtMillis = now
        historyZoneId = ZoneId.systemDefault()
        historyDays.clear()
        lastHistoryWallMillis = now
        lastHistoryDoseSed = 0.0
        lastHistorySaveWallMillis = now
        lastRecordedThroughMillis = now
    }

    /** Saves the session being replaced; called before start() resets the dose. */
    private fun closeHistorySession() {
        if (historySessionId == null) return
        val now = nowMillis()
        addHistoryDelta(exposureSession.snapshot().accumulatedDoseSed, now)
        saveHistory(ExposureRecordStatus.COMPLETED, now)
        historySessionId = null
    }

    /**
     * Duration uses wall time (never more than real time, even in 60x dev mode) and only
     * counts direct sun, so "time in the sun" excludes shade; shade still adds its dose.
     * A segment that crosses local midnight is split, with dose shared by time on each day.
     */
    private fun addHistoryDelta(doseSed: Double, now: Long) {
        val from = lastHistoryWallMillis
        val inDirectSun =
            lastHistoryStatus == ExposureStatus.RUNNING && lastHistoryContext == ExposureContext.DIRECT_SUN
        val doseDelta = doseSed - lastHistoryDoseSed
        lastHistoryWallMillis = now
        lastHistoryDoseSed = doseSed
        // No wall time passed (or the clock went back): any dose goes to the current day.
        if (now <= from) {
            addToHistoryDay(historyDate(now), 0L, doseDelta)
            return
        }
        var start = from
        while (start < now) {
            val date = historyDate(start)
            val nextMidnight = date.plusDays(1).atStartOfDay(historyZoneId).toInstant().toEpochMilli()
            val end = minOf(now, nextMidnight)
            val share = (end - start).toDouble() / (now - from)
            addToHistoryDay(date, if (inDirectSun) end - start else 0L, doseDelta * share)
            start = end
        }
    }

    private fun historyDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(historyZoneId).toLocalDate()

    private fun addToHistoryDay(date: LocalDate, durationMillis: Long, doseDelta: Double) {
        if (durationMillis == 0L && doseDelta <= 0.0) return
        val previous = historyDays[date]
        if (previous == null) {
            historyDays[date] = ExposureDayTotal(date, durationMillis, doseDelta)
        } else {
            historyDays[date] =
                ExposureDayTotal(date, previous.activeDurationMillis + durationMillis, previous.doseSed + doseDelta)
        }
    }

    /** Builds the record now, then saves it in order behind any earlier save. */
    private fun saveHistory(status: ExposureRecordStatus, now: Long) {
        val repository = historyRepository
        val sessionId = historySessionId
        if (repository == null || sessionId == null) return
        // Storage rejects a checkpoint older than the last one, so a wall-clock jump back is held.
        if (now > lastRecordedThroughMillis) lastRecordedThroughMillis = now
        lastHistorySaveWallMillis = now
        val record =
            ExposureRecord(
                sessionId = sessionId,
                startedAtMillis = historyStartedAtMillis,
                recordedThroughMillis = lastRecordedThroughMillis,
                zoneId = historyZoneId.id,
                status = status,
                days = historyDays.values.sortedBy { it.date },
            )
        viewModelScope.launch {
            // Single attempt: the next checkpoint carries the full cumulative state anyway.
            val saved = historySaveMutex.withLock { repository.save(record).isSuccess }
            val callback = onHistorySaved
            if (saved && callback != null) callback()
        }
    }

    private fun toRecordStatus(status: ExposureStatus): ExposureRecordStatus =
        when (status) {
            ExposureStatus.PAUSED -> ExposureRecordStatus.PAUSED
            ExposureStatus.COMPLETE -> ExposureRecordStatus.COMPLETED
            else -> ExposureRecordStatus.ACTIVE
        }

    /**
     * Loads every viewable Sun log week (the current Mon-Sun week and the ones before it)
     * with one daily query, so all pager pages are ready before the user swipes. Called again
     * by pull-to-refresh, which re-anchors the range to today.
     */
    fun observeSunLogWeeks() {
        val repository = historyRepository ?: return
        val today = todayDate()
        val currentWeekStart = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val firstWeekStart = currentWeekStart.minusWeeks((SUN_LOG_WEEK_COUNT - 1).toLong())
        val daysFlow = repository.observeDaily(firstWeekStart, currentWeekStart.plusWeeks(1))
        sunLogWeeksJob?.cancel()
        sunLogWeeksJob = viewModelScope.launch {
            // observeDaily returns every day in the range (zero when empty), so 7-day chunks are weeks.
            daysFlow.collect { days ->
                val weeks = days.chunked(7).map { weekDays -> ExposureWeeklySummary(weekDays.first().date, weekDays) }
                _state.update { it.copy(sunLogWeeks = weeks) }
            }
        }
    }

    private fun todayDate(): LocalDate =
        Instant.ofEpochMilli(nowMillis()).atZone(ZoneId.systemDefault()).toLocalDate()

    override fun onCleared() {
        monitoringController?.stop()
        super.onCleared()
    }

    private fun LocationFix.coordinateLabel(): String =
        String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)

    private fun List<UvForecastReading>.nearestTo(timestampMillis: Long): UvForecastReading? =
        minByOrNull { reading -> abs(reading.forecastTimeMillis - timestampMillis) }

    private companion object {
        const val DEFAULT_LUX = 38_200
        const val MAX_LUX = 100_000
        const val INDOOR_ENTER_LUX = 1_000
        const val INDOOR_EXIT_LUX = 2_000
        const val INDOOR_TRANSITION_DELAY_MILLIS = 10_000L
        const val HISTORY_CHECKPOINT_MILLIS = 60_000L
    }
}
