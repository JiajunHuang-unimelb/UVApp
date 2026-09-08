package com.example.uvapp

import com.example.uvapp.domain.location.CurrentLocationProvider
import com.example.uvapp.domain.location.LocationResult
import com.example.uvapp.domain.model.LocationFix
import com.example.uvapp.domain.model.UvForecastReading
import com.example.uvapp.domain.model.UvForecastState
import com.example.uvapp.domain.repository.UvRepository
import com.example.uvapp.viewmodel.ForecastViewModel
import com.example.uvapp.viewmodel.MainViewModel
import com.example.uvapp.viewmodel.SettingsViewModel
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ForecastViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private var now = LocalDateTime.of(2026, 9, 7, 9, 15)
    private var refreshes = 0
    private val readings = (0..2).flatMap { day ->
        (0..23).map { hour ->
            UvForecastReading(
                now.toLocalDate().plusDays(day.toLong()).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
                if (hour == 9) 2.0 + day else if (hour == 10) 6.0 + day else 0.0,
                null, null,
            )
        }
    }
    private val source = MutableStateFlow(UvForecastState(readings = readings))

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun build(): ForecastViewModel {
        val settings = SettingsViewModel(FakeUserPreferencesRepository())
        val repository = object : UvRepository {
            override fun observeForecast(latitude: Double, longitude: Double) = source
            override suspend fun refresh(latitude: Double, longitude: Double, force: Boolean): Result<Unit> {
                refreshes++
                return Result.success(Unit)
            }
        }
        val location = object : CurrentLocationProvider {
            override suspend fun getCurrentLocation() = LocationResult.Success(
                LocationFix(0.0, 0.0, 1f, 0L, false, false),
            )
        }
        val main = MainViewModel(FakeUvRepository(), settings, location, repository,
            nowMillis = { now.toInstant(ZoneOffset.UTC).toEpochMilli() })
        val forecast = ForecastViewModel(settings, main, { now }, ZoneOffset.UTC)
        main.onUseCurrentLocation()
        dispatcher.scheduler.runCurrent()
        return forecast
    }

    @Test fun `opens on today at the current minute using API data`() {
        val vm = build()
        assertEquals(7, vm.state.value.selectedDay!!.dayOfMonth)
        assertEquals(555, vm.state.value.selectedTimeMinutes)
        assertEquals(3.0, vm.state.value.selectedUv, 0.001)
    }

    @Test fun `another date starts at the current minute not the daily maximum`() {
        val vm = build()
        vm.selectDay(1)
        assertEquals(8, vm.state.value.selectedDay!!.dayOfMonth)
        assertEquals(555, vm.state.value.selectedTimeMinutes)
        assertEquals(4.0, vm.state.value.selectedUv, 0.001)
        now = now.plusMinutes(1)
        dispatcher.scheduler.advanceTimeBy(1000)
        dispatcher.scheduler.runCurrent()
        assertEquals(556, vm.state.value.selectedTimeMinutes)
        assertTrue(vm.state.value.selectedUv > 4.0)
    }

    @Test fun `following today advances the selected date at midnight`() {
        val vm = build()
        now = now.plusDays(1).withHour(0).withMinute(0)
        dispatcher.scheduler.advanceTimeBy(1000)
        dispatcher.scheduler.runCurrent()
        assertEquals(8, vm.state.value.selectedDay!!.dayOfMonth)
        assertEquals(0, vm.state.value.selectedTimeMinutes)
    }

    @Test fun `dragging updates the reading and preserves the inspected time`() {
        val vm = build()
        vm.selectTime(600)
        assertEquals(6.0, vm.state.value.selectedUv, 0.001)
        now = now.plusMinutes(1)
        dispatcher.scheduler.advanceTimeBy(1000)
        dispatcher.scheduler.runCurrent()
        assertEquals(600, vm.state.value.selectedTimeMinutes)
    }

    @Test fun `night time is not clamped to daylight and empty forecasts are safe`() {
        val vm = build()
        now = now.withHour(23).withMinute(45)
        vm.selectDay(2)
        assertEquals(1425, vm.state.value.selectedTimeMinutes)
        source.value = UvForecastState()
        dispatcher.scheduler.runCurrent()
        vm.refresh()
        dispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.days.isEmpty())
        assertTrue(refreshes > 1)
    }
}
