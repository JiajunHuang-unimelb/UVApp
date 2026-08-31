package com.example.uvapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uvapp.data.UvRepository
import com.example.uvapp.domain.model.ForecastDay
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.SkinType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Seekbar window: 06:30 .. 20:30 in 30-minute steps (29 steps). */
const val SEEK_START_MINUTES = 6 * 60 + 30
const val SEEK_END_MINUTES = 20 * 60 + 30
const val SEEK_STEP_MINUTES = 30
const val SEEK_STEPS = (SEEK_END_MINUTES - SEEK_START_MINUTES) / SEEK_STEP_MINUTES

/** Immutable snapshot of the Forecast page. */
data class ForecastUiState(
    val days: List<ForecastDay> = emptyList(),
    val selectedDayIndex: Int = 0,
    val selectedTimeMinutes: Int = 14 * 60 + 30,
    val skinType: SkinType = SkinType.II,
    val spf: Int = 15,
    val uvIndex: Double = 8.4,
    val placeName: String = "Southbank, Melbourne",
    val lightContext: LightContext = LightContext.DIRECT_SUN,
    val isCached: Boolean = false,
) {
    val selectedDay: ForecastDay? get() = days.getOrNull(selectedDayIndex)

    /** Forecast UV at the selected day + hour (falls back to the current UV). */
    val selectedUv: Double get() = selectedDay?.uvAt(selectedTimeMinutes / 60.0) ?: uvIndex
}

/**
 * Forecast page state: 7 day chips, hour selection and the hero copy that must
 * stay in sync with Home (same values, same source of truth).
 */
class ForecastViewModel(
    private val repository: UvRepository,
    settingsViewModel: SettingsViewModel,
    mainViewModel: MainViewModel,
) : ViewModel() {

    private val _state = MutableStateFlow(ForecastUiState())
    val state: StateFlow<ForecastUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val days = repository.getForecastDays()
            _state.update {
                it.copy(
                    days = days,
                    selectedDayIndex = days.indexOfFirst { d -> d.weekday == "Wed" }.coerceAtLeast(0),
                )
            }
        }
        viewModelScope.launch {
            settingsViewModel.state.collect { s ->
                _state.update { it.copy(skinType = s.skinType, spf = s.spf) }
            }
        }
        viewModelScope.launch {
            mainViewModel.state.collect { m ->
                _state.update {
                    it.copy(
                        uvIndex = m.displayUv,
                        placeName = m.placeName,
                        lightContext = m.displayContext,
                        isCached = m.isCached,
                    )
                }
            }
        }
    }

    fun selectDay(index: Int) {
        _state.update { st ->
            val day = st.days.getOrNull(index) ?: return@update st
            st.copy(
                selectedDayIndex = index,
                selectedTimeMinutes = clampToDayWindow(day, st.selectedTimeMinutes),
            )
        }
    }

    /** Reloads the forecast from the repository (keeps the current selection). */
    fun refresh() {
        viewModelScope.launch {
            val days = repository.getForecastDays()
            _state.update { st ->
                st.copy(
                    days = days,
                    selectedDayIndex = st.selectedDayIndex.coerceIn(days.indices),
                )
            }
        }
    }

    fun selectTime(minutes: Int) {
        _state.update { st -> st.copy(selectedTimeMinutes = minutes.coerceIn(SEEK_START_MINUTES, SEEK_END_MINUTES)) }
    }

    /** Snaps a time into the selected day's sunrise..sunset window (30-min grid). */
    private fun clampToDayWindow(day: ForecastDay, timeMinutes: Int): Int {
        val start = ((day.sunriseMinutes + SEEK_STEP_MINUTES - 1) / SEEK_STEP_MINUTES) * SEEK_STEP_MINUTES
        val end = (day.sunsetMinutes / SEEK_STEP_MINUTES) * SEEK_STEP_MINUTES
        return timeMinutes.coerceIn(start, end)
    }
}
