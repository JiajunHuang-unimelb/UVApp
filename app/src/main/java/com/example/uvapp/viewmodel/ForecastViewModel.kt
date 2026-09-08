package com.example.uvapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uvapp.domain.model.ForecastDay
import com.example.uvapp.domain.model.HourlyUv
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.model.UvForecastReading
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val SEEK_START_MINUTES = 0
const val SEEK_END_MINUTES = 23 * 60 + 59
const val SEEK_STEP_MINUTES = 1
const val SEEK_STEPS = SEEK_END_MINUTES - SEEK_START_MINUTES

data class ForecastUiState(
    val days: List<ForecastDay> = emptyList(),
    val selectedDayIndex: Int = 0,
    val selectedTimeMinutes: Int = 0,
    val skinType: SkinType = SkinType.II,
    val spf: Int = 15,
    val uvIndex: Double = 0.0,
    val uvAvailable: Boolean = false,
    val placeName: String = "",
    val lightContext: LightContext = LightContext.DIRECT_SUN,
    val isCached: Boolean = false,
) {
    val selectedDay: ForecastDay? get() = days.getOrNull(selectedDayIndex)
    val selectedUv: Double get() = selectedDay?.uvAt(selectedTimeMinutes / 60.0) ?: uvIndex
}

/**
 * Forecast page state: 7 day chips, hour selection and the hero copy that must
 * stay in sync with Home (same values, same source of truth).
 *
 * Forecast data comes from [MainViewModel]'s real, Room-cached Open-Meteo
 * readings (the same pipeline the Home hero number uses) — there is no
 * separate repository call here.
 */
class ForecastViewModel(
    settingsViewModel: SettingsViewModel,
    private val mainViewModel: MainViewModel,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    now: () -> LocalDate = { LocalDate.now(zoneId) },
) : ViewModel() {
    private val _state = MutableStateFlow(ForecastUiState())
    val state = _state.asStateFlow()
    private var followsClock = true
    private var followsToday = true
    private var selectedDate = now().toLocalDate()

    private var selectedDate: LocalDate = now()
    private var groupedDates: List<LocalDate> = emptyList()

    init {
        viewModelScope.launch {
            settingsViewModel.state.collect { s ->
                _state.update { it.copy(skinType = s.skinType, spf = s.spf) }
            }
        }
        viewModelScope.launch {
            mainViewModel.state.collect { m ->
                val byDate = groupByDate(m.forecastReadings)
                groupedDates = byDate.keys.toList()
                val days = byDate.map { (date, readings) -> date.toForecastDay(readings) }
                _state.update { st ->
                    st.copy(
                        days = days,
                        selectedDayIndex = groupedDates.indexOf(selectedDate).coerceAtLeast(0),
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
        val date = groupedDates.getOrNull(index) ?: return
        selectedDate = date
        _state.update { st ->
            val day = st.days.getOrNull(index) ?: return@update st.copy(selectedDayIndex = index)
            st.copy(
                selectedDayIndex = index,
                selectedTimeMinutes = clampToDayWindow(day, st.selectedTimeMinutes),
            )
        }
    }

    /** Reloads the forecast through the same location-aware repository Home uses. */
    fun refresh() = mainViewModel.onRefresh()

    fun selectTime(minutes: Int) {
        followsClock = false
        _state.update { it.copy(selectedTimeMinutes = minutes.coerceIn(SEEK_START_MINUTES, SEEK_END_MINUTES)) }
    }

    private fun groupByDate(readings: List<UvForecastReading>): Map<LocalDate, List<UvForecastReading>> =
        readings.groupBy { Instant.ofEpochMilli(it.forecastTimeMillis).atZone(zoneId).toLocalDate() }.toSortedMap()

    /**
     * Sunrise/sunset aren't in the hourly API response, so they're
     * approximated from the first/last hour with a non-zero UV reading.
     */
    private fun LocalDate.toForecastDay(readings: List<UvForecastReading>): ForecastDay {
        val sorted = readings.sortedBy { it.forecastTimeMillis }
        val daylightHours =
            sorted
                .filter { it.uvIndex > 0.0 }
                .map { Instant.ofEpochMilli(it.forecastTimeMillis).atZone(zoneId).hour }
        return ForecastDay(
            weekday = dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
            dayOfMonth = dayOfMonth,
            maxUv = sorted.maxOfOrNull { it.uvIndex } ?: 0.0,
            sunriseMinutes = (daylightHours.minOrNull() ?: 6) * 60,
            sunsetMinutes = (daylightHours.maxOrNull() ?: 20) * 60 + 59,
            hourly = sorted.map { reading ->
                val hour = Instant.ofEpochMilli(reading.forecastTimeMillis).atZone(zoneId).hour
                HourlyUv(hour.toDouble(), reading.uvIndex)
            },
        )
    }

    /** Snaps a time into the selected day's sunrise..sunset window (30-min grid). */
    private fun clampToDayWindow(day: ForecastDay, timeMinutes: Int): Int {
        val start = ((day.sunriseMinutes + SEEK_STEP_MINUTES - 1) / SEEK_STEP_MINUTES) * SEEK_STEP_MINUTES
        val end = (day.sunsetMinutes / SEEK_STEP_MINUTES) * SEEK_STEP_MINUTES
        return timeMinutes.coerceIn(start, end)
    }
}
