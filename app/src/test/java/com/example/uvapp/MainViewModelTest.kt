package com.example.uvapp

import com.example.uvapp.domain.alerts.ExposureAlertGateway
import com.example.uvapp.domain.environment.AcousticContext
import com.example.uvapp.domain.environment.DevicePosture
import com.example.uvapp.domain.environment.ExposureMonitoringController
import com.example.uvapp.domain.exposure.ExposureCalculator
import com.example.uvapp.domain.exposure.ExposureContext
import com.example.uvapp.domain.exposure.ExposurePauseReason
import com.example.uvapp.domain.exposure.ExposureStatus
import com.example.uvapp.domain.location.CurrentLocationProvider
import com.example.uvapp.domain.location.LocationResult
import com.example.uvapp.domain.model.Coordinates
import com.example.uvapp.domain.model.ExposureDailySummary
import com.example.uvapp.domain.model.ExposureDayTotal
import com.example.uvapp.domain.model.ExposureRecord
import com.example.uvapp.domain.model.ExposureRecordStatus
import com.example.uvapp.domain.model.ExposureWeeklySummary
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.LocationFix
import com.example.uvapp.domain.model.PlaceName
import com.example.uvapp.domain.model.PlaceSearchResult
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
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
            elapsedRealtimeMillis = { mainDispatcher.scheduler.currentTime },
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
            elapsedRealtimeMillis = { mainDispatcher.scheduler.currentTime },
        )

    @Test
    fun `dev UV override recomputes the burn countdown`() {
        val vm = buildLocatedViewModel()
        settle()

        vm.onOverrideUvToggle()
        vm.onUvOverride(1.0)
        vm.onStartExposure(); flush()

        val state = vm.state.value
        assertEquals(1.0, state.displayUv, 0.0)
        assertEquals(4_000L, state.totalBurnSeconds)
        assertEquals(state.totalBurnSeconds, state.remainingSeconds)
    }

    @Test
    fun `exposure lifecycle is authoritative`() {
        val vm = buildLocatedViewModel()
        settle()
        alignToTick()

        assertEquals(ExposureStatus.NOT_STARTED, vm.state.value.exposureStatus)
        assertEquals(0L, vm.state.value.remainingSeconds)

        vm.onStartExposure(); flush()
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

        vm.onResetSession(save = true)
        assertEquals(ExposureStatus.NOT_STARTED, vm.state.value.exposureStatus)
        assertEquals(0.0, vm.state.value.accumulatedDoseSed, 0.0)
        assertEquals(0L, vm.state.value.remainingSeconds)
        vm.onStartExposure(); flush()
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
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
                elapsedRealtimeMillis = { mainDispatcher.scheduler.currentTime },
            )

        assertEquals(1, monitoring.stopCount)
        vm.onStartExposure(); flush()
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
        alignToTick()
        vm.onStartExposure(); flush()
        val before = vm.state.value.remainingSeconds

        mainDispatcher.scheduler.advanceTimeBy(3_000)
        mainDispatcher.scheduler.runCurrent()

        assertEquals(before - 3, vm.state.value.remainingSeconds)
    }

    @Test
    fun `speed60x makes the countdown tick 60 seconds per tick`() {
        val vm = buildLocatedViewModel()
        settle()
        vm.onStartExposure(); flush()
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
        vm.onStartExposure(); flush()
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
        vm.onStartExposure(); flush()

        environment.setLux(500)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(12_000)
        mainDispatcher.scheduler.runCurrent()

        assertFalse(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
        assertEquals(0, alerts.callCount)
    }

    @Test
    fun `low light with quiet stationary motion does not pause outside a saved location`() {
        val environment = MockEnvironmentContextProvider()
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(environment, alerts)
        settle()
        vm.onStartExposure(); flush()

        environment.setLux(500)
        environment.setAcoustic(-60.0, AcousticContext.QUIET_INDOOR_LIKELY)
        environment.setMotion(DevicePosture.FACE_DOWN, isMoving = false)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(12_000)
        mainDispatcher.scheduler.runCurrent()

        assertFalse(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
        assertEquals(0, alerts.callCount)
    }

    @Test
    fun `physical proximity alone does not classify indoor or pause exposure`() {
        val environment = MockEnvironmentContextProvider()
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(environment, alerts)
        settle()
        vm.onStartExposure(); flush()

        environment.setDeviceOccluded(true)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(12_000)
        mainDispatcher.scheduler.runCurrent()

        assertFalse(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
        assertEquals(0, alerts.callCount)
    }

    @Test
    fun `low light and physical proximity do not pause outside a saved location`() {
        val environment = MockEnvironmentContextProvider()
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(environment, alerts)
        settle()
        vm.onStartExposure(); flush()

        environment.setLux(500)
        environment.setDeviceOccluded(true)
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(12_000)
        mainDispatcher.scheduler.runCurrent()
        assertFalse(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
        assertEquals(0, alerts.callCount)
    }

    @Test
    fun `covered phone uses conservative unknown exposure instead of shade`() {
        val environment =
            MockEnvironmentContextProvider(
                initialLux = 500,
                initiallyDeviceOccluded = true,
            )
        val vm = buildEnvironmentViewModel(environment)
        settle()

        vm.onStartExposure(); flush()

        val state = vm.state.value
        val conservativeTotalSeconds =
            ExposureCalculator.calculateRemainingSeconds(
                remainingDoseSed = state.doseLimitSed,
                uvIndex = state.displayUv,
                contextFactor = ExposureContext.UNKNOWN.doseRateFactor,
            )
        assertEquals(ExposureContext.UNKNOWN, state.exposureSnapshot.context)
        assertEquals(conservativeTotalSeconds, state.totalBurnSeconds)
        assertEquals(ExposureStatus.RUNNING, state.exposureStatus)
        assertFalse(state.indoorDetected)
    }

    @Test
    fun `covering and uncovering phone switches between shade and conservative exposure`() {
        val environment =
            MockEnvironmentContextProvider(
                initialLux = 8_000,
                initiallyDeviceOccluded = false,
            )
        val vm = buildEnvironmentViewModel(environment)
        settle()
        alignToTick()
        vm.onStartExposure(); flush()
        val shadeTotalSeconds = vm.state.value.totalBurnSeconds
        assertEquals(ExposureContext.SHADE, vm.state.value.exposureSnapshot.context)

        environment.setDeviceOccluded(true)
        mainDispatcher.scheduler.runCurrent()
        assertEquals(ExposureContext.SHADE, vm.state.value.exposureSnapshot.context)
        advance(2_999L)
        assertEquals(ExposureContext.SHADE, vm.state.value.exposureSnapshot.context)
        advance(1L)
        val coveredTotalSeconds = vm.state.value.totalBurnSeconds

        assertEquals(ExposureContext.UNKNOWN, vm.state.value.exposureSnapshot.context)
        assertTrue(coveredTotalSeconds < shadeTotalSeconds)

        environment.setDeviceOccluded(false)
        mainDispatcher.scheduler.runCurrent()
        assertEquals(ExposureContext.UNKNOWN, vm.state.value.exposureSnapshot.context)
        advance(9_999L)
        assertEquals(ExposureContext.UNKNOWN, vm.state.value.exposureSnapshot.context)
        advance(1L)

        assertEquals(ExposureContext.SHADE, vm.state.value.exposureSnapshot.context)
        assertTrue(vm.state.value.totalBurnSeconds > coveredTotalSeconds)
    }

    @Test
    fun `confirmed indoor context takes priority over phone occlusion`() {
        val environment =
            MockEnvironmentContextProvider(
                initialLux = 500,
                initiallyNearIndoorLocation = true,
                initiallyDeviceOccluded = false,
            )
        val vm = buildEnvironmentViewModel(environment)
        settle()
        vm.onStartExposure(); flush()
        assertEquals(ExposureContext.SHADE, vm.state.value.exposureSnapshot.context)

        mainDispatcher.scheduler.advanceTimeBy(10_000)
        mainDispatcher.scheduler.runCurrent()

        assertTrue(vm.state.value.indoorDetected)
        environment.setDeviceOccluded(true)
        flush()
        assertEquals(ExposureContext.INDOOR, vm.state.value.exposureSnapshot.context)
        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureStatus)
        assertEquals(ExposurePauseReason.INDOOR_DETECTED, vm.state.value.pauseReason)
    }

    @Test
    fun `unavailable proximity keeps ordinary shade classification`() {
        val environment =
            MockEnvironmentContextProvider(
                initialLux = 8_000,
                initiallyDeviceOccluded = null,
            )
        val vm = buildEnvironmentViewModel(environment)
        settle()

        vm.onStartExposure(); flush()

        assertEquals(ExposureContext.SHADE, vm.state.value.exposureSnapshot.context)
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
    }

    @Test
    fun `light bubble shows indoor range before fused indoor confirmation`() {
        val environment = MockEnvironmentContextProvider()
        val vm = buildEnvironmentViewModel(environment, FakeExposureAlertGateway())
        settle()
        vm.onStartExposure(); flush()

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

        vm.onStartExposure(); flush()
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
        vm.onStartExposure(); flush()

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
        vm.onStartExposure(); flush()

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
        vm.onStartExposure(); flush()

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
        vm.onStartExposure(); flush()

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
        vm.onStartExposure(); flush()
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

        vm.onStartExposure(); flush()

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
                elapsedRealtimeMillis = { mainDispatcher.scheduler.currentTime },
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
                elapsedRealtimeMillis = { mainDispatcher.scheduler.currentTime },
            )
        settle()

        assertEquals("-37.81360, 144.96310", vm.state.value.placeName)
        assertEquals(approximateFix, vm.state.value.locationFix)
    }

    private fun buildSearchViewModel(
        placeRepository: PlaceRepository,
        forecastRepository: FakeForecastRepository = FakeForecastRepository(),
    ) = MainViewModel(
        settingsViewModel = SettingsViewModel(FakeUserPreferencesRepository()),
        locationProvider = FakeLocationProvider(LocationResult.Success(PRECISE_FIX)),
        forecastRepository = forecastRepository,
        placeRepository = placeRepository,
        nowMillis = { NOW_MILLIS },
        elapsedRealtimeMillis = { mainDispatcher.scheduler.currentTime },
    )

    @Test
    fun `submitted search shows place results`() {
        val vm = buildSearchViewModel(FakePlaceRepository(listOf(CARLTON)))
        settle()

        vm.onSearchClick()
        vm.onQueryChange("Carlton")
        vm.onSearchSubmit()
        settle()

        assertEquals(listOf(CARLTON), vm.state.value.searchResults)
        assertEquals(null, vm.state.value.searchStatus)
    }

    @Test
    fun `empty or failed search shows a status line`() {
        val empty = buildSearchViewModel(FakePlaceRepository())
        settle()
        empty.onQueryChange("Nowhere")
        empty.onSearchSubmit()
        settle()
        assertEquals("No places found", empty.state.value.searchStatus)

        val failing = buildSearchViewModel(FailingPlaceRepository())
        settle()
        failing.onQueryChange("Carlton")
        failing.onSearchSubmit()
        settle()
        assertEquals("Search failed. Check your connection.", failing.state.value.searchStatus)
        assertTrue(failing.state.value.searchResults.isEmpty())
    }

    @Test
    fun `selecting a place loads its forecast with an approximate fix`() {
        val forecastRepository = FakeForecastRepository()
        val vm = buildSearchViewModel(FakePlaceRepository(listOf(CARLTON)), forecastRepository)
        settle()

        vm.onSearchClick()
        vm.onQueryChange("Carlton")
        vm.onSearchSubmit()
        settle()
        vm.onPlaceSelected(CARLTON)
        settle()

        val state = vm.state.value
        assertEquals("Carlton", state.placeName)
        assertFalse(state.showSearchDialog)
        assertEquals("", state.searchQuery)
        assertTrue(state.searchResults.isEmpty())
        assertEquals(CARLTON.coordinates.latitude, state.locationFix!!.latitude, 0.0)
        assertEquals(CARLTON.coordinates.longitude, state.locationFix!!.longitude, 0.0)
        assertTrue(state.locationFix!!.isApproximate)
        assertEquals(CARLTON.coordinates.latitude, forecastRepository.latitude, 0.0)
        assertEquals(CARLTON.coordinates.longitude, forecastRepository.longitude, 0.0)
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
                elapsedRealtimeMillis = { mainDispatcher.scheduler.currentTime },
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
                elapsedRealtimeMillis = { mainDispatcher.scheduler.currentTime },
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
            elapsedRealtimeMillis = { mainDispatcher.scheduler.currentTime },
        )
        vm.onUseCurrentLocation()
        mainDispatcher.scheduler.runCurrent()
        vm.onStartExposure(); flush()
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
            elapsedRealtimeMillis = { mainDispatcher.scheduler.currentTime },
            historyRepository = history,
            onHistorySaved = onSaved,
        )

    private fun tick(seconds: Int) = advance(seconds * 1_000L)

    /** Advances virtual time, running every ticker iteration up to and including the end. */
    private fun advance(millis: Long) {
        mainDispatcher.scheduler.advanceTimeBy(millis)
        mainDispatcher.scheduler.runCurrent()
    }

    /** Runs saves launched by the last action without moving virtual time. */
    private fun flush() = mainDispatcher.scheduler.runCurrent()

    /** Moves to the next whole virtual second, so a session starts exactly on a ticker iteration. */
    private fun alignToTick() = advance(1_000L - mainDispatcher.scheduler.currentTime % 1_000L)

    /** Shifts the wall clock so that "now" is [millisBefore] ms before [midnight]. */
    private fun wallClockBefore(midnight: Long, millisBefore: Long) {
        wallOffsetMillis = midnight - millisBefore - NOW_MILLIS - mainDispatcher.scheduler.currentTime
    }

    @Test
    fun `history session id is stable across pause and resume`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()

        vm.onStartExposure(); flush()
        tick(2)
        vm.onPauseExposure()
        tick(1)
        vm.onResumeExposure()
        tick(1)

        assertEquals(
            listOf(ExposureRecordStatus.ACTIVE, ExposureRecordStatus.PAUSED, ExposureRecordStatus.ACTIVE),
            history.saved.map { it.status },
        )
        assertEquals(1, history.saved.map { it.sessionId }.distinct().size)
        assertTrue(history.saved.last().doseSed > 0.0)
    }

    @Test
    fun `starting a session saves it as active straight away`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()

        vm.onStartExposure(); flush()
        flush()

        val saved = history.saved.single()
        assertEquals(ExposureRecordStatus.ACTIVE, saved.status)
        assertEquals(0L, saved.activeDurationMillis)
    }
    @Test
    fun `pause and resume between ticks keep the exact direct-sun time`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()

        // Virtual time is 701 ms here, so none of these steps lines up with a ticker iteration.
        vm.onStartExposure(); flush()
        advance(2_000L)
        vm.onPauseExposure()
        advance(500L)
        vm.onResumeExposure()
        advance(500L)
        vm.onResetSession(save = true)
        flush()

        val completed = history.saved.first { it.status == ExposureRecordStatus.COMPLETED }
        assertEquals(2_500L, completed.activeDurationMillis)
    }

    @Test
    fun `a light change between ticks closes the sun segment at that moment`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()

        vm.onStartExposure(); flush()
        advance(1_500L)
        vm.onOverrideLightToggle()
        vm.onLightOverride(LightContext.SHADE)
        tick(2)
        vm.onPauseExposure()
        flush()

        val saved = history.saved.last()
        assertEquals(3_500L, saved.activeDurationMillis)
        assertEquals(1_500L, saved.days.sumOf { it.directSunDurationMillis })
        assertEquals(2_000L, saved.days.sumOf { it.shadeDurationMillis })
    }
    @Test
    fun `save reset completes the old session and waits for an explicit new start`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()
    
        // First exposure session
        vm.onStartExposure(); flush()
        tick(2)
    
        vm.onResetSession(save = true)
        assertEquals(ExposureStatus.NOT_STARTED, vm.state.value.exposureStatus)
        assertEquals(null, vm.state.value.activeSessionId)
        tick(1)
        assertEquals(2, history.saved.size)
        vm.onStartExposure(); flush()
        tick(1)
    
        // Pause the new session.
        vm.onPauseExposure()
        tick(1)
    
        assertEquals(
            listOf(
                ExposureRecordStatus.ACTIVE,
                ExposureRecordStatus.COMPLETED,
                ExposureRecordStatus.ACTIVE,
                ExposureRecordStatus.PAUSED,
            ),
            history.saved.map { it.status },
        )
    
        val oldActive = history.saved[0]
        val oldCompleted = history.saved[1]
        val newActive = history.saved[2]
        val newPaused = history.saved[3]
    
        // Restart must complete the original session.
        assertEquals(oldActive.sessionId, oldCompleted.sessionId)
    
        // The completed session must contain the exposure accumulated
        // before the restart.
        assertTrue(oldCompleted.doseSed > 0.0)
        assertEquals(2_000L, oldCompleted.activeDurationMillis)
        assertEquals(1_000L, newPaused.activeDurationMillis)
    
        // Only an explicit new Start creates a new session.
        assertNotEquals(oldCompleted.sessionId, newActive.sessionId)
    
        // Pausing the new session must not create another session.
        assertEquals(newActive.sessionId, newPaused.sessionId)
    }

    @Test
    fun `running session is checkpointed once a minute`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()
        alignToTick()

        vm.onStartExposure(); flush()
        tick(59)
        assertEquals(1, history.saved.size)

        tick(1)
        assertEquals(2, history.saved.size)
        assertEquals(ExposureRecordStatus.ACTIVE, history.saved[1].status)
        assertEquals(60_000L, history.saved[1].activeDurationMillis)
    }

    @Test
    fun `reaching the dose limit is saved as completed`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()

        vm.onSpeedToggle()
        vm.onStartExposure(); flush()
        tick(20)

        assertEquals(ExposureStatus.COMPLETE, vm.state.value.exposureStatus)
        assertEquals(ExposureRecordStatus.COMPLETED, history.saved.last().status)
        assertEquals(vm.state.value.accumulatedDoseSed, history.saved.last().doseSed, 1e-9)
    }

    @Test
    fun `a session across local midnight is split between the two days`() {
        val firstDay = LocalDate.of(2026, 10, 2)
        val midnight = firstDay.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()
        alignToTick()
        wallClockBefore(midnight, 3_000L)

        // Start 3 s before midnight, pause 1 s after it; a tick lands exactly on midnight.
        vm.onStartExposure(); flush()
        tick(4)
        vm.onPauseExposure()
        flush()

        val days = history.saved.last().days
        assertEquals(listOf(firstDay, firstDay.plusDays(1)), days.map { it.date })
        assertEquals(3_000L, days[0].activeDurationMillis)
        assertEquals(1_000L, days[1].activeDurationMillis)
        assertEquals(3.0, days[0].doseSed / days[1].doseSed, 1e-9)
    }
    @Test
    fun `a session ending exactly at local midnight adds nothing to the next day`() {
        val firstDay = LocalDate.of(2026, 10, 2)
        val midnight = firstDay.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()
        alignToTick()
        wallClockBefore(midnight, 3_000L)

        vm.onStartExposure(); flush()
        tick(3)
        vm.onResetSession(save = true)
        flush()

        val completed = history.saved.first { it.status == ExposureRecordStatus.COMPLETED }
        assertEquals(listOf(firstDay), completed.days.map { it.date })
        assertEquals(3_000L, completed.days.single().activeDurationMillis)
    }
    @Test
    fun `a segment between ticks is split at midnight by time on each side`() {
        val firstDay = LocalDate.of(2026, 10, 2)
        val midnight = firstDay.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()
        alignToTick()
        wallClockBefore(midnight, 1_250L)

        // Ticks land at midnight -0.25 s and +0.75 s, so that tick segment is split 250/750 ms.
        vm.onStartExposure(); flush()
        advance(2_500L)
        vm.onPauseExposure()
        flush()

        val days = history.saved.last().days
        assertEquals(listOf(firstDay, firstDay.plusDays(1)), days.map { it.date })
        assertEquals(1_250L, days[0].activeDurationMillis)
        assertEquals(1_250L, days[1].activeDurationMillis)
    }

    @Test
    fun `recorded-through time never goes backwards when the wall clock does`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()

        vm.onStartExposure(); flush()
        tick(2)
        vm.onPauseExposure()
        tick(1)
        wallOffsetMillis -= 600_000L
        vm.onResumeExposure()
        tick(1)

        assertEquals(3, history.saved.size)
        assertTrue(history.saved[2].recordedThroughMillis >= history.saved[1].recordedThroughMillis)
        assertTrue(history.saved[2].days.all { it.activeDurationMillis >= 0L })
    }

    @Test
    fun `successful saves notify the widget and failed saves are not retried`() {
        val history = FakeExposureHistoryRepository()
        var notified = 0
        val vm = buildHistoryViewModel(history, onSaved = { notified++ })
        settle()

        vm.onStartExposure(); flush()
        tick(2)
        vm.onPauseExposure()
        tick(1)
        assertEquals(2, history.saveAttempts)
        assertEquals(2, notified)

        history.failSaves = true
        vm.onResumeExposure()
        tick(5)
        assertEquals(3, history.saveAttempts)
        assertEquals(2, notified)
    }

    @Test
    fun `only direct sun counts as time in the sun while shade still adds dose`() {
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

    @Test
    fun `history separates direct sun and shade and counts both as active time`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()

        vm.onStartExposure(); flush()
        tick(2)
        vm.onOverrideLightToggle()
        vm.onLightOverride(LightContext.SHADE)
        tick(1)
        val doseAtShadeStart = vm.state.value.accumulatedDoseSed
        tick(2)
        vm.onPauseExposure()
        tick(1)

        val saved = history.saved.last()
        // The override settles the sun segment when it happens; everything after it is shade.
        assertEquals(5_000L, saved.activeDurationMillis)
        assertEquals(2_000L, saved.days.sumOf { it.directSunDurationMillis })
        assertEquals(3_000L, saved.days.sumOf { it.shadeDurationMillis })
        assertTrue(saved.doseSed > doseAtShadeStart)
    }

    @Test
    fun `sun log numbers default to percent and switch to time`() {
        val vm = buildHistoryViewModel(FakeExposureHistoryRepository())
        settle()
        assertFalse(vm.state.value.sunLogShowsTime)

        vm.onSunLogShowTime(true)
        vm.onTabSelected(com.example.uvapp.viewmodel.Tab.HOME)
        assertTrue(vm.state.value.sunLogShowsTime)
    }

    @Test
    fun `save settles the final fractional second using elapsed time rather than epoch time`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()
        vm.onStartExposure(); flush()
        val startedAt = NOW_MILLIS + mainDispatcher.scheduler.currentTime
        advance(437L)
        vm.onResetSession(save = true)
        flush()

        val completed = history.saved.last()
        assertEquals(ExposureRecordStatus.COMPLETED, completed.status)
        assertEquals(startedAt, completed.startedAtMillis)
        assertEquals(startedAt + 437L, completed.recordedThroughMillis)
        assertEquals(437L, completed.activeDurationMillis)
        // UVI 7.1 -> 0.001775 SED/s, for exactly 0.437 seconds in direct sun.
        assertEquals(0.001775 * 0.437, completed.doseSed, 1e-10)
        assertEquals(ExposureStatus.NOT_STARTED, vm.state.value.exposureSnapshot.status)
        assertEquals(0L, vm.state.value.exposureSnapshot.activeDurationMillis)
        assertEquals(0.0, vm.state.value.exposureSnapshot.accumulatedDoseSed, 0.0)
    }

    @Test
    fun `active metadata and snapshot match the persisted session across pause and resume`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()
        assertEquals(null, vm.state.value.activeSessionId)
        vm.onStartExposure(); flush()
        flush()
        val first = history.saved.single()
        val generation = vm.state.value.exposureSessionId
        assertEquals(first.sessionId, vm.state.value.activeSessionId)
        assertEquals(first.startedAtMillis, vm.state.value.activeSessionStartedAt)
        assertEquals(first.zoneId, vm.state.value.activeSessionZoneId)
        advance(1_250L)
        vm.onPauseExposure()
        flush()
        val state = vm.state.value
        assertEquals(ExposureStatus.PAUSED, state.exposureSnapshot.status)
        assertEquals(state.accumulatedDoseSed, state.exposureSnapshot.accumulatedDoseSed, 0.0)
        assertEquals(1_250L, state.exposureSnapshot.activeDurationMillis)
        assertEquals(state.exposureSnapshot.accumulatedDoseSed, history.saved.last().doseSed, 1e-10)
        vm.onResumeExposure()
        assertEquals(generation, vm.state.value.exposureSessionId)
        assertEquals(first.sessionId, vm.state.value.activeSessionId)
    }

    @Test
    fun `discard deletes checkpoints and notifies history consumers`() {
        val history = FakeExposureHistoryRepository()
        var notified = 0
        val vm = buildHistoryViewModel(history, onSaved = { notified++ })
        settle()
        vm.onStartExposure(); flush()
        advance(1_250L)
        vm.onPauseExposure()
        flush()
        val id = history.saved.first().sessionId
        val before = notified
        assertTrue(history.records.containsKey(id))
        vm.onResetSession(save = false)
        flush()
        assertEquals(listOf(id), history.deletedIds)
        assertTrue(history.records.isEmpty())
        assertEquals(before + 1, notified)
        assertEquals(ExposureStatus.NOT_STARTED, vm.state.value.exposureStatus)
        assertEquals(null, vm.state.value.activeSessionId)
        assertEquals(null, vm.state.value.activeSessionStartedAt)
        assertEquals(null, vm.state.value.activeSessionZoneId)
        assertEquals(null, vm.state.value.pauseReason)
    }

    @Test
    fun `queued saves cannot resurrect a discarded session or delete the next session`() {
        val history = FakeExposureHistoryRepository().apply { saveDelayMillis = 2_000L }
        val vm = buildHistoryViewModel(history)
        settle()
        vm.onStartExposure(); flush()
        val oldId = vm.state.value.activeSessionId
        advance(100L) // First save is suspended while holding the history mutex.
        vm.onPauseExposure() // Queue another checkpoint.
        vm.onResetSession(save = false)
        vm.onStartExposure(); flush()
        assertTrue(vm.state.value.exposureStarting)
        assertEquals(null, vm.state.value.activeSessionId)
        advance(6_000L)
        val newId = vm.state.value.activeSessionId
        assertNotEquals(null, newId)
        assertNotEquals(oldId, newId)
        assertEquals(listOf(oldId), history.deletedIds)
        assertEquals(setOf(newId), history.records.keys)
        assertTrue(history.saved.none { it.sessionId == oldId && it.status == ExposureRecordStatus.COMPLETED })
    }

    @Test
    fun `repeated reset and stale resume leave an idle session untouched`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()
        vm.onResetSession(save = true)
        vm.onResumeExposure()
        assertEquals(0, vm.state.value.exposureSessionId)
        vm.onStartExposure(); flush()
        advance(250L)
        vm.onResetSession(save = false)
        flush()
        val generation = vm.state.value.exposureSessionId
        val saves = history.saveAttempts
        vm.onResetSession(save = true)
        vm.onResetSession(save = false)
        vm.onResumeExposure()
        flush()
        assertEquals(generation, vm.state.value.exposureSessionId)
        assertEquals(saves, history.saveAttempts)
        assertEquals(1, history.deletedIds.size)
        assertEquals(ExposureStatus.NOT_STARTED, vm.state.value.exposureStatus)
        assertEquals(null, vm.state.value.activeSessionId)
    }

    @Test
    fun `duplicate start preserves accumulated exposure and session identity`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()
        vm.onStartExposure(); flush()
        advance(1_500L)
        val before = vm.state.value
        vm.onStartExposure(); flush()
        assertEquals(before.activeSessionId, vm.state.value.activeSessionId)
        assertEquals(before.exposureSessionId, vm.state.value.exposureSessionId)
        assertEquals(before.accumulatedDoseSed, vm.state.value.accumulatedDoseSed, 0.0)
        vm.onResetSession(save = true)
        flush()
        assertEquals(1_500L, history.saved.last().activeDurationMillis)
    }

    @Test
    fun `reset indoors waits for start and leaving indoors does not resume an idle session`() {
        val environment = MockEnvironmentContextProvider(initialLux = 500, initiallyNearIndoorLocation = true)
        val vm = buildEnvironmentViewModel(environment)
        settle()
        advance(10_000L)
        vm.onStartExposure(); flush()
        assertEquals(ExposurePauseReason.INDOOR_DETECTED, vm.state.value.pauseReason)
        vm.onResetSession(save = true)
        assertEquals(ExposureStatus.NOT_STARTED, vm.state.value.exposureStatus)
        assertEquals(null, vm.state.value.pauseReason)
        environment.setNearIndoorLocation(false)
        flush()
        advance(10_000L)
        assertFalse(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.NOT_STARTED, vm.state.value.exposureStatus)
        vm.onStartExposure(); flush()
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
    }

    @Test
    fun `new start after indoor reset is paused without an extra indoor alert`() {
        val environment = MockEnvironmentContextProvider(initialLux = 500, initiallyNearIndoorLocation = true)
        val alerts = FakeExposureAlertGateway()
        val vm = buildEnvironmentViewModel(environment, alerts)
        settle()
        vm.onStartExposure(); flush()
        advance(10_000L)
        assertEquals(1, alerts.callCount)
        vm.onResetSession(save = false)
        vm.onStartExposure(); flush()
        advance(2_000L)
        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureSnapshot.status)
        assertEquals(ExposurePauseReason.INDOOR_DETECTED, vm.state.value.pauseReason)
        assertEquals(0.0, vm.state.value.accumulatedDoseSed, 0.0)
        assertEquals(1, alerts.callCount)
    }

    @Test
    fun `motion override cancels indoor entry and clearing it requires a fresh debounce`() {
        val environment = MockEnvironmentContextProvider(initialLux = 500, initiallyNearIndoorLocation = true)
        val vm = buildEnvironmentViewModel(environment)
        settle()
        vm.onStartExposure(); flush()
        advance(5_000L)
        vm.onActiveToggle()
        assertEquals(ExposureContext.UNKNOWN, vm.state.value.exposureSnapshot.context)
        advance(2_000L)
        vm.onActiveToggle()
        advance(9_999L)
        assertFalse(vm.state.value.indoorDetected)
        advance(1L)
        assertTrue(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureStatus)
    }

    @Test
    fun `sound override cancels indoor entry and clearing it requires a fresh debounce`() {
        val environment = MockEnvironmentContextProvider(initialLux = 500, initiallyNearIndoorLocation = true)
        val vm = buildEnvironmentViewModel(environment)
        settle()
        vm.onStartExposure(); flush()
        advance(5_000L)
        vm.onAudioToggle()
        assertEquals(ExposureContext.UNKNOWN, vm.state.value.exposureSnapshot.context)
        advance(2_000L)
        vm.onAudioToggle()
        advance(9_999L)
        assertFalse(vm.state.value.indoorDetected)
        advance(1L)
        assertTrue(vm.state.value.indoorDetected)
    }

    @Test
    fun `covered bright reading does not resume a confirmed indoor session`() {
        val environment = MockEnvironmentContextProvider(initialLux = 500, initiallyNearIndoorLocation = true)
        val vm = buildEnvironmentViewModel(environment)
        settle()
        vm.onStartExposure(); flush()
        advance(10_000L)
        environment.setDeviceOccluded(true)
        environment.setLux(100_000)
        flush()
        advance(11_000L)
        assertTrue(vm.state.value.indoorDetected)
        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureStatus)
        environment.setNearIndoorLocation(false)
        flush()
        advance(10_000L)
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureStatus)
        assertEquals(ExposureContext.UNKNOWN, vm.state.value.exposureSnapshot.context)
    }

    @Test
    fun `sensor update publishes a newly stable context between ticker callbacks`() {
        val environment = MockEnvironmentContextProvider(initialLux = 8_000, initiallyDeviceOccluded = false)
        val vm = buildEnvironmentViewModel(environment)
        settle() // Time 701, not aligned with the periodic tick.
        vm.onStartExposure(); flush()
        environment.setDeviceOccluded(true)
        flush()
        advance(3_100L) // The 3 s candidate is ready, but the next tick is still pending.
        assertEquals(ExposureContext.SHADE, vm.state.value.exposureSnapshot.context)
        environment.setLux(7_999)
        flush()
        assertEquals(ExposureContext.UNKNOWN, vm.state.value.exposureSnapshot.context)
        assertEquals(ExposureStatus.RUNNING, vm.state.value.exposureSnapshot.status)
    }

    private fun recordedDose(
        id: String,
        date: LocalDate,
        dose: Double,
        status: ExposureRecordStatus = ExposureRecordStatus.COMPLETED,
    ): ExposureRecord {
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return ExposureRecord(
            sessionId = id, startedAtMillis = start, recordedThroughMillis = start + 60_000L,
            zoneId = ZoneId.systemDefault().id, status = status,
            days = listOf(ExposureDayTotal(date, 60_000L, 60_000L, 0L, 0L, dose)),
        )
    }

    private fun historyToday(): LocalDate =
        java.time.Instant.ofEpochMilli(NOW_MILLIS + mainDispatcher.scheduler.currentTime + wallOffsetMillis)
            .atZone(ZoneId.systemDefault()).toLocalDate()

    private fun useUvEight(vm: MainViewModel) {
        vm.onOverrideUvToggle()
        vm.onUvOverride(8.0)
    }

    @Test
    fun `start subtracts all today's saved dose but not yesterday or tomorrow`() {
        val history = FakeExposureHistoryRepository()
        val today = historyToday()
        history.seed(recordedDose("completed", today, 0.2))
        history.seed(recordedDose("paused", today, 0.1, ExposureRecordStatus.PAUSED))
        history.seed(recordedDose("checkpoint", today, 0.1, ExposureRecordStatus.ACTIVE))
        history.seed(recordedDose("yesterday", today.minusDays(1), 0.9))
        history.seed(recordedDose("tomorrow", today.plusDays(1), 0.9))
        val vm = buildHistoryViewModel(history)
        settle()
        useUvEight(vm)
        vm.onSunLogPreviousWeek()
        vm.onStartExposure(); flush()
        assertEquals(300L, vm.state.value.remainingSeconds)
        assertEquals(500L, vm.state.value.totalBurnSeconds)
        assertEquals(0.6, vm.state.value.remainingDoseSed, 1e-9)
        assertEquals(0.4, vm.state.value.exposureSnapshot.dailyAccumulatedDoseSed!!, 1e-9)
        assertEquals(0.0, vm.state.value.accumulatedDoseSed, 0.0)
        assertEquals(0.0, history.saved.last().doseSed, 0.0)
    }

    @Test
    fun `save then start consumes remaining daily allowance without copying old dose into history`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()
        useUvEight(vm)
        vm.onStartExposure(); flush()
        advance(60_000L)
        vm.onResetSession(save = true)
        vm.onStartExposure(); flush()
        assertEquals(440L, vm.state.value.remainingSeconds)
        assertEquals(0.0, vm.state.value.accumulatedDoseSed, 0.0)
        advance(30_000L)
        vm.onResetSession(save = true); flush()
        assertEquals(2, history.records.size)
        assertEquals(0.18, history.records.values.sumOf { it.doseSed }, 1e-9)
        assertEquals(listOf(0.06, 0.12), history.records.values.map { it.doseSed }.sorted().map { kotlin.math.round(it * 100) / 100 })
    }

    @Test
    fun `current session checkpoints are excluded from stored daily allowance`() {
        val history = FakeExposureHistoryRepository()
        history.seed(recordedDose("earlier", historyToday(), 0.4))
        val vm = buildHistoryViewModel(history)
        settle()
        useUvEight(vm)
        vm.onStartExposure(); flush()
        advance(60_000L)
        vm.onPauseExposure(); flush()
        assertEquals(0.12, history.saved.last().doseSed, 1e-9)
        assertEquals(0.52, vm.state.value.exposureSnapshot.dailyAccumulatedDoseSed!!, 1e-9)
        assertEquals(240L, vm.state.value.remainingSeconds)
        vm.onResumeExposure(); flush()
        assertEquals(240L, vm.state.value.remainingSeconds)
        advance(10_000L)
        vm.onPauseExposure(); flush()
        assertEquals(230L, vm.state.value.remainingSeconds)
    }

    @Test
    fun `new viewmodel reads persisted exposure from an earlier app session`() {
        val history = FakeExposureHistoryRepository()
        val first = buildHistoryViewModel(history)
        settle()
        useUvEight(first)
        first.onStartExposure(); flush()
        advance(60_000L)
        first.onResetSession(save = true); flush()
        val reopened = buildHistoryViewModel(history)
        settle()
        useUvEight(reopened)
        reopened.onStartExposure(); flush()
        assertEquals(440L, reopened.state.value.remainingSeconds)
    }

    @Test
    fun `next start waits for pending save before reading daily allowance`() {
        val history = FakeExposureHistoryRepository()
        val vm = buildHistoryViewModel(history)
        settle()
        useUvEight(vm)
        vm.onStartExposure(); flush()
        advance(10_000L)
        history.saveDelayMillis = 2_000L
        vm.onResetSession(save = true)
        vm.onStartExposure(); flush()
        assertTrue(vm.state.value.exposureStarting)
        assertFalse(vm.state.value.exposureStarted)
        advance(1_999L)
        assertFalse(vm.state.value.exposureStarted)
        advance(1L)
        assertFalse(vm.state.value.exposureStarting)
        assertEquals(490L, vm.state.value.remainingSeconds)
        assertEquals(0L, vm.state.value.exposureSnapshot.activeDurationMillis)
    }

    @Test
    fun `discard removes only current session from next daily allowance`() {
        val history = FakeExposureHistoryRepository()
        history.seed(recordedDose("earlier", historyToday(), 0.4))
        val vm = buildHistoryViewModel(history)
        settle()
        useUvEight(vm)
        vm.onStartExposure(); flush()
        advance(10_000L)
        vm.onPauseExposure(); flush()
        assertEquals(290L, vm.state.value.remainingSeconds)
        vm.onResetSession(save = false)
        vm.onStartExposure(); flush()
        assertEquals(300L, vm.state.value.remainingSeconds)
        assertEquals(0.4, history.records.values.sumOf { it.doseSed }, 1e-9)
    }

    @Test
    fun `saved daily dose at the limit starts completed without counting another interval`() {
        val history = FakeExposureHistoryRepository()
        history.seed(recordedDose("earlier", historyToday(), 1.0))
        val vm = buildHistoryViewModel(history)
        settle()
        useUvEight(vm)
        vm.onStartExposure(); flush()
        assertEquals(ExposureStatus.COMPLETE, vm.state.value.exposureStatus)
        assertEquals(0L, vm.state.value.remainingSeconds)
        advance(60_000L)
        assertEquals(0.0, vm.state.value.accumulatedDoseSed, 0.0)
    }

    @Test
    fun `small remaining daily allowance completes before this session reaches its own full limit`() {
        val history = FakeExposureHistoryRepository()
        history.seed(recordedDose("earlier", historyToday(), 0.995))
        val vm = buildHistoryViewModel(history)
        settle()
        alignToTick()
        useUvEight(vm)
        vm.onStartExposure(); flush()
        advance(3_000L)
        assertEquals(ExposureStatus.COMPLETE, vm.state.value.exposureStatus)
        assertEquals(0L, vm.state.value.remainingSeconds)
        assertEquals(0.006, vm.state.value.accumulatedDoseSed, 1e-9)
        advance(10_000L)
        assertEquals(0.006, vm.state.value.accumulatedDoseSed, 1e-9)
    }

    @Test
    fun `new calendar day restores allowance and keeps yesterday's history`() {
        val history = FakeExposureHistoryRepository()
        history.seed(recordedDose("yesterday", historyToday(), 0.4))
        val vm = buildHistoryViewModel(history)
        settle()
        useUvEight(vm)
        vm.onStartExposure(); flush()
        assertEquals(300L, vm.state.value.remainingSeconds)
        vm.onResetSession(save = true); flush()
        wallOffsetMillis += 86_400_000L
        vm.onStartExposure(); flush()
        assertEquals(500L, vm.state.value.remainingSeconds)
        assertEquals(0.4, history.records["yesterday"]!!.doseSed, 0.0)
    }

    @Test
    fun `session spanning midnight subtracts only today's part of live and saved exposure`() {
        val day = LocalDate.of(2026, 10, 11)
        val midnight = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val history = FakeExposureHistoryRepository()
        history.seed(recordedDose("day-one", day, 0.4))
        history.seed(recordedDose("day-two", day.plusDays(1), 0.2))
        val vm = buildHistoryViewModel(history)
        settle()
        alignToTick()
        wallClockBefore(midnight, 1_250L)
        useUvEight(vm)
        vm.onStartExposure(); flush()
        assertEquals(300L, vm.state.value.remainingSeconds)
        advance(2_500L)
        vm.onPauseExposure(); flush()
        assertEquals(0.005, vm.state.value.accumulatedDoseSed, 1e-9)
        assertEquals(0.2025, vm.state.value.exposureSnapshot.dailyAccumulatedDoseSed!!, 1e-9)
        assertEquals(399L, vm.state.value.remainingSeconds)
        assertEquals(listOf(day, day.plusDays(1)), history.saved.last().days.map { it.date })
        vm.onResetSession(save = true)
        vm.onStartExposure(); flush()
        assertEquals(399L, vm.state.value.remainingSeconds)
    }

    @Test
    fun `midnight loading hides allowance and preserves manual pause`() {
        val day = LocalDate.of(2026, 10, 11)
        val midnight = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val history = FakeExposureHistoryRepository()
        history.seed(recordedDose("next-day", day.plusDays(1), 0.3))
        val vm = buildHistoryViewModel(history)
        settle()
        alignToTick()
        wallClockBefore(midnight, 1_000L)
        useUvEight(vm)
        vm.onStartExposure(); flush()
        history.dailyReadDelayMillis = 1_000L
        advance(1_000L)
        assertTrue(vm.state.value.dailyDoseLoading)
        assertEquals(Long.MAX_VALUE, vm.state.value.remainingSeconds)
        advance(500L)
        vm.onPauseExposure(); flush()
        advance(500L)
        assertFalse(vm.state.value.dailyDoseLoading)
        assertEquals(ExposureStatus.PAUSED, vm.state.value.exposureStatus)
        assertEquals(ExposurePauseReason.MANUAL, vm.state.value.pauseReason)
        assertEquals(0.301, vm.state.value.exposureSnapshot.dailyAccumulatedDoseSed!!, 1e-9)
    }

    @Test
    fun `history loading cannot start tracking early or create duplicate sessions`() {
        val history = FakeExposureHistoryRepository().apply { dailyReadDelayMillis = 2_000L }
        history.seed(recordedDose("earlier", historyToday(), 0.4))
        val vm = buildHistoryViewModel(history)
        settle()
        useUvEight(vm)
        vm.onStartExposure()
        vm.onStartExposure(); flush()
        assertTrue(vm.state.value.exposureStarting)
        assertEquals(ExposureStatus.NOT_STARTED, vm.state.value.exposureStatus)
        advance(1_999L)
        assertEquals(0L, vm.state.value.exposureSessionId)
        advance(1L)
        assertEquals(1L, vm.state.value.exposureSessionId)
        assertEquals(300L, vm.state.value.remainingSeconds)
        assertEquals(0.0, vm.state.value.accumulatedDoseSed, 0.0)
    }

    @Test
    fun `cancelled history load cannot start later or clear another request's loading state`() {
        val history = FakeExposureHistoryRepository().apply { dailyReadDelayMillis = 2_000L }
        val vm = buildHistoryViewModel(history)
        settle()
        vm.onStartExposure(); flush()
        advance(500L)
        vm.onResetSession(save = false)
        vm.onStartExposure(); flush()
        assertTrue(vm.state.value.exposureStarting)
        advance(1_500L)
        assertFalse(vm.state.value.exposureStarted)
        assertTrue(vm.state.value.exposureStarting)
        advance(500L)
        assertEquals(1L, vm.state.value.exposureSessionId)
    }

    @Test
    fun `failed history read blocks start until retry instead of assuming zero dose`() {
        val history = FakeExposureHistoryRepository().apply { failDailyReads = true }
        history.seed(recordedDose("earlier", historyToday(), 0.4))
        val vm = buildHistoryViewModel(history)
        settle()
        useUvEight(vm)
        vm.onStartExposure(); flush()
        assertFalse(vm.state.value.exposureStarted)
        assertFalse(vm.state.value.exposureStarting)
        assertTrue(vm.state.value.dailyDoseError != null)
        assertTrue(history.saved.isEmpty())
        history.failDailyReads = false
        vm.onStartExposure(); flush()
        assertEquals(300L, vm.state.value.remainingSeconds)
        assertEquals(null, vm.state.value.dailyDoseError)
    }

    @Test
    fun `other records update today's allowance without changing this session dose`() {
        val history = FakeExposureHistoryRepository()
        history.seed(recordedDose("earlier", historyToday(), 0.2))
        val vm = buildHistoryViewModel(history)
        settle()
        useUvEight(vm)
        vm.onStartExposure(); flush()
        advance(10_000L)
        vm.onPauseExposure(); flush()
        assertEquals(390L, vm.state.value.remainingSeconds)
        history.seed(recordedDose("earlier", historyToday(), 0.4))
        flush()
        assertEquals(290L, vm.state.value.remainingSeconds)
        assertEquals(0.02, vm.state.value.accumulatedDoseSed, 1e-9)
        assertEquals(ExposurePauseReason.MANUAL, vm.state.value.pauseReason)
    }

    @Test
    fun `failed new-day history read hides allowance until retry and keeps manual pause`() {
        val day = LocalDate.of(2026, 10, 11)
        val midnight = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val history = FakeExposureHistoryRepository()
        history.seed(recordedDose("next-day", day.plusDays(1), 0.4))
        val vm = buildHistoryViewModel(history)
        settle()
        alignToTick()
        wallClockBefore(midnight, 1_000L)
        useUvEight(vm)
        vm.onStartExposure(); flush()
        vm.onPauseExposure(); flush()
        history.failDailyReads = true
        advance(1_000L)
        assertTrue(vm.state.value.dailyDoseError != null)
        assertEquals(Long.MAX_VALUE, vm.state.value.remainingSeconds)
        assertEquals(ExposurePauseReason.MANUAL, vm.state.value.pauseReason)
        history.failDailyReads = false
        vm.onRetryDailyDose(); flush()
        assertEquals(null, vm.state.value.dailyDoseError)
        assertEquals(300L, vm.state.value.remainingSeconds)
        assertEquals(ExposurePauseReason.MANUAL, vm.state.value.pauseReason)
    }

    private class FakeExposureHistoryRepository : ExposureHistoryRepository {
        val saved = mutableListOf<ExposureRecord>()
        val records = mutableMapOf<String, ExposureRecord>()
        val deletedIds = mutableListOf<String>()
        val weekRequests = mutableListOf<LocalDate>()
        var saveAttempts = 0
            private set
        var failSaves = false
        var saveDelayMillis = 0L
        var dailyReadDelayMillis = 0L
        var failDailyReads = false
        private val recordChanges = MutableStateFlow(0L)

        fun seed(record: ExposureRecord) {
            records[record.sessionId] = record
            recordChanges.value++
        }

        override suspend fun save(record: ExposureRecord): Result<Unit> {
            saveAttempts++
            delay(saveDelayMillis)
            if (failSaves) return Result.failure(IllegalStateException("write failed"))
            saved += record
            seed(record)
            return Result.success(Unit)
        }

        override suspend fun getSession(sessionId: String): ExposureRecord? =
            records[sessionId]

        override fun observeHistory(
            limit: Int,
            offset: Int,
        ): Flow<List<ExposureRecord>> = flowOf(records.values.toList())

        override fun observeDaily(
            start: LocalDate,
            endExclusive: LocalDate,
            excludingSessionId: String?,
        ): Flow<List<ExposureDailySummary>> = flow {
            delay(dailyReadDelayMillis)
            if (failDailyReads) error("History unavailable")
            emitAll(recordChanges.map {
                records.values.filter { it.sessionId != excludingSessionId }
                    .flatMap { it.days }
                    .filter { it.date >= start && it.date < endExclusive }
                    .groupBy { it.date }
                    .map { (date, days) -> ExposureDailySummary(date = date, doseSed = days.sumOf { it.doseSed }) }
            })
        }

        // weekStart echoes the requested date so tests can see which week is shown.
        override fun observeWeek(containingDate: LocalDate): Flow<ExposureWeeklySummary> {
            weekRequests += containingDate
            return flowOf(ExposureWeeklySummary(containingDate, emptyList()))
        }

        override suspend fun deleteSession(sessionId: String): Result<Unit> {
            deletedIds += sessionId
            records.remove(sessionId)
            recordChanges.value++
            return Result.success(Unit)
        }

        override suspend fun clearHistory(): Result<Unit> {
            records.clear()
            recordChanges.value++
            return Result.success(Unit)
        }
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

    private class FakePlaceRepository(
        private val searchResults: List<PlaceSearchResult> = emptyList(),
    ) : PlaceRepository {
        override suspend fun searchPlaces(query: String) = Result.success(searchResults)

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
        const val NOW_MILLIS = 1_790_000_000_000L
        val CARLTON =
            PlaceSearchResult(
                name = "Carlton",
                displayName = "Carlton, Melbourne, City of Melbourne, Victoria, 3053, Australia",
                coordinates = Coordinates(latitude = -37.8001, longitude = 144.9671),
            )
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
