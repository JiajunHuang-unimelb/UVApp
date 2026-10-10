package com.example.uvapp

import com.example.uvapp.domain.environment.AcousticContext
import com.example.uvapp.domain.environment.DevicePosture
import com.example.uvapp.domain.environment.EnvironmentContextProvider
import com.example.uvapp.domain.environment.StepActivity
import com.example.uvapp.domain.environment.StepCounterTracker
import com.example.uvapp.domain.model.*
import com.example.uvapp.domain.location.*
import com.example.uvapp.domain.repository.IndoorLocationRepository
import com.example.uvapp.platform.environment.MockEnvironmentContextProvider
import com.example.uvapp.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class IndoorLocationsTest {
    private val dispatcher = StandardTestDispatcher()
    private val good = LocationFix(-37.8, 144.96, 10f, 10_000, false, false)
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { Dispatchers.resetMain() }
    private class Memory : IndoorLocationRepository {
        override val data = MutableStateFlow(IndoorLocationsData())
        override suspend fun update(transform: (IndoorLocationsData) -> IndoorLocationsData) { data.value = transform(data.value) }
    }
    private fun main(environment: EnvironmentContextProvider? = null) =
        MainViewModel(
            SettingsViewModel(FakeUserPreferencesRepository()),
            environmentContextProvider = environment,
            elapsedRealtimeMillis = { dispatcher.scheduler.currentTime },
        )
    private fun provider(fix: LocationFix = good) = object : CurrentLocationProvider { override suspend fun getCurrentLocation() = LocationResult.Success(fix) }
    @Test fun `quality and distance boundaries`() {
        assertTrue(good.usableForIndoor(40_000))
        assertFalse(good.usableForIndoor(40_001))
        assertFalse(good.copy(isApproximate = true).usableForIndoor(10_000))
        assertFalse(good.copy(accuracyMeters = 51f).usableForIndoor(10_000))
        assertFalse(good.usableForIndoor(9_999))
        val place = IndoorLocation("a", "Home", good.latitude, good.longitude, 10_000)
        assertTrue(place.contains(good.latitude, good.longitude))
        assertTrue(place.contains(good.latitude + 0.0008, good.longitude))
        assertFalse(place.contains(good.latitude + 0.001, good.longitude))
    }
    @Test fun `saving is opt in and duplicate coordinates rename rather than append`() {
        val repo = Memory()
        val vm = IndoorLocationsViewModel(repo, provider(), main(), now = { 10_000 })
        dispatcher.scheduler.runCurrent()
        vm.requestSave(); dispatcher.scheduler.runCurrent()
        assertTrue(repo.data.value.locations.isEmpty())
        val id = repo.data.value.pending!!.id
        vm.setName("Home"); vm.confirm(); dispatcher.scheduler.runCurrent()
        assertEquals(id, repo.data.value.locations.single().id)
        assertNull(repo.data.value.pending)
        vm.requestSave(); dispatcher.scheduler.runCurrent()
        vm.setName("Work"); vm.confirm(); dispatcher.scheduler.runCurrent()
        assertEquals("Work", repo.data.value.locations.single().name)
        vm.delete(id); dispatcher.scheduler.runCurrent()
        assertTrue(repo.data.value.locations.isEmpty())
    }
    @Test fun `running exposure prompts once and preserves captured candidate across restoration`() {
        val repo = Memory()
        repo.data.value = IndoorLocationsData(suggestionsEnabled = true)
        val environment = MockEnvironmentContextProvider()
        val main = main(environment)
        val alerts = mutableListOf<IndoorSuggestion?>()
        val vm = IndoorLocationsViewModel(repo, provider(), main, alerts::add, now = { 10_000 })
        dispatcher.scheduler.runCurrent()
        vm.requestSave(); dispatcher.scheduler.runCurrent()
        vm.dismiss(); dispatcher.scheduler.runCurrent()
        main.onStartExposure()
        main.onOverrideLightToggle()
        main.onLightOverride(LightContext.INDOOR)
        vm.setVisible(false)
        environment.setAcoustic(-60.0, AcousticContext.QUIET_INDOOR_LIKELY)
        environment.setMotion(DevicePosture.FACE_UP, isMoving = false)
        environment.setStepActivity(0, 0, activity = StepActivity.STATIONARY)
        dispatcher.scheduler.runCurrent()
        val candidate = repo.data.value.pending!!
        assertTrue(main.state.value.exposureRunning)
        assertNull(main.state.value.pauseReason)
        assertEquals(good.latitude, candidate.latitude, 0.0)
        assertEquals(1, alerts.count { it != null })
        vm.dismiss(); dispatcher.scheduler.runCurrent()
        main.onResumeExposure(); dispatcher.scheduler.runCurrent()
        main.onPauseExposure(); dispatcher.scheduler.runCurrent()
        assertNull(repo.data.value.pending)
        assertEquals(com.example.uvapp.domain.exposure.ExposurePauseReason.MANUAL, main.state.value.pauseReason)
        repo.data.value = repo.data.value.copy(pending = candidate)
        val restored = IndoorLocationsViewModel(repo, provider(good.copy(latitude = 0.0)), main(), now = { 99_000 })
        dispatcher.scheduler.runCurrent()
        assertEquals(candidate, restored.state.value.data.pending)
    }
    @Test fun `running exposure requests a fresh fix without manually pausing`() {
        val repo = Memory()
        repo.data.value = IndoorLocationsData(suggestionsEnabled = true)
        val environment = MockEnvironmentContextProvider()
        val main = main(environment)
        var locationRequests = 0
        val freshProvider = object : CurrentLocationProvider {
            override suspend fun getCurrentLocation(): LocationResult {
                locationRequests++
                return LocationResult.Success(good)
            }
        }
        val vm = IndoorLocationsViewModel(repo, freshProvider, main, now = { 10_000 })
        dispatcher.scheduler.runCurrent()
        main.onStartExposure()
        main.onOverrideLightToggle()
        main.onLightOverride(LightContext.INDOOR)
        environment.setAcoustic(-50.0, AcousticContext.QUIET_INDOOR_LIKELY)
        environment.setMotion(DevicePosture.FACE_UP, isMoving = false)
        environment.setStepActivity(0, 0, activity = StepActivity.STATIONARY)
        dispatcher.scheduler.runCurrent()

        assertEquals(1, locationRequests)
        assertNotNull(repo.data.value.pending)
        assertTrue(main.state.value.exposureRunning)
    }
    @Test fun `failed fresh fix does not suppress a later sensor retry`() {
        val repo = Memory()
        repo.data.value = IndoorLocationsData(suggestionsEnabled = true)
        val environment = MockEnvironmentContextProvider()
        val main = main(environment)
        var locationRequests = 0
        val retryingProvider = object : CurrentLocationProvider {
            override suspend fun getCurrentLocation(): LocationResult {
                locationRequests++
                return if (locationRequests == 1) {
                    LocationResult.Success(good.copy(capturedAtMillis = 0L))
                } else {
                    LocationResult.Success(good.copy(capturedAtMillis = 40_001L))
                }
            }
        }
        val vm = IndoorLocationsViewModel(repo, retryingProvider, main, now = { 40_001L })
        dispatcher.scheduler.runCurrent()
        main.onStartExposure()
        main.onOverrideLightToggle()
        main.onLightOverride(LightContext.INDOOR)
        environment.setAcoustic(-50.0, AcousticContext.QUIET_INDOOR_LIKELY)
        environment.setMotion(DevicePosture.FACE_UP, isMoving = false)
        environment.setStepActivity(0, 0, activity = StepActivity.STATIONARY)
        dispatcher.scheduler.runCurrent()

        assertNull(repo.data.value.pending)

        dispatcher.scheduler.advanceTimeBy(3_000L)
        dispatcher.scheduler.runCurrent()
        assertEquals(1, locationRequests)

        environment.setMotion(DevicePosture.FACE_UP, isMoving = true)
        dispatcher.scheduler.runCurrent()
        environment.setMotion(DevicePosture.FACE_UP, isMoving = false)
        dispatcher.scheduler.runCurrent()

        assertEquals(2, locationRequests)
        assertNotNull(repo.data.value.pending)
    }
    @Test fun `suggestion allows conversational audio but blocks sustained loud activity`() {
        val repo = Memory()
        repo.data.value = IndoorLocationsData(suggestionsEnabled = true)
        val environment = MockEnvironmentContextProvider()
        val main = main(environment)
        val vm = IndoorLocationsViewModel(repo, provider(), main, now = { 10_000 })
        dispatcher.scheduler.runCurrent()
        vm.requestSave(); dispatcher.scheduler.runCurrent()
        vm.dismiss(); dispatcher.scheduler.runCurrent()
        main.onStartExposure()
        main.onOverrideLightToggle()
        main.onLightOverride(LightContext.INDOOR)

        environment.setAcoustic(-60.0, AcousticContext.QUIET_INDOOR_LIKELY)
        environment.setMotion(DevicePosture.FACE_UP, isMoving = true)
        environment.setStepActivity(0, 0, activity = StepActivity.STATIONARY)
        dispatcher.scheduler.runCurrent()
        main.onPauseExposure(); dispatcher.scheduler.runCurrent()
        assertNull(repo.data.value.pending)

        main.onResumeExposure()
        environment.setAcoustic(-30.0, AcousticContext.UNCERTAIN)
        environment.setMotion(DevicePosture.FACE_UP, isMoving = false)
        dispatcher.scheduler.runCurrent()
        main.onPauseExposure(); dispatcher.scheduler.runCurrent()
        assertNotNull(repo.data.value.pending)
        vm.dismiss(); dispatcher.scheduler.runCurrent()

        main.onStartExposure()
        environment.setAcoustic(-10.0, AcousticContext.ACTIVE_OUTDOOR_LIKELY)
        environment.setStepActivity(10, 10, recentSteps = 0, activity = StepActivity.STATIONARY)
        dispatcher.scheduler.runCurrent()
        main.onPauseExposure(); dispatcher.scheduler.runCurrent()
        assertNull(repo.data.value.pending)

        main.onStartExposure()
        environment.setAcoustic(-30.0, AcousticContext.UNCERTAIN)
        environment.setStepActivity(10, 10, recentSteps = 3, activity = StepActivity.WALKING)
        dispatcher.scheduler.runCurrent()
        main.onPauseExposure(); dispatcher.scheduler.runCurrent()
        assertNull(repo.data.value.pending)

        main.onStartExposure()
        val tracker = StepCounterTracker()
        tracker.update(100f, 0L)
        val incidentalSteps = tracker.update(102f, 1_000L)!!
        environment.setStepActivity(incidentalSteps.stepsSinceStart, 0, recentSteps = incidentalSteps.recentSteps, activity = incidentalSteps.activity)
        dispatcher.scheduler.runCurrent()
        main.onPauseExposure(); dispatcher.scheduler.runCurrent()
        assertNotNull(repo.data.value.pending)
    }
    @Test fun `missing microphone evidence blocks suggestion until it becomes available`() {
        val repo = Memory()
        repo.data.value = IndoorLocationsData(suggestionsEnabled = true)
        val environment = MockEnvironmentContextProvider()
        val main = main(environment)
        IndoorLocationsViewModel(repo, provider(), main, now = { 10_000L })
        dispatcher.scheduler.runCurrent()
        main.onStartExposure()
        main.onOverrideLightToggle()
        main.onLightOverride(LightContext.INDOOR)
        environment.setMotion(DevicePosture.FACE_UP, isMoving = false)
        environment.setStepActivity(0, 0, activity = StepActivity.STATIONARY)
        dispatcher.scheduler.runCurrent()

        main.onPauseExposure()
        dispatcher.scheduler.runCurrent()
        assertNull(repo.data.value.pending)

        main.onResumeExposure()
        environment.setAcoustic(-50.0, AcousticContext.QUIET_INDOOR_LIKELY)
        dispatcher.scheduler.runCurrent()
        main.onPauseExposure()
        dispatcher.scheduler.runCurrent()

        assertNotNull(repo.data.value.pending)
    }
    @Test fun `missing motion evidence blocks suggestion until stationary reading arrives`() {
        val repo = Memory()
        repo.data.value = IndoorLocationsData(suggestionsEnabled = true)
        val environment = MockEnvironmentContextProvider()
        val main = main(environment)
        IndoorLocationsViewModel(repo, provider(), main, now = { 10_000L })
        dispatcher.scheduler.runCurrent()
        main.onStartExposure()
        main.onOverrideLightToggle()
        main.onLightOverride(LightContext.INDOOR)
        environment.setAcoustic(-50.0, AcousticContext.QUIET_INDOOR_LIKELY)
        environment.setStepActivity(0, 0, activity = StepActivity.STATIONARY)
        dispatcher.scheduler.runCurrent()

        main.onPauseExposure()
        dispatcher.scheduler.runCurrent()
        assertNull(repo.data.value.pending)

        main.onResumeExposure()
        environment.setMotion(DevicePosture.FACE_UP, isMoving = false)
        dispatcher.scheduler.runCurrent()
        main.onPauseExposure()
        dispatcher.scheduler.runCurrent()

        assertNotNull(repo.data.value.pending)
    }
    @Test fun `disabled suggestions ignore otherwise eligible sensor context`() {
        val repo = Memory()
        repo.data.value = IndoorLocationsData(suggestionsEnabled = false)
        val environment = MockEnvironmentContextProvider()
        val main = main(environment)
        IndoorLocationsViewModel(repo, provider(), main, now = { 10_000L })
        dispatcher.scheduler.runCurrent()
        main.onStartExposure()
        main.onOverrideLightToggle()
        main.onLightOverride(LightContext.INDOOR)
        environment.setAcoustic(-50.0, AcousticContext.QUIET_INDOOR_LIKELY)
        environment.setMotion(DevicePosture.FACE_UP, isMoving = false)
        environment.setStepActivity(0, 0, activity = StepActivity.STATIONARY)
        dispatcher.scheduler.runCurrent()

        main.onPauseExposure()
        dispatcher.scheduler.runCurrent()

        assertNull(repo.data.value.pending)
    }
    @Test fun `enabling suggestions during eligible tracking prompts without a pause`() {
        val repo = Memory()
        val environment = MockEnvironmentContextProvider(initialLux = 500)
        val main = main(environment)
        val vm = IndoorLocationsViewModel(repo, provider(), main, now = { 10_000L })
        environment.setAcoustic(-50.0, AcousticContext.QUIET_INDOOR_LIKELY)
        environment.setMotion(DevicePosture.FACE_UP, isMoving = false)
        environment.setStepActivity(0, 0, activity = StepActivity.STATIONARY)
        dispatcher.scheduler.runCurrent()

        vm.enableSuggestions(true)
        dispatcher.scheduler.runCurrent()
        assertNull(repo.data.value.pending)

        vm.enableSuggestions(false)
        dispatcher.scheduler.runCurrent()
        main.onStartExposure()
        dispatcher.scheduler.runCurrent()
        assertNull(repo.data.value.pending)

        vm.enableSuggestions(true)
        dispatcher.scheduler.runCurrent()
        assertNotNull(repo.data.value.pending)
        assertTrue(main.state.value.exposureRunning)
    }

    @Test fun `saved location suppresses suggestion despite eligible sensors`() {
        val repo = Memory()
        repo.data.value = IndoorLocationsData(
            locations = listOf(IndoorLocation("home", "Home", good.latitude, good.longitude, 10_000L)),
            suggestionsEnabled = true,
        )
        val environment = MockEnvironmentContextProvider(initialLux = 500)
        val main = main(environment)
        IndoorLocationsViewModel(repo, provider(), main, now = { 10_000L })
        dispatcher.scheduler.runCurrent()
        main.onStartExposure()
        environment.setAcoustic(-50.0, AcousticContext.QUIET_INDOOR_LIKELY)
        environment.setMotion(DevicePosture.FACE_UP, isMoving = false)
        environment.setStepActivity(0, 0, activity = StepActivity.STATIONARY)
        dispatcher.scheduler.runCurrent()

        assertNull(repo.data.value.pending)
    }

    @Test fun `automatic indoor pause never creates a save suggestion`() {
        val repo = Memory()
        repo.data.value = IndoorLocationsData(suggestionsEnabled = true)
        val environment =
            MockEnvironmentContextProvider(
                initialLux = 500,
                initiallyNearIndoorLocation = true,
            )
        val main = main(environment)
        IndoorLocationsViewModel(
            repository = repo,
            location = provider(),
            main = main,
            now = { 10_000L },
            reportIndoorProximity = {},
        )
        dispatcher.scheduler.runCurrent()
        main.onStartExposure()

        dispatcher.scheduler.advanceTimeBy(10_000L)
        dispatcher.scheduler.runCurrent()

        assertEquals(com.example.uvapp.domain.exposure.ExposurePauseReason.INDOOR_DETECTED, main.state.value.pauseReason)
        assertNull(repo.data.value.pending)
    }

    @Test fun `unavailable step data suppresses automatic suggestions but allows explicit saving`() {
        val repo = Memory()
        repo.data.value = IndoorLocationsData(suggestionsEnabled = true)
        val environment = MockEnvironmentContextProvider()
        val main = main(environment)
        val vm = IndoorLocationsViewModel(repo, provider(), main, now = { 10_000 })
        dispatcher.scheduler.runCurrent()
        main.onStartExposure()
        main.onOverrideLightToggle()
        main.onLightOverride(LightContext.INDOOR)
        environment.setAcoustic(-30.0, AcousticContext.UNCERTAIN)
        environment.setMotion(DevicePosture.FACE_UP, isMoving = false)
        environment.setStepActivity(null, null)
        dispatcher.scheduler.runCurrent()

        main.onPauseExposure(); dispatcher.scheduler.runCurrent()
        assertNull(main.state.value.stepActivity)
        assertNull(repo.data.value.pending)

        vm.requestSave(); dispatcher.scheduler.runCurrent()
        assertNotNull(repo.data.value.pending)
    }
    @Test fun `bad location never produces candidate and proximity is unknown`() {
        val repo = Memory()
        val main = main()
        val vm = IndoorLocationsViewModel(repo, provider(good.copy(isApproximate = true)), main, now = { 10_000 })
        dispatcher.scheduler.runCurrent()
        vm.requestSave(); dispatcher.scheduler.runCurrent()
        assertNull(repo.data.value.pending)
        assertNotNull(vm.state.value.error)
        assertNull(main.state.value.nearIndoorLocation)
    }
}
