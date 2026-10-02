package com.example.uvapp

import com.example.uvapp.domain.alerts.ExposureAlertGateway
import com.example.uvapp.domain.environment.AcousticContext
import com.example.uvapp.domain.environment.CameraLightContext
import com.example.uvapp.domain.environment.ExposureMonitoringController
import com.example.uvapp.domain.exposure.ExposurePauseReason
import com.example.uvapp.domain.exposure.ExposureStatus
import com.example.uvapp.domain.location.CurrentLocationProvider
import com.example.uvapp.domain.location.LocationResult
import com.example.uvapp.domain.model.Coordinates
import com.example.uvapp.domain.model.ExposureDailySummary
import com.example.uvapp.domain.model.ExposureRecord
import com.example.uvapp.domain.model.ExposureRecordStatus
import com.example.uvapp.domain.model.ExposureWeeklySummary
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.LocationFix
import com.example.uvapp.domain.model.PlaceName
import com.example.uvapp.domain.model.UvDataSource
import com.example.uvapp.domain.model.UvForecastReading
import com.example.uvapp.domain.model.UvForecastState
import com.example.uvapp.domain.repository.ExposureHistoryRepository
import com.example.uvapp.domain.repository.PlaceRepository
import com.example.uvapp.domain.repository.UvRepository as ForecastUvRepository
import com.example.uvapp.platform.environment.MockEnvironmentContextProvider
import com.example.uvapp.viewmodel.MainViewModel
import com.example.uvapp.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.time.LocalDate
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    /** Advances queued ViewModel work without draining the infinite countdown ticker. */
    private fun settle() {
        mainDispatcher.scheduler.advanceTimeBy(701)
        mainDispatcher.scheduler.runCurrent()
    }

    private fun buildLocatedViewModel() =
        MainViewModel(
            settingsViewModel = SettingsViewModel(FakeUserPreferencesRepository()),
            locationProvider = FakeLocationProvider(LocationResult.Success(PRECISE_FIX)),
            forecastRepository = FakeForecastRepository(),
            nowMillis = { NOW_MILLIS },
        )

    private fun buildEnvironmentViewModel(
        environment: MockEnvironmentContextProvider,
        alerts: FakeExposureAlertGateway = FakeExposureAlertGateway(),
    ) =
        MainViewModel(
            settingsViewModel = SettingsViewModel(FakeUserPreferencesRepository()),
            locationProvider = FakeLocationProvider(LocationResult.Success(PRECISE_FIX)),
            forecastRepository = FakeForecastRepository(),
            environmentContextProvider = environment,
            alertGateway = alerts,
            nowMillis = { NOW_MILLIS },
        )

    @Test
    fun `dev UV override recomputes the burn countdown`() {
        val vm = buildLocatedViewModel()
        settle()

        vm.onOverrideUvToggle()
        vm.onUvOverride(1.0)
        vm.onStartExposure()

        val state = vm.state.value
        assertEquals(1.0, state.displayUv, 0.0)
        assertEquals(4_000L, state.totalBurnSeconds)
        assertEquals(state.totalBurnSeconds, state.remainingSeconds)
    }

    @Test
    fun `exposure lifecycle is authoritative`() {
        val vm = buildLocatedViewModel()
        settle()

        assertEquals(ExposureStatus.NOT_STARTED, vm.state.value.exposureStatus)
        assertEquals(0L, vm.state.value.remainingSeconds)

        vm.onStartExposure()
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
        val runningRemaining = vm.state.value.remainingSeconds

        vm.onPauseExposure()
        mainDispatcher.scheduler.advanceTimeBy(3_000)
        mainDispatcher.scheduler.runCurrent()
        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureStatus)
        assertEquals(ExposurePauseReason.MANUAL, vm.state.value.pauseReason)
        assertEquals(runningRemaining, vm.state.value.remainingSeconds)

        vm.onResumeExposure()
        mainDispatcher.scheduler.advanceTimeBy(1_000)
        mainDispatcher.scheduler.runCurrent()
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
        assertEquals(null, vm.state.value.pauseReason)
        assertEquals(runningRemaining - 1L, vm.state.value.remainingSeconds)

        vm.onResetTimer()
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
        assertEquals(0.0, vm.state.value.accumulatedDoseSed, 0.0)
        assertEquals(vm.state.value.totalBurnSeconds, vm.state.value.remainingSeconds)
    }

    @Test
    fun `exposure session starts monitoring and keeps it active while paused`() {
        val monitoring = FakeExposureMonitoringController()
        val vm =
            MainViewModel(
                settingsViewModel = SettingsViewModel(FakeUserPreferencesRepository()),
                monitoringController = monitoring,
                nowMillis = { NOW_MILLIS },
            )

        assertEquals(1, monitoring.stopCount)
        vm.onStartExposure()
        assertEquals(1, monitoring.startCount)

        vm.onPauseExposure()
        assertEquals(1, monitoring.stopCount)

        vm.onResumeExposure()
        assertEquals(2, monitoring.startCount)
    }

    @Test
    fun `countdown ticks down one second per real second`() {
        val vm = buildLocatedViewModel()
        settle()
        vm.onStartExposure()
        val before = vm.state.value.remainingSeconds

        mainDispatcher.scheduler.advanceTimeBy(3_000)
        mainDispatcher.scheduler.runCurrent()

        assertEquals(before - 3, vm.state.value.remainingSeconds)
    }

    @Test
    fun `speed60x makes the countdown tick 60 seconds per tick`() {
        val vm = buildLocatedViewModel()
        settle()
        vm.onStartExposure()
        vm.onSpeedToggle()
        val before = vm.state.value.remainingSeconds

        mainDispatcher.scheduler.advanceTimeBy(1_000)
        mainDispatcher.scheduler.runCurrent()

        assertEquals((before - 60).coerceAtLeast(0), vm.state.value.remainingSeconds)
    }

    @Test
    fun `exposure completion alerts exactly once`() {
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(MockEnvironmentContextProvider(), alerts)
        settle()
        vm.onOverrideUvToggle()
        vm.onUvOverride(12.0)
        vm.onStartExposure()
        vm.onSpeedToggle()

        mainDispatcher.scheduler.advanceTimeBy(10_000)
        mainDispatcher.scheduler.runCurrent()
        assertEquals(ExposureStatus.COMPLETE, vm.state.value.exposureStatus)
        assertEquals(1, alerts.exposureLimitCount)

        mainDispatcher.scheduler.advanceTimeBy(10_000)
        mainDispatcher.scheduler.runCurrent()
        assertEquals(1, alerts.exposureLimitCount)
    }

    @Test
    fun `developer alert buttons invoke output gateway`() {
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(MockEnvironmentContextProvider(), alerts)

        vm.onTestReapplyAlert()
        vm.onTestBandWarning()

        assertEquals(1, alerts.reapplyPreviewCount)
        assertEquals(1, alerts.bandPreviewCount)
    }

    @Test
    fun `low light alone does not classify indoor or pause exposure`() {
        val environment = MockEnvironmentContextProvider()
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(environment, alerts)
        settle()
        vm.onStartExposure()

        environment.setLux(500)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(12_000)
        mainDispatcher.scheduler.runCurrent()

        assertFalse(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
        assertEquals(0, alerts.callCount)
    }

    @Test
    fun `low light with dark camera and quiet sound pauses exposure`() {
        val environment = MockEnvironmentContextProvider()
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(environment, alerts)
        settle()
        vm.onStartExposure()

        environment.setLux(500)
        environment.setCameraLuminance(10, CameraLightContext.DARK)
        environment.setAcoustic(-60.0, AcousticContext.QUIET_INDOOR_LIKELY)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(10_000)
        mainDispatcher.scheduler.runCurrent()

        assertTrue(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureStatus)
        assertEquals(ExposurePauseReason.INDOOR_DETECTED, vm.state.value.pauseReason)
        assertEquals(1, alerts.callCount)
    }

    @Test
    fun `physical proximity alone does not classify indoor or pause exposure`() {
        val environment = MockEnvironmentContextProvider()
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(environment, alerts)
        settle()
        vm.onStartExposure()

        environment.setDeviceOccluded(true)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(12_000)
        mainDispatcher.scheduler.runCurrent()

        assertFalse(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
        assertEquals(0, alerts.callCount)
    }

    @Test
    fun `stable low light and physical proximity pause then clear proximity resumes`() {
        val environment = MockEnvironmentContextProvider()
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(environment, alerts)
        settle()
        vm.onStartExposure()

        environment.setLux(500)
        environment.setDeviceOccluded(true)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(9_999)
        mainDispatcher.scheduler.runCurrent()
        assertFalse(vm.state.value.indoorDetected)

        mainDispatcher.scheduler.advanceTimeBy(1)
        mainDispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureStatus)
        assertEquals(ExposurePauseReason.INDOOR_DETECTED, vm.state.value.pauseReason)
        assertEquals(1, alerts.callCount)

        environment.setDeviceOccluded(false)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(10_000)
        mainDispatcher.scheduler.runCurrent()
        assertFalse(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
        assertEquals(1, alerts.callCount)
    }

    @Test
    fun `light bubble shows indoor range before fused indoor confirmation`() {
        val environment = MockEnvironmentContextProvider()
        val vm = buildEnvironmentViewModel(environment, FakeExposureAlertGateway())
        settle()
        vm.onStartExposure()

        environment.setLux(500)
        mainDispatcher.scheduler.runCurrent()

        assertEquals(LightContext.INDOOR, vm.state.value.lightReadingContext)
        assertEquals(LightContext.SHADE, vm.state.value.displayContext)
        assertFalse(vm.state.value.indoorDetected)
    }

    @Test
    fun `starting exposure clears and locks manual lux override`() {
        val vm = buildLocatedViewModel()
        settle()
        vm.onLuxChange(500)
        assertEquals(500, vm.state.value.displayLux)

        vm.onStartExposure()
        val sensorLux = vm.state.value.lux
        assertEquals(null, vm.state.value.luxOverride)
        assertEquals(sensorLux, vm.state.value.displayLux)

        vm.onLuxChange(50)
        assertEquals(null, vm.state.value.luxOverride)
        assertEquals(sensorLux, vm.state.value.displayLux)
    }

    @Test
    fun `location proximity alone does not classify indoor or pause exposure`() {
        val environment = MockEnvironmentContextProvider()
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(environment, alerts)
        settle()
        vm.onStartExposure()

        environment.setNearIndoorLocation(true)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(12_000)
        mainDispatcher.scheduler.runCurrent()

        assertFalse(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
        assertEquals(0, alerts.callCount)
    }

    @Test
    fun `developer location toggle supplies mock indoor proximity`() {
        val environment = MockEnvironmentContextProvider()
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(environment, alerts)
        settle()
        vm.onStartExposure()

        vm.onOverrideLightToggle()
        vm.onLightOverride(LightContext.INDOOR)
        vm.onLocationToggle()
        mainDispatcher.scheduler.advanceTimeBy(10_000)
        mainDispatcher.scheduler.runCurrent()

        assertEquals(true, vm.state.value.nearIndoorLocation)
        assertTrue(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureStatus)
        assertEquals(ExposurePauseReason.INDOOR_DETECTED, vm.state.value.pauseReason)
        assertEquals(1, alerts.callCount)
    }

    @Test
    fun `stable low light and location auto pause once then stable outdoor resumes`() {
        val environment = MockEnvironmentContextProvider()
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(environment, alerts)
        settle()
        vm.onStartExposure()

        environment.setLux(500)
        environment.setNearIndoorLocation(true)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(9_999)
        mainDispatcher.scheduler.runCurrent()
        assertFalse(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)

        mainDispatcher.scheduler.advanceTimeBy(1)
        mainDispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureStatus)
        assertEquals(ExposurePauseReason.INDOOR_DETECTED, vm.state.value.pauseReason)
        assertEquals(1, alerts.callCount)

        environment.setLux(1_500)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(12_000)
        mainDispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureStatus)

        environment.setLux(2_001)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(10_000)
        mainDispatcher.scheduler.runCurrent()
        assertFalse(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
        assertEquals(null, vm.state.value.pauseReason)
        assertEquals(1, alerts.callCount)
    }

    @Test
    fun `signal flapping restarts indoor debounce`() {
        val environment = MockEnvironmentContextProvider()
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(environment, alerts)
        settle()
        vm.onStartExposure()

        environment.setLux(500)
        environment.setNearIndoorLocation(true)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(9_000)
        mainDispatcher.scheduler.runCurrent()

        environment.setNearIndoorLocation(false)
        mainDispatcher.scheduler.runCurrent()
        environment.setNearIndoorLocation(true)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(9_999)
        mainDispatcher.scheduler.runCurrent()

        assertFalse(vm.state.value.indoorDetected)
        assertEquals(0, alerts.callCount)

        mainDispatcher.scheduler.advanceTimeBy(1)
        mainDispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureStatus)
        assertEquals(1, alerts.callCount)
    }

    @Test
    fun `outdoor transition does not resume a manually paused exposure`() {
        val environment = MockEnvironmentContextProvider()
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(environment, alerts)
        settle()
        vm.onStartExposure()
        vm.onPauseExposure()

        environment.setLux(500)
        environment.setNearIndoorLocation(true)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(10_000)
        mainDispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureStatus)
        assertEquals(ExposurePauseReason.MANUAL, vm.state.value.pauseReason)
        assertEquals(0, alerts.callCount)

        environment.setNearIndoorLocation(false)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(10_000)
        mainDispatcher.scheduler.runCurrent()

        assertFalse(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureStatus)
        assertEquals(ExposurePauseReason.MANUAL, vm.state.value.pauseReason)
        assertEquals(0, alerts.callCount)
    }

    @Test
    fun `starting exposure while already indoor pauses immediately without duplicate alert`() {
        val environment = MockEnvironmentContextProvider(initialLux = 500, initiallyNearIndoorLocation = true)
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(environment, alerts)
        settle()
        mainDispatcher.scheduler.advanceTimeBy(10_000)
        mainDispatcher.scheduler.runCurrent()
        assertTrue(vm.state.value.indoorDetected)

        vm.onStartExposure()

        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureStatus)
        assertEquals(ExposurePauseReason.INDOOR_DETECTED, vm.state.value.pauseReason)
        assertEquals(0, alerts.callCount)

        environment.setNearIndoorLocation(false)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(10_000)
        mainDispatcher.scheduler.runCurrent()
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
        assertEquals(null, vm.state.value.pauseReason)
    }

    @Test
    fun `current location refreshes UV through the cached forecast repository`() {
        val locationProvider = FakeLocationProvider(LocationResult.Success(PRECISE_FIX))
        val forecastRepository = FakeForecastRepository()
        val placeRepository = FakePlaceRepository()
        val vm =
            MainViewModel(
                settingsViewModel = SettingsViewModel(FakeUserPreferencesRepository()),
                locationProvider = locationProvider,
                forecastRepository = forecastRepository,
                placeRepository = placeRepository,
                nowMillis = { NOW_MILLIS },
            )

        // init() already triggers locate() automatically when both repositories
        // are configured — no explicit onUseCurrentLocation() call needed here.
        settle()

        assertEquals(1, locationProvider.callCount)
        assertEquals(PRECISE_FIX.latitude, forecastRepository.latitude, 0.0)
        assertEquals(PRECISE_FIX.longitude, forecastRepository.longitude, 0.0)
        assertEquals(7.1, vm.state.value.uvIndex, 0.0)
        assertTrue(vm.state.value.uvAvailable)
        assertEquals("Melbourne, City of Melbourne", vm.state.value.placeName)
        assertEquals(Coordinates(PRECISE_FIX.latitude, PRECISE_FIX.longitude), placeRepository.coordinates)
        assertEquals(PRECISE_FIX, vm.state.value.locationFix)
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `failed place lookup falls back to raw coordinates`() {
        val approximateFix = PRECISE_FIX.copy(isApproximate = true, accuracyMeters = 2_000f)
        val vm =
            MainViewModel(
                settingsViewModel = SettingsViewModel(FakeUserPreferencesRepository()),
                locationProvider = FakeLocationProvider(LocationResult.Success(approximateFix)),
                forecastRepository = FakeForecastRepository(),
                placeRepository = FailingPlaceRepository(),
                nowMillis = { NOW_MILLIS },
            )
        settle()

        assertEquals("-37.81360, 144.96310", vm.state.value.placeName)
        assertEquals(approximateFix, vm.state.value.locationFix)
    }

    @Test
    fun `location timeout surfaces an error without touching the forecast repository`() {
        val forecastRepository = FakeForecastRepository()
        val vm =
            MainViewModel(
                settingsViewModel = SettingsViewModel(FakeUserPreferencesRepository()),
                locationProvider = FakeLocationProvider(LocationResult.Timeout),
                forecastRepository = forecastRepository,
                nowMillis = { NOW_MILLIS },
            )
        settle()

        // init() goes straight to locate() when both repositories are configured, so
            // the UV index stays at its untouched default.
        assertEquals(0.0, vm.state.value.uvIndex, 0.0)
        assertTrue(checkNotNull(vm.state.value.errorMessage).contains("timed out"))
        assertFalse(vm.state.value.isLoading)
        assertEquals(0, forecastRepository.refreshCallCount)
    }

    @Test
    fun `new location request cancels an older request`() {
        val locationProvider = SlowThenFastLocationProvider()
        val forecastRepository = FakeForecastRepository()
        val vm =
            MainViewModel(
                settingsViewModel = SettingsViewModel(FakeUserPreferencesRepository()),
                locationProvider = locationProvider,
                forecastRepository = forecastRepository,
                nowMillis = { NOW_MILLIS },
            )
        // init() already fired the first (slow) request; this call must cancel it.
        settle()

        vm.onUseCurrentLocation()
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(1_001)
        mainDispatcher.scheduler.runCurrent()

        assertEquals(2, locationProvider.callCount)
        assertEquals(FAST_FIX, vm.state.value.locationFix)
        assertEquals(FAST_FIX.latitude, forecastRepository.latitude, 0.0)
    }

    @Test
    fun `refreshing unchanged UV preserves the elapsed countdown`() {
        val vm = MainViewModel(
            settingsViewModel = SettingsViewModel(FakeUserPreferencesRepository()),
            locationProvider = FakeLocationProvider(LocationResult.Success(PRECISE_FIX)),
            forecastRepository = FakeForecastRepository(),
            nowMillis = { NOW_MILLIS },
        )
        vm.onUseCurrentLocation()
        mainDispatcher.scheduler.runCurrent()
        vm.onStartExposure()
        mainDispatcher.scheduler.advanceTimeBy(3000)
        mainDispatcher.scheduler.runCurrent()
        val remaining = vm.state.value.remainingSeconds
        vm.onRefresh()
        mainDispatcher.scheduler.runCurrent()
        assertEquals(remaining, vm.state.value.remainingSeconds)
        assertEquals(vm.state.value.totalBurnSeconds - 3, remaining)
    }

    // ---- Exposure history ----------------------------------------------------

    /** Shifts the injected wall clock without moving virtual (ticker) time. */
    private var wallOffsetMillis = 0L

    private fun buildHistoryViewModel(
        history: FakeExposureHistoryRepository,
        onSaved: (suspend () -> Unit)? = null,
    ) =
        MainViewModel(
            settingsViewModel = SettingsViewModel(FakeUserPreferencesRepository()),
            locationProvider = FakeLocationProvider(LocationResult.Success(PRECISE_FIX)),
            forecastRepository = FakeForecastRepository(),
            nowMillis = { NOW_MILLIS + mainDispatcher.scheduler.currentTime + wallOffsetMillis },
            historyRepository = history,
            onHistorySaved = onSaved,
        )

    private fun tick(seconds: Int) {
        mainDispatcher.scheduler.advanceTimeBy(seconds * 1_000L)
        mainDispatcher.scheduler.runCurrent()
    }

    @Test
    fun `history session id is stable across pause and resume`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()

        vm.onStartExposure()
        tick(2)
        vm.onPauseExposure()
        tick(1)
        vm.onResumeExposure()
        tick(1)

        assertEquals(listOf(ExposureRecordStatus.PAUSED, ExposureRecordStatus.ACTIVE), history.saved.map { it.status })
        assertEquals(history.saved[0].sessionId, history.saved[1].sessionId)
        assertTrue(history.saved[1].doseSed > 0.0)
    }

    @Test
    fun `restart saves the old session as completed before a new one starts`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()

        vm.onStartExposure()
        tick(2)
        vm.onResetTimer()
        tick(1)
        vm.onPauseExposure()
        tick(1)

        assertEquals(2, history.saved.size)
        assertEquals(ExposureRecordStatus.COMPLETED, history.saved[0].status)
        assertTrue(history.saved[0].doseSed > 0.0)
        assertNotEquals(history.saved[0].sessionId, history.saved[1].sessionId)
    }

    @Test
    fun `running session is checkpointed once a minute`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()

        vm.onStartExposure()
        tick(59)
        assertTrue(history.saved.isEmpty())

        tick(2)
        assertEquals(1, history.saved.size)
        assertEquals(ExposureRecordStatus.ACTIVE, history.saved[0].status)
        assertEquals(60_000L, history.saved[0].activeDurationMillis)
    }

    @Test
    fun `reaching the dose limit is saved as completed`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()

        vm.onSpeedToggle()
        vm.onStartExposure()
        tick(20)

        assertEquals(ExposureStatus.COMPLETE, vm.state.value.exposureStatus)
        assertEquals(ExposureRecordStatus.COMPLETED, history.saved.last().status)
        assertEquals(vm.state.value.accumulatedDoseSed, history.saved.last().doseSed, 1e-9)
    }

    @Test
    fun `time after local midnight lands on the new date`() {
        val zone = ZoneId.systemDefault()
        val firstDay = LocalDate.of(2026, 10, 2)
        val midnight = firstDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        // Ticks at virtual 1s/2s/3s/4s become midnight -2s/-1s/0s/+1s.
        wallOffsetMillis = midnight - NOW_MILLIS - 3_000L
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()

        vm.onStartExposure()
        tick(3)
        vm.onPauseExposure()
        tick(1)

        val days = history.saved.single().days
        assertEquals(listOf(firstDay, firstDay.plusDays(1)), days.map { it.date })
        assertEquals(1_000L, days[0].activeDurationMillis)
        assertEquals(2_000L, days[1].activeDurationMillis)
    }

    @Test
    fun `recorded-through time never goes backwards when the wall clock does`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()

        vm.onStartExposure()
        tick(2)
        vm.onPauseExposure()
        tick(1)
        wallOffsetMillis -= 600_000L
        vm.onResumeExposure()
        tick(1)

        assertEquals(2, history.saved.size)
        assertTrue(history.saved[1].recordedThroughMillis >= history.saved[0].recordedThroughMillis)
        assertTrue(history.saved[1].days.all { it.activeDurationMillis >= 0L })
    }

    @Test
    fun `successful saves notify the widget and failed saves are not retried`() {
        val history = FakeExposureHistoryRepository()
        var notified = 0
        val vm = buildHistoryViewModel(history, onSaved = { notified++ })
        settle()

        vm.onStartExposure()
        tick(2)
        vm.onPauseExposure()
        tick(1)
        assertEquals(1, history.saveAttempts)
        assertEquals(1, notified)

        history.failSaves = true
        vm.onResumeExposure()
        tick(5)
        assertEquals(2, history.saveAttempts)
        assertEquals(1, notified)
    }

    @Test
    fun `sun log paging moves by a week and stops at the current week`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()
        val today = history.weekRequests.single()

        vm.onSunLogNextWeek()
        vm.onSunLogPreviousWeek()
        vm.onSunLogNextWeek()
        settle()

        assertEquals(listOf(today, today.minusDays(7), today), history.weekRequests)
        assertEquals(today, vm.state.value.sunLogWeek?.weekStart)
    }

    private class FakeExposureHistoryRepository : ExposureHistoryRepository {
        val saved = mutableListOf<ExposureRecord>()
        val weekRequests = mutableListOf<LocalDate>()
        var saveAttempts = 0
            private set
        var failSaves = false

        override suspend fun save(record: ExposureRecord): Result<Unit> {
            saveAttempts++
            if (failSaves) return Result.failure(IllegalStateException("write failed"))
            saved += record
            return Result.success(Unit)
        }

        override suspend fun getSession(sessionId: String): ExposureRecord? =
            saved.lastOrNull { it.sessionId == sessionId }

        override fun observeHistory(
            limit: Int,
            offset: Int,
        ): Flow<List<ExposureRecord>> = flowOf(saved.toList())

        override fun observeDaily(
            start: LocalDate,
            endExclusive: LocalDate,
        ): Flow<List<ExposureDailySummary>> = flowOf(emptyList())

        // weekStart echoes the requested date so tests can see which week is shown.
        override fun observeWeek(containingDate: LocalDate): Flow<ExposureWeeklySummary> {
            weekRequests += containingDate
            return flowOf(ExposureWeeklySummary(containingDate, emptyList()))
        }

        override suspend fun deleteSession(sessionId: String): Result<Unit> = Result.success(Unit)

        override suspend fun clearHistory(): Result<Unit> = Result.success(Unit)
    }

    private class FakeLocationProvider(
        private val result: LocationResult,
    ) : CurrentLocationProvider {
        var callCount = 0
            private set

        override suspend fun getCurrentLocation(): LocationResult {
            callCount++
            return result
        }
    }

    private class FakeExposureAlertGateway : ExposureAlertGateway {
        var callCount = 0
            private set
        var exposureLimitCount = 0
            private set
        var reapplyPreviewCount = 0
            private set
        var bandPreviewCount = 0
            private set

        override fun notifyIndoorAutoPause() {
            callCount++
        }

        override fun notifyExposureLimitReached() {
            exposureLimitCount++
        }

        override fun previewReapplyReminder() {
            reapplyPreviewCount++
        }

        override fun previewBandWarning() {
            bandPreviewCount++
        }
    }

    private class FakeExposureMonitoringController : ExposureMonitoringController {
        var startCount = 0
            private set
        var stopCount = 0
            private set

        override fun start() {
            startCount++
        }

        override fun stop() {
            stopCount++
        }
    }

    private class SlowThenFastLocationProvider : CurrentLocationProvider {
        var callCount = 0
            private set

        override suspend fun getCurrentLocation(): LocationResult {
            callCount++
            return if (callCount == 1) {
                delay(1_000)
                LocationResult.Success(PRECISE_FIX)
            } else {
                LocationResult.Success(FAST_FIX)
            }
        }
    }

    private class FakeForecastRepository : ForecastUvRepository {
        private val forecastState = MutableStateFlow(UvForecastState())
        var latitude = 0.0
            private set
        var longitude = 0.0
            private set
        var refreshCallCount = 0
            private set

        override fun observeForecast(
            latitude: Double,
            longitude: Double,
        ): Flow<UvForecastState> = forecastState

        override suspend fun refresh(
            latitude: Double,
            longitude: Double,
        ): Result<Unit> {
            this.latitude = latitude
            this.longitude = longitude
            refreshCallCount++
            forecastState.value =
                UvForecastState(
                    readings =
                        listOf(
                            UvForecastReading(
                                forecastTimeMillis = NOW_MILLIS,
                                uvIndex = 7.1,
                                clearSkyUvIndex = 7.5,
                                cloudCoverPercent = 20,
                            ),
                        ),
                    source = UvDataSource.NETWORK,
                    lastUpdatedMillis = NOW_MILLIS,
                )
            return Result.success(Unit)
        }
    }

    private class FakePlaceRepository : PlaceRepository {
        override suspend fun searchPlaces(query: String) =
            Result.success(emptyList<com.example.uvapp.domain.model.PlaceSearchResult>())

        var coordinates: Coordinates? = null
            private set

        override suspend fun reverseGeocode(coordinates: Coordinates): Result<PlaceName> {
            this.coordinates = coordinates
            return Result.success(
                PlaceName(
                    label = "Melbourne, City of Melbourne",
                    locality = "Melbourne",
                    city = "City of Melbourne",
                    state = "Victoria",
                    country = "Australia",
                    displayName = "Melbourne, City of Melbourne, Victoria, Australia",
                ),
            )
        }
    }

    private class FailingPlaceRepository : PlaceRepository {
        override suspend fun searchPlaces(query: String) =
            Result.failure<List<com.example.uvapp.domain.model.PlaceSearchResult>>(IllegalStateException("offline"))

        override suspend fun reverseGeocode(coordinates: Coordinates): Result<PlaceName> =
            Result.failure(IllegalStateException("offline"))
    }

    private companion object {
        const val NOW_MILLIS = 1_800_000L
        val PRECISE_FIX =
            LocationFix(
                latitude = -37.8136,
                longitude = 144.9631,
                accuracyMeters = 20f,
                capturedAtMillis = NOW_MILLIS,
                isApproximate = false,
                isMock = false,
            )
        val FAST_FIX =
            LocationFix(
                latitude = -33.8688,
                longitude = 151.2093,
                accuracyMeters = 12f,
                capturedAtMillis = NOW_MILLIS,
                isApproximate = false,
                isMock = false,
            )
    }
}
