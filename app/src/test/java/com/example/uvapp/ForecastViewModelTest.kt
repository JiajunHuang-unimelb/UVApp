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
import java.time.LocalDateTime
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
 * ForecastViewModel derives its data entirely from MainViewModel's real,
 * location-aware forecast pipeline and, like MainViewModel, runs an infinite
 * clock-following ticker -- so tests use a dedicated dispatcher and bounded
 * advanceTimeBy rather than advanceUntilIdle (see MainViewModelTest).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForecastViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val zone = ZoneOffset.UTC
    private val fixedNow: LocalDateTime = LocalDateTime.of(2026, 9, 9, 14, 30)
    private val todayStartMillis = LocalDateTime.of(2026, 9, 9, 0, 0).toInstant(zone).toEpochMilli()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
        val vm = ForecastViewModel(settings, main, now = { fixedNow }, zoneId = zone)
        settle()
        return vm to forecastRepository
    }

    @Test
    fun `groups hourly readings into days and selects today`() {
        val readings = listOf(
            hourlyReading(dayOffset = 0, hour = 12, uv = 8.5),
            hourlyReading(dayOffset = 1, hour = 12, uv = 4.2),
        )
        val (vm, _) = buildViewModel(readings)

        assertEquals(2, vm.state.value.days.size)
        assertEquals(0, vm.state.value.selectedDayIndex)
        assertEquals(8.5, vm.state.value.days[0].maxUv, 0.0)
        assertEquals(4.2, vm.state.value.days[1].maxUv, 0.0)
    }

    @Test
    fun `follows the clock by default, tracking the current time`() {
        val (vm, _) = buildViewModel(listOf(hourlyReading(0, 12, 5.0)))

        assertEquals(14 * 60 + 30, vm.state.value.selectedTimeMinutes)
    }

    @Test
    fun `selecting a time stops following the clock`() {
        val (vm, _) = buildViewModel(listOf(hourlyReading(0, 12, 5.0)))

        vm.selectTime(9 * 60)
        mainDispatcher.scheduler.advanceTimeBy(2_000)
        mainDispatcher.scheduler.runCurrent()

        assertEquals(9 * 60, vm.state.value.selectedTimeMinutes)
    }

    @Test
    fun `selectCurrentTime resumes following the clock`() {
        val (vm, _) = buildViewModel(listOf(hourlyReading(0, 12, 5.0)))
        vm.selectTime(9 * 60)

        vm.selectCurrentTime()

        assertEquals(14 * 60 + 30, vm.state.value.selectedTimeMinutes)
    }

    @Test
    fun `selectDay switches to the chosen day`() {
        val readings = listOf(
            hourlyReading(dayOffset = 0, hour = 12, uv = 8.5),
            hourlyReading(dayOffset = 1, hour = 12, uv = 4.2),
        )
        val (vm, _) = buildViewModel(readings)

        vm.selectDay(1)

        assertEquals(1, vm.state.value.selectedDayIndex)
    }

    @Test
    fun `refresh delegates to MainViewModel's forecast repository`() {
        val (vm, forecastRepository) = buildViewModel(listOf(hourlyReading(0, 12, 5.0)))
        val callsAfterInit = forecastRepository.refreshCallCount

        vm.refresh()
        settle()

        assertTrue(forecastRepository.refreshCallCount > callsAfterInit)
    }

    @Test
    fun `mirrors the current UV and place name from MainViewModel`() {
        val (vm, _) = buildViewModel(listOf(hourlyReading(0, 12, 5.0)))

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
