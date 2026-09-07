package com.example.uvapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uvapp.domain.model.ForecastDay
import com.example.uvapp.domain.model.HourlyUv
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.SkinType
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
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

/** Home and Forecast share the same timestamped API/cache readings. */
class ForecastViewModel(
    settingsViewModel: SettingsViewModel,
    private val mainViewModel: MainViewModel,
    private val now: () -> LocalDateTime = LocalDateTime::now,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val _state = MutableStateFlow(ForecastUiState())
    val state = _state.asStateFlow()
    private var followsClock = true
    private var followsToday = true
    private var selectedDate = now().toLocalDate()

    init {
        viewModelScope.launch {
            while (isActive) {
                if (followsClock) {
                    val current = now()
                    if (followsToday) selectedDate = current.toLocalDate()
                    _state.update {
                        it.copy(
                            selectedTimeMinutes = current.hour * 60 + current.minute,
                            selectedDayIndex = if (followsToday) {
                                it.days.indexOfFirst { day -> day.dayOfMonth == current.dayOfMonth }.coerceAtLeast(0)
                            } else it.selectedDayIndex,
                        )
                    }
                }
                delay(1_000)
            }
        }
        viewModelScope.launch {
            settingsViewModel.state.collect { settings ->
                _state.update { it.copy(skinType = settings.skinType, spf = settings.spf) }
            }
        }
        viewModelScope.launch {
            mainViewModel.state.collect { main ->
                val dates = main.forecastReadings
                    .groupBy { Instant.ofEpochMilli(it.forecastTimeMillis).atZone(zoneId).toLocalDate() }
                    .toSortedMap()
                val days = dates.map { (date, readings) ->
                    ForecastDay(
                        weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                        dayOfMonth = date.dayOfMonth,
                        maxUv = readings.maxOf { it.uvIndex },
                        // The hourly API does not supply sunrise/sunset times.
                        sunriseMinutes = null,
                        sunsetMinutes = null,
                        hourly = readings.sortedBy { it.forecastTimeMillis }.map {
                            val time = Instant.ofEpochMilli(it.forecastTimeMillis).atZone(zoneId)
                            HourlyUv(time.hour + time.minute / 60.0, it.uvIndex)
                        },
                    )
                }
                _state.update {
                    it.copy(
                        days = days,
                        selectedDayIndex = dates.keys.indexOf(selectedDate).coerceAtLeast(0),
                        uvIndex = main.displayUv,
                        uvAvailable = main.uvAvailable,
                        placeName = main.placeName,
                        lightContext = main.displayContext,
                        isCached = main.isCached,
                    )
                }
            }
        }
    }

    fun selectDay(index: Int) {
        val dates = mainViewModel.state.value.forecastReadings
            .map { Instant.ofEpochMilli(it.forecastTimeMillis).atZone(zoneId).toLocalDate() }.distinct().sorted()
        selectedDate = dates.getOrNull(index) ?: return
        followsClock = true
        followsToday = selectedDate == now().toLocalDate()
        val current = now()
        _state.update { it.copy(selectedDayIndex = index, selectedTimeMinutes = current.hour * 60 + current.minute) }
    }

    fun refresh() {
        followsClock = true
        selectedDate = now().toLocalDate()
        followsToday = true
        mainViewModel.onRefresh()
    }

    fun selectTime(minutes: Int) {
        followsClock = false
        _state.update { it.copy(selectedTimeMinutes = minutes.coerceIn(SEEK_START_MINUTES, SEEK_END_MINUTES)) }
    }
}
