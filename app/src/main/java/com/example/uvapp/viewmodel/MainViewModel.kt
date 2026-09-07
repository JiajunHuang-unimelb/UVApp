package com.example.uvapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uvapp.data.ApiStatus
import com.example.uvapp.data.UvRepository as AuxiliaryUvRepository
import com.example.uvapp.domain.advisor.BurnCalculator
import com.example.uvapp.domain.location.CurrentLocationProvider
import com.example.uvapp.domain.location.LocationResult
import com.example.uvapp.domain.model.Coordinates
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.LocationFix
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.model.UvBand
import com.example.uvapp.domain.model.UvDataSource
import com.example.uvapp.domain.model.UvForecastReading
import com.example.uvapp.domain.repository.PlaceRepository
import com.example.uvapp.domain.repository.UvRepository as ForecastUvRepository
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Bottom-navigation destinations. */
enum class Tab { HOME, FORECAST, SETTINGS }

/** Developer-mode override switches (mirrors the high-fi debug card). */
data class DevUiState(
    val speed60x: Boolean = false,
    val overrideUv: Boolean = false,
    val uvOverride: Double = 8.4,
    val overrideLight: Boolean = false,
    val lightOverride: LightContext = LightContext.DIRECT_SUN,
    val overrideAudio: Boolean = false,
    val simulateOccluded: Boolean = false,
    val forceOffline: Boolean = false,
    val overrideLocation: Boolean = false,
    val simulateActive: Boolean = false,
)

/** Immutable snapshot of everything the Home page (and shared chrome) renders. */
data class MainUiState(
    val selectedTab: Tab = Tab.HOME,
    val showSearchDialog: Boolean = false,
    val searchQuery: String = "",
    val uvIndex: Double = 0.0,
    val uvAvailable: Boolean = false,
    val forecastReadings: List<UvForecastReading> = emptyList(),
    val placeName: String = "Southbank, Melbourne",
    val lightContext: LightContext = LightContext.DIRECT_SUN,
    val remainingSeconds: Long = Long.MAX_VALUE,
    val totalBurnSeconds: Long = Long.MAX_VALUE,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCached: Boolean = false,
    val locationFix: LocationFix? = null,
    val lux: Int = 38_200,
    /** Manual lux override from the slidable exposure indicator (testing). */
    val luxOverride: Int? = null,
    val stepsPerMinute: Int = 84,
    val apiStatuses: List<ApiStatus> = emptyList(),
    val skinType: SkinType = SkinType.II,
    val spf: Int = 15,
    val devModeEnabled: Boolean = false,
    val dev: DevUiState = DevUiState(),
) {
    /** UV shown on the hero (dev override wins). */
    val displayUv: Double get() = if (dev.overrideUv) dev.uvOverride else uvIndex
    val band: UvBand get() = UvBand.fromIndex(displayUv)

    /** Lux shown on the exposure indicator (manual override wins). */
    val displayLux: Int get() = luxOverride ?: lux

    /** Context shown on Home (dev override wins, then the manual lux override). */
    val displayContext: LightContext get() = when {
        dev.overrideLight -> dev.lightOverride
        luxOverride != null -> LightContext.fromLux(luxOverride)
        else -> lightContext
    }

    val isTimerFinite: Boolean get() = totalBurnSeconds in 1 until Long.MAX_VALUE
    val isWarning: Boolean get() = isTimerFinite && remainingSeconds < 15 * 60L
}

/**
 * Home + shared chrome state. Owns the countdown ticker, current forecast data,
 * search dialog visibility and all developer-mode overrides.
 */
