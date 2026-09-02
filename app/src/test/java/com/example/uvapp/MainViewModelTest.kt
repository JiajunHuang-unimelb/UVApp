package com.example.uvapp

import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.viewmodel.MainViewModel
import com.example.uvapp.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Uses a dedicated [StandardTestDispatcher] (not the implicit one from `runTest`) so we can
 * advance virtual time in bounded steps via [advanceTimeBy]. MainViewModel's countdown ticker
 * is an infinite `while (isActive) { delay(1_000); ... }` loop, so calling `advanceUntilIdle()`
 * would never return.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Lets the one-shot refresh() coroutine (700ms simulated latency) finish. */
    private fun settle() {
        mainDispatcher.scheduler.advanceTimeBy(701)
        mainDispatcher.scheduler.runCurrent()
    }

    @Test
    fun `initial refresh populates state from the repository`() {
        val repo = FakeUvRepository(currentUv = 6.2, placeName = "Docklands, Melbourne")
        val vm = MainViewModel(repo, SettingsViewModel())

        settle()

        val state = vm.state.value
        assertEquals(6.2, state.uvIndex, 0.0)
        assertEquals("Docklands, Melbourne", state.placeName)
        assertEquals(LightContext.DIRECT_SUN, state.lightContext)
        assertTrue(state.apiStatuses.isNotEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `forceOffline keeps cached values and surfaces an error`() {
        val vm = MainViewModel(FakeUvRepository(), SettingsViewModel())
        settle()

        vm.onOfflineToggle()
        vm.onRefresh()
        settle()

        val state = vm.state.value
        assertTrue(state.isCached)
        assertEquals("Couldn't update · showing cached data", state.errorMessage)
    }

    @Test
    fun `dev UV override recomputes the burn countdown`() {
        val vm = MainViewModel(FakeUvRepository(), SettingsViewModel())
        settle()

        vm.onOverrideUvToggle()
        vm.onUvOverride(1.0)

        val state = vm.state.value
        assertEquals(1.0, state.displayUv, 0.0)
        assertTrue(state.totalBurnSeconds > 0)
        assertEquals(state.totalBurnSeconds, state.remainingSeconds)
    }

    @Test
    fun `countdown ticks down one second per real second`() {
        val vm = MainViewModel(FakeUvRepository(), SettingsViewModel())
        settle()
        val before = vm.state.value.remainingSeconds

        mainDispatcher.scheduler.advanceTimeBy(3_000)
        mainDispatcher.scheduler.runCurrent()

        assertEquals(before - 3, vm.state.value.remainingSeconds)
    }

    @Test
    fun `speed60x makes the countdown tick 60 seconds per tick`() {
        val vm = MainViewModel(FakeUvRepository(), SettingsViewModel())
        settle()
        vm.onSpeedToggle()
        val before = vm.state.value.remainingSeconds

        mainDispatcher.scheduler.advanceTimeBy(1_000)
        mainDispatcher.scheduler.runCurrent()

        assertEquals((before - 60).coerceAtLeast(0), vm.state.value.remainingSeconds)
    }
}
