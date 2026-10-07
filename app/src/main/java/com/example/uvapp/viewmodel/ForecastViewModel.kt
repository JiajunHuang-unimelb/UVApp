package com.example.uvapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uvapp.data.openmeteo.OpenMeteoApi
import com.example.uvapp.data.openmeteo.OpenMeteoClient
import com.example.uvapp.domain.model.ForecastDay
import com.example.uvapp.domain.model.HourlyUv
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.LocationFix
import com.example.uvapp.domain.model.SkinType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val SEEK_START_MINUTES = 0
const val SEEK_END_MINUTES = 23 * 60 + 59
const val SEEK_STEP_MINUTES = 1

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
    private val sunApi: OpenMeteoApi = OpenMeteoClient.create(),
) : ViewModel() {
    private val _state = MutableStateFlow(ForecastUiState())
    val state = _state.asStateFlow()
    private var followsClock = true
    private var followsToday = true
    private var selectedDate = now().toLocalDate()
    private var sunWindowsByDate: Map<LocalDate, Pair<Int, Int>> = emptyMap()
    private var sunWindowsFix: LocationFix? = null

    init {
        viewModelScope.launch {
            while (isActive) {
                if (followsClock) {
                    val current = now()
                    if (followsToday) selectedDate = current.toLocalDate()
                    _state.update {
                        val targetDayIndex = if (followsToday) {
                            it.days.indexOfFirst { day -> day.dayOfMonth == current.dayOfMonth }.coerceAtLeast(0)
                        } else it.selectedDayIndex
                        it.copy(
                            selectedTimeMinutes = clampToWindow(current.hour * 60 + current.minute, it.days.getOrNull(targetDayIndex)),
                            selectedDayIndex = targetDayIndex,
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
            mainViewModel.state.collect { main -> applyMainState(main) }
        }
    }

    private fun applyMainState(main: MainUiState) {
        val dates = main.forecastReadings
            .groupBy { Instant.ofEpochMilli(it.forecastTimeMillis).atZone(zoneId).toLocalDate() }
            .toSortedMap()
        val fix = main.locationFix
        if (fix != null && fix != sunWindowsFix) {
            sunWindowsFix = fix
            fetchSunWindows(fix)
        }
        val days = dates.map { (date, readings) ->
            val sun = sunWindowsByDate[date]
            ForecastDay(
                weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                dayOfMonth = date.dayOfMonth,
                maxUv = readings.maxOf { it.uvIndex },
                sunriseMinutes = sun?.first,
                sunsetMinutes = sun?.second,
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

    fun selectDay(index: Int) {
        val dates = mainViewModel.state.value.forecastReadings
            .map { Instant.ofEpochMilli(it.forecastTimeMillis).atZone(zoneId).toLocalDate() }.distinct().sorted()
        selectedDate = dates.getOrNull(index) ?: return
        followsClock = true
        followsToday = selectedDate == now().toLocalDate()
        val current = now()
        _state.update {
            it.copy(
                selectedDayIndex = index,
                selectedTimeMinutes = clampToWindow(current.hour * 60 + current.minute, it.days.getOrNull(index)),
            )
        }
    }

    fun refresh() {
        followsClock = true
        selectedDate = now().toLocalDate()
        followsToday = true
        mainViewModel.onRefresh()
    }

    fun selectTime(minutes: Int) {
        followsClock = false
        _state.update { it.copy(selectedTimeMinutes = clampToWindow(minutes, it.selectedDay)) }
    }

    fun selectCurrentTime() {
        followsClock = true
        val current = now()
        _state.update { it.copy(selectedTimeMinutes = clampToWindow(current.hour * 60 + current.minute, it.selectedDay)) }
    }

    /** Fetches this week's sunrise/sunset online; the slider falls back to the full day until this resolves. */
    private fun fetchSunWindows(fix: LocationFix) {
        viewModelScope.launch {
            try {
                val response = sunApi.getSunTimes(fix.latitude, fix.longitude)
                sunWindowsByDate = response.daily.time.indices.associate { i ->
                    val sunrise = LocalDateTime.parse(response.daily.sunrise[i])
                    val sunset = LocalDateTime.parse(response.daily.sunset[i])
                    LocalDate.parse(response.daily.time[i]) to
                        Pair(sunrise.hour * 60 + sunrise.minute, sunset.hour * 60 + sunset.minute)
                }
                applyMainState(mainViewModel.state.value)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Leave sunWindowsByDate as-is; clampToWindow falls back to the full day.
            }
        }
    }

    /** The selectable range for a day: its sunrise-sunset window, or the full day when unknown. */
    private fun clampToWindow(minutes: Int, day: ForecastDay?): Int {
        val start = day?.sunriseMinutes
        val end = day?.sunsetMinutes
        return if (start != null && end != null && start < end) {
            minutes.coerceIn(start, end)
        } else {
            minutes.coerceIn(SEEK_START_MINUTES, SEEK_END_MINUTES)
        }
    }
}
