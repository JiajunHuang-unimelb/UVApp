package com.example.uvapp

import com.example.uvapp.domain.model.*
import com.example.uvapp.domain.location.*
import com.example.uvapp.domain.repository.IndoorLocationRepository
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
    private fun main() = MainViewModel(SettingsViewModel(FakeUserPreferencesRepository()), elapsedRealtimeMillis = { dispatcher.scheduler.currentTime })
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
    @Test fun `manual pause prompts once and preserves captured candidate across restoration`() {
        val repo = Memory()
        repo.data.value = IndoorLocationsData(suggestionsEnabled = true)
        val main = main()
        val alerts = mutableListOf<IndoorSuggestion?>()
        val vm = IndoorLocationsViewModel(repo, provider(), main, alerts::add, now = { 10_000 })
        dispatcher.scheduler.runCurrent()
        vm.requestSave(); dispatcher.scheduler.runCurrent()
        vm.dismiss(); dispatcher.scheduler.runCurrent()
        main.onStartExposure()
        main.onOverrideLightToggle()
        main.onLightOverride(LightContext.INDOOR)
        dispatcher.scheduler.runCurrent()
        vm.setVisible(false)
        main.onPauseExposure(); dispatcher.scheduler.runCurrent()
        val candidate = repo.data.value.pending!!
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
