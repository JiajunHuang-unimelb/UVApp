package com.example.uvapp

import com.example.uvapp.domain.exposure.ExposureStatus
import com.example.uvapp.platform.environment.MockEnvironmentContextProvider
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

/** Integration-style unit tests for sensor samples consumed by [MainViewModel]. */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelSensorTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `uncovering phone restores lux based exposure context`() {
        val environment =
            MockEnvironmentContextProvider(
                initialLux = 8_000,
                initiallyDeviceOccluded = true,
            )
        val viewModel = buildViewModel(environment)
        dispatcher.scheduler.runCurrent()

        viewModel.onOverrideUvToggle()
        viewModel.onUvOverride(6.0)
        viewModel.onStartExposure()
        val conservativeSeconds = viewModel.state.value.totalBurnSeconds

        environment.setDeviceOccluded(false)
        dispatcher.scheduler.runCurrent()
        // Moving to a lower dose rate has to hold steady first, so tilting cannot flip the countdown.
        assertEquals(conservativeSeconds, viewModel.state.value.totalBurnSeconds)

        dispatcher.scheduler.advanceTimeBy(11_000)
        dispatcher.scheduler.runCurrent()

        // Once unobstructed for long enough, the same 8,000 lux reading is trustworthy shade evidence again.
        val shadeSeconds = viewModel.state.value.totalBurnSeconds
        assertTrue(shadeSeconds > conservativeSeconds)
    }

    @Test
    fun `missing sensor evidence does not pause exposure`() {
        val environment =
            MockEnvironmentContextProvider(
                // 100,000 is the provider's conservative unavailable-light fallback.
                initialLux = 100_000,
                initiallyNearIndoorLocation = false,
                initiallyDeviceOccluded = null,
            )
        val viewModel = buildViewModel(environment)
        dispatcher.scheduler.runCurrent()
        viewModel.onOverrideUvToggle()
        viewModel.onUvOverride(6.0)
        viewModel.onStartExposure()

        dispatcher.scheduler.advanceTimeBy(12_000)
        dispatcher.scheduler.runCurrent()

        assertFalse(viewModel.state.value.indoorDetected)
        assertEquals(ExposureStatus.RUNNING, viewModel.state.value.exposureStatus)
    }

    private fun buildViewModel(environment: MockEnvironmentContextProvider) =
        MainViewModel(
            settingsViewModel = SettingsViewModel(FakeUserPreferencesRepository()),
            environmentContextProvider = environment,
            nowMillis = { FIXED_TIME_MILLIS },
            elapsedRealtimeMillis = { dispatcher.scheduler.currentTime },
        )

    private companion object {
        const val FIXED_TIME_MILLIS = 1_800_000L
    }
}
