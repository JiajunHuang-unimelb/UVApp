package com.example.uvapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uvapp.data.ApiStatus
import com.example.uvapp.data.UvRepository
import com.example.uvapp.domain.advisor.BurnCalculator
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.model.UvBand
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
    val uvIndex: Double = 8.4,
    val placeName: String = "Southbank, Melbourne",
    val lightContext: LightContext = LightContext.DIRECT_SUN,
    val remainingSeconds: Long = 152 * 60L,
    val totalBurnSeconds: Long = 178 * 60L,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isCached: Boolean = false,
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
 * Home + shared chrome state. Owns the countdown ticker, mock refresh flow,
 * search dialog visibility and all developer-mode overrides.
 */
class MainViewModel(
    private val repository: UvRepository,
    settingsViewModel: SettingsViewModel,
) : ViewModel() {

    private val _state = MutableStateFlow(MainUiState())
    val state: StateFlow<MainUiState> = _state.asStateFlow()

    private var lastSkinType: SkinType = SkinType.II
    private var lastSpf: Int = 15

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
                _state.update { st ->
                    val step = if (st.dev.speed60x) 60L else 1L
                    st.copy(remainingSeconds = (st.remainingSeconds - step).coerceAtLeast(0L))
                }
            }
        }

        refresh()
    }

    // ---- User actions -------------------------------------------------------

    fun onTabSelected(tab: Tab) = _state.update { it.copy(selectedTab = tab) }

    fun onSearchClick() = _state.update { it.copy(showSearchDialog = true) }

    fun onSearchDismiss() = _state.update { it.copy(showSearchDialog = false) }

    fun onQueryChange(query: String) = _state.update { it.copy(searchQuery = query) }

    fun onPlaceSelected(suburb: String) = _state.update {
        it.copy(placeName = "$suburb, Melbourne", showSearchDialog = false, searchQuery = "")
    }

    fun onUseCurrentLocation() {
        _state.update { it.copy(placeName = "Southbank, Melbourne", showSearchDialog = false, searchQuery = "") }
        refresh()
    }

    fun onLocate() = refresh()

    fun onRefresh() = refresh()

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

    private fun refresh() {
        viewModelScope.launch {
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
                val uv = repository.getCurrentUv()
                val place = repository.getPlaceName()
                val sensor = repository.getSensorContext()
                val statuses = repository.getApiStatuses()
                _state.update {
                    it.copy(
                        isLoading = false,
                        uvIndex = uv.index,
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
            }
        }
    }

    /** Re-derive burn time from the current profile and restart the countdown. */
    private fun recomputeBurn() {
        val st = _state.value
        val totalMinutes = BurnCalculator.burnMinutes(st.skinType, st.spf, st.displayUv, st.displayContext)
        val totalSeconds = if (totalMinutes == Int.MAX_VALUE) Long.MAX_VALUE else totalMinutes.toLong() * 60L
        _state.update {
            it.copy(
                totalBurnSeconds = totalSeconds,
                remainingSeconds = totalSeconds,
            )
        }
    }
}
