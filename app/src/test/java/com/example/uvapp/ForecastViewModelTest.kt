package com.example.uvapp

import com.example.uvapp.domain.location.CurrentLocationProvider
import com.example.uvapp.domain.location.LocationResult
import com.example.uvapp.domain.model.LocationFix
import com.example.uvapp.domain.model.UvDataSource
import com.example.uvapp.domain.model.UvForecastReading
import com.example.uvapp.domain.model.UvForecastState
import com.example.uvapp.domain.repository.UvRepository as ForecastUvRepository
import com.example.uvapp.viewmodel.ForecastViewModel
import com.example.uvapp.viewmodel.MainViewModel
import com.example.uvapp.viewmodel.SettingsViewModel
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ForecastViewModel now derives its data entirely from MainViewModel's real,
 * location-aware forecast pipeline (no repository of its own) — these tests
 * drive that through fakes rather than a standalone forecast fixture.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForecastViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val zone = ZoneOffset.UTC
    private val today: LocalDate = LocalDate.of(2026, 9, 9)
    private val todayStartMillis = today.atStartOfDay(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }
    private val source = MutableStateFlow(UvForecastState(readings = readings))

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun settle() {
        mainDispatcher.scheduler.advanceTimeBy(1)
        mainDispatcher.scheduler.runCurrent()
    }

    private fun hourlyReading(dayOffset: Long, hour: Int, uv: Double) = UvForecastReading(
        forecastTimeMillis = todayStartMillis + (dayOffset * 24 + hour) * 3_600_000L,
        uvIndex = uv,
        clearSkyUvIndex = null,
        cloudCoverPercent = null,
    )

    private fun buildViewModel(
        readings: List<UvForecastReading>,
        forecastRepository: FakeForecastRepository = FakeForecastRepository(readings),
    ): Pair<ForecastViewModel, FakeForecastRepository> {
        val settings = SettingsViewModel(FakeUserPreferencesRepository())
        val main =
            MainViewModel(
                auxiliaryRepository = FakeUvRepository(),
                settingsViewModel = settings,
                locationProvider = FakeLocationProvider(),
                forecastRepository = forecastRepository,
            )
        settle()
        return ForecastViewModel(settings, main, zoneId = zone, now = { today }) to forecastRepository
    }

    @Test
    fun `groups hourly readings into days and selects today`() {
        val readings = listOf(
            hourlyReading(dayOffset = 0, hour = 12, uv = 8.5),
            hourlyReading(dayOffset = 1, hour = 12, uv = 4.2),
        )
        val (vm, _) = buildViewModel(readings)
        settle()

        assertEquals(2, vm.state.value.days.size)
        assertEquals(0, vm.state.value.selectedDayIndex)
        assertEquals(8.5, vm.state.value.days[0].maxUv, 0.0)
        assertEquals(4.2, vm.state.value.days[1].maxUv, 0.0)
    }

    @Test
    fun `selectDay clamps the selected time into that day's daylight window`() {
        val readings = listOf(
            hourlyReading(dayOffset = 0, hour = 8, uv = 3.0),
            hourlyReading(dayOffset = 0, hour = 18, uv = 3.0),
            hourlyReading(dayOffset = 1, hour = 9, uv = 2.0),
            hourlyReading(dayOffset = 1, hour = 15, uv = 2.0),
        )
        val (vm, _) = buildViewModel(readings)
        settle()

        vm.selectTime(20 * 60) // 20:00 — inside day 0's window, outside day 1's.
        vm.selectDay(1)

        val state = vm.state.value
        assertEquals(1, state.selectedDayIndex)
        // Sunset is approximated as the last daylight hour + 59 min (15:59), then
        // snapped down to the nearest 30-min step -> 15:30.
        assertEquals(15 * 60 + 30, state.selectedTimeMinutes)
    }

    @Test
    fun `selectTime clamps to the global seek window`() {
        val (vm, _) = buildViewModel(listOf(hourlyReading(0, 12, 5.0)))
        settle()

        vm.selectTime(0)
        assertEquals(SEEK_START_MINUTES, vm.state.value.selectedTimeMinutes)

        vm.selectTime(24 * 60)
        assertEquals(SEEK_END_MINUTES, vm.state.value.selectedTimeMinutes)
    }

    @Test
    fun `refresh delegates to MainViewModel's forecast repository`() {
        val (vm, forecastRepository) = buildViewModel(listOf(hourlyReading(0, 12, 5.0)))
        settle()
        val callsAfterInit = forecastRepository.refreshCallCount

        vm.refresh()
        settle()

        assertTrue(forecastRepository.refreshCallCount > callsAfterInit)
    }

    @Test
    fun `mirrors the current UV and place name from MainViewModel`() {
        val (vm, _) = buildViewModel(listOf(hourlyReading(0, 12, 5.0)))
        settle()

        assertEquals(5.0, vm.state.value.uvIndex, 0.0)
        assertEquals("-37.81360, 144.96310", vm.state.value.placeName)
    }

    private class FakeLocationProvider : CurrentLocationProvider {
        override suspend fun getCurrentLocation(): LocationResult =
            LocationResult.Success(
                LocationFix(
                    latitude = -37.8136,
                    longitude = 144.9631,
                    accuracyMeters = 10f,
                    capturedAtMillis = 0L,
                    isApproximate = false,
                    isMock = false,
                ),
            )
    }

    private class FakeForecastRepository(
        initialReadings: List<UvForecastReading>,
    ) : ForecastUvRepository {
        private val state = MutableStateFlow(UvForecastState(readings = initialReadings, source = UvDataSource.NETWORK))
        var refreshCallCount = 0
            private set

        override fun observeForecast(
            latitude: Double,
            longitude: Double,
        ): Flow<UvForecastState> = state

        override suspend fun refresh(
            latitude: Double,
            longitude: Double,
            force: Boolean,
        ): Result<Unit> {
            refreshCallCount++
            return Result.success(Unit)
        }
    }
}
