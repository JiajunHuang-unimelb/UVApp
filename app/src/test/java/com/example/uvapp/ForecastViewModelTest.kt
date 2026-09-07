package com.example.uvapp

import com.example.uvapp.domain.model.ForecastDay
import com.example.uvapp.domain.model.HourlyUv
import com.example.uvapp.viewmodel.ForecastViewModel
import com.example.uvapp.viewmodel.MainViewModel
import com.example.uvapp.viewmodel.SEEK_END_MINUTES
import com.example.uvapp.viewmodel.SEEK_START_MINUTES
import com.example.uvapp.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** See MainViewModelTest for why this uses bounded advanceTimeBy, not advanceUntilIdle. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForecastViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private val days = listOf(
        ForecastDay("Mon", 11, 2.4, sunriseMinutes = 403, sunsetMinutes = 1211, hourly = listOf(HourlyUv(12.0, 2.0))),
        ForecastDay("Wed", 13, 8.5, sunriseMinutes = 403, sunsetMinutes = 1211, hourly = listOf(HourlyUv(12.0, 8.5))),
        // Sunrise 08:20 -> clamps up to 08:30 (510); sunset 18:20 -> clamps down to 18:00 (1080).
        ForecastDay("Fri", 15, 4.2, sunriseMinutes = 500, sunsetMinutes = 1100, hourly = listOf(HourlyUv(12.0, 4.2))),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun settle() {
        mainDispatcher.scheduler.advanceTimeBy(701)
        mainDispatcher.scheduler.runCurrent()
    }

    private fun buildViewModel(repo: FakeUvRepository): ForecastViewModel {
        val settings = SettingsViewModel(FakeUserPreferencesRepository())
        val main = MainViewModel(repo, settings)
        return ForecastViewModel(repo, settings, main)
    }

    @Test
    fun `init selects the Wed day and loads the forecast`() {
        val repo = FakeUvRepository(forecastDays = days)
        val vm = buildViewModel(repo)

        settle()

        assertEquals(days, vm.state.value.days)
        assertEquals(1, vm.state.value.selectedDayIndex)
    }

    @Test
    fun `selectDay clamps the selected time into that day's sunrise-sunset window`() {
        val repo = FakeUvRepository(forecastDays = days)
        val vm = buildViewModel(repo)
        settle()

        vm.selectTime(20 * 60) // 20:00 — inside Wed's window, outside Fri's.
        vm.selectDay(2) // Fri

        val state = vm.state.value
        assertEquals(2, state.selectedDayIndex)
        assertEquals(1_080, state.selectedTimeMinutes) // clamped to Fri's 18:00 sunset
    }

    @Test
    fun `selectTime clamps to the global seek window`() {
        val repo = FakeUvRepository(forecastDays = days)
        val vm = buildViewModel(repo)
        settle()

        vm.selectTime(0)
        assertEquals(SEEK_START_MINUTES, vm.state.value.selectedTimeMinutes)

        vm.selectTime(24 * 60)
        assertEquals(SEEK_END_MINUTES, vm.state.value.selectedTimeMinutes)
    }

    @Test
    fun `refresh reloads the forecast from the repository`() {
        val repo = FakeUvRepository(forecastDays = days)
        val vm = buildViewModel(repo)
        settle()
        val callsAfterInit = repo.forecastCallCount

        vm.refresh()
        settle()

        assertTrue(repo.forecastCallCount > callsAfterInit)
    }

    @Test
    fun `mirrors the current UV and place name from MainViewModel`() {
        val repo = FakeUvRepository(forecastDays = days, currentUv = 5.0, placeName = "Fitzroy, Melbourne")
        val vm = buildViewModel(repo)
        settle()

        assertEquals(5.0, vm.state.value.uvIndex, 0.0)
        assertEquals("Fitzroy, Melbourne", vm.state.value.placeName)
    }
}