class MainViewModel(
    private val auxiliaryRepository: AuxiliaryUvRepository,
    settingsViewModel: SettingsViewModel,
    private val locationProvider: CurrentLocationProvider? = null,
    private val forecastRepository: ForecastUvRepository? = null,
    private val placeRepository: PlaceRepository? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private var lastSkinType: SkinType = SkinType.II
    private var lastSpf: Int = 15
    private var auxiliaryRefreshJob: Job? = null
    private var locationJob: Job? = null
    private var forecastObservationJob: Job? = null
    private var forecastRefreshJob: Job? = null
    private var placeLookupJob: Job? = null

    init {
        // Mirror settings changes (skin type / SPF / dev mode) into the UI state.
        viewModelScope.launch {
            settingsViewModel.state.collect { s ->
                val burnChanged = s.skinType != lastSkinType || s.spf != lastSpf
                lastSkinType = s.skinType
                lastSpf = s.spf
                _state.update { it.copy(skinType = s.skinType, spf = s.spf, devModeEnabled = s.devModeEnabled) }
                if (burnChanged) recomputeBurn()
            }
        }

        // Countdown ticker — 1 real second per tick, 60 per tick in dev 60x mode.
        viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val previousUv = _state.value.displayUv
                _state.update { st ->
                    val step = if (st.dev.speed60x) 60L else 1L
                    st.copy(
                        uvIndex = st.forecastReadings.nearestTo(nowMillis())?.uvIndex ?: st.uvIndex,
                        remainingSeconds = if (st.isTimerFinite) (st.remainingSeconds - step).coerceAtLeast(0L) else st.remainingSeconds,
                    )
                }
                if (_state.value.displayUv != previousUv) recomputeBurn()
            }
        }

        if (forecastRepository == null) refreshAuxiliaryData()
    }

    // ---- User actions -------------------------------------------------------

    fun onTabSelected(tab: Tab) = _state.update { it.copy(selectedTab = tab) }

    fun onSearchClick() = _state.update { it.copy(showSearchDialog = true) }

    fun onSearchDismiss() = _state.update { it.copy(showSearchDialog = false) }

    fun onQueryChange(query: String) = _state.update { it.copy(searchQuery = query) }

    fun onPlaceSelected(suburb: String) {
        cancelLocationWork()
        _state.update {
            it.copy(
                placeName = "$suburb, Melbourne",
                showSearchDialog = false,
                searchQuery = "",
                locationFix = null,
                errorMessage = null,
                isCached = false,
            )
        }
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
            refreshForecast(fix, force = true)
        } else if (forecastRepository != null) {
            locate()
        } else {
            refreshAuxiliaryData()
        }
    }

    fun onResetTimer() = _state.update { it.copy(remainingSeconds = it.totalBurnSeconds) }

    // ---- Exposure indicator (slidable lux, for testing) ----------------------

    /** Dragging the lux bar overrides the sensor reading and re-derives context. */
    fun onLuxChange(lux: Int) {
        val clamped = lux.coerceIn(0, 100_000)
        val previous = _state.value.displayContext
        _state.update { it.copy(luxOverride = clamped) }
        // Only restart the burn countdown when the effective context changed.
        if (_state.value.displayContext != previous) recomputeBurn()
    }

    // ---- Developer-mode overrides -------------------------------------------

    fun onSpeedToggle() = _state.update { it.copy(dev = it.dev.copy(speed60x = !it.dev.speed60x)) }

    fun onOverrideUvToggle() = _state.update {
        it.copy(dev = it.dev.copy(overrideUv = !it.dev.overrideUv))
    }.also { recomputeBurn() }

    fun onUvOverride(value: Double) = _state.update {
        it.copy(dev = it.dev.copy(uvOverride = value.coerceIn(0.0, 12.0)))
    }.also { recomputeBurn() }

    fun onOverrideLightToggle() = _state.update {
        it.copy(dev = it.dev.copy(overrideLight = !it.dev.overrideLight))
    }.also { recomputeBurn() }

    fun onLightOverride(context: LightContext) = _state.update {
        it.copy(dev = it.dev.copy(lightOverride = context))
    }.also { recomputeBurn() }

    fun onAudioToggle() = _state.update { it.copy(dev = it.dev.copy(overrideAudio = !it.dev.overrideAudio)) }

    fun onOccludedToggle() = _state.update { it.copy(dev = it.dev.copy(simulateOccluded = !it.dev.simulateOccluded)) }

    fun onOfflineToggle() = _state.update { it.copy(dev = it.dev.copy(forceOffline = !it.dev.forceOffline)) }

    fun onLocationToggle() = _state.update { it.copy(dev = it.dev.copy(overrideLocation = !it.dev.overrideLocation)) }

    fun onActiveToggle() = _state.update { it.copy(dev = it.dev.copy(simulateActive = !it.dev.simulateActive)) }

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

        auxiliaryRefreshJob?.cancel()
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
                        refreshForecast(fix, force = false)
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
                                forecastReadings = forecast.readings,
                                uvAvailable = currentReading != null,
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
                        if (currentReading != null) recomputeBurn()
                    }
            }
    }

    private fun refreshForecast(
        fix: LocationFix,
        force: Boolean,
    ) {
        val repository = forecastRepository ?: return
        forecastRefreshJob?.cancel()
        forecastRefreshJob =
            viewModelScope.launch {
                _state.update { it.copy(isLoading = true, errorMessage = null) }
                try {
                    val result = repository.refresh(fix.latitude, fix.longitude, force)
                    result.exceptionOrNull()?.let { error ->
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = error.message ?: "Unable to update UV data.",
                                isCached = true,
                            )
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

    private fun refreshAuxiliaryData() {
        auxiliaryRefreshJob?.cancel()
        auxiliaryRefreshJob = viewModelScope.launch {
            val offline = _state.value.dev.forceOffline
            _state.update { it.copy(isLoading = true) }
            delay(700) // simulated network latency
            if (offline) {
                // Backend unreachable -> keep cached values, surface an error.
                _state.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Couldn't update · showing cached data",
                        isCached = true,
                    )
                }
            } else {
                val uv = auxiliaryRepository.getCurrentUv()
                val place = auxiliaryRepository.getPlaceName()
                val sensor = auxiliaryRepository.getSensorContext()
                val statuses = auxiliaryRepository.getApiStatuses()
                _state.update {
                    it.copy(
                        isLoading = false,
                        uvIndex = uv.index,
                        uvAvailable = true,
                        placeName = place,
                        lightContext = sensor.lightContext,
                        lux = sensor.lux,
                        luxOverride = null,
                        stepsPerMinute = sensor.stepsPerMinute,
                        apiStatuses = statuses,
                        errorMessage = null,
                        isCached = false,
                    )
                }
                recomputeBurn()
            }
        }
    }

    /** Recalculate the estimate while preserving elapsed time. */
    private fun recomputeBurn() {
        val st = _state.value
        val totalMinutes = BurnCalculator.burnMinutes(st.skinType, st.spf, st.displayUv, st.displayContext)
        val totalSeconds = if (totalMinutes == Int.MAX_VALUE) Long.MAX_VALUE else totalMinutes.toLong() * 60L
        _state.update {
            it.copy(
                totalBurnSeconds = totalSeconds,
                remainingSeconds = if (totalSeconds == Long.MAX_VALUE) Long.MAX_VALUE
                    else (totalSeconds - if (it.isTimerFinite) it.totalBurnSeconds - it.remainingSeconds else 0L).coerceAtLeast(0L),
            )
        }
    }

    private fun LocationFix.coordinateLabel(): String =
        String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)

    private fun List<UvForecastReading>.nearestTo(timestampMillis: Long): UvForecastReading? =
        minByOrNull { reading -> abs(reading.forecastTimeMillis - timestampMillis) }
}
