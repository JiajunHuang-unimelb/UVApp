package com.example.uvapp

import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.location.CurrentLocationProvider
import com.example.uvapp.domain.location.LocationResult
import com.example.uvapp.domain.model.Coordinates
import com.example.uvapp.domain.model.LocationFix
import com.example.uvapp.domain.model.PlaceName
import com.example.uvapp.domain.model.UvDataSource
import com.example.uvapp.domain.model.UvForecastReading
import com.example.uvapp.domain.model.UvForecastState
import com.example.uvapp.domain.repository.PlaceRepository
import com.example.uvapp.domain.repository.UvRepository as ForecastUvRepository
import com.example.uvapp.viewmodel.MainViewModel
import com.example.uvapp.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
        val vm = MainViewModel(repo, SettingsViewModel(FakeUserPreferencesRepository()))

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
        val vm = MainViewModel(FakeUvRepository(), SettingsViewModel(FakeUserPreferencesRepository()))
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
        val vm = MainViewModel(FakeUvRepository(), SettingsViewModel(FakeUserPreferencesRepository()))
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
        val vm = MainViewModel(FakeUvRepository(), SettingsViewModel(FakeUserPreferencesRepository()))
        settle()
        val before = vm.state.value.remainingSeconds

        mainDispatcher.scheduler.advanceTimeBy(3_000)
        mainDispatcher.scheduler.runCurrent()

        assertEquals(before - 3, vm.state.value.remainingSeconds)
    }

    @Test
    fun `speed60x makes the countdown tick 60 seconds per tick`() {
        val vm = MainViewModel(FakeUvRepository(), SettingsViewModel(FakeUserPreferencesRepository()))
        settle()
        vm.onSpeedToggle()
        val before = vm.state.value.remainingSeconds

        mainDispatcher.scheduler.advanceTimeBy(1_000)
        mainDispatcher.scheduler.runCurrent()

        assertEquals((before - 60).coerceAtLeast(0), vm.state.value.remainingSeconds)
    }

    @Test
    fun `current location refreshes UV through the cached forecast repository`() {
        val locationProvider = FakeLocationProvider(LocationResult.Success(PRECISE_FIX))
        val forecastRepository = FakeForecastRepository()
        val placeRepository = FakePlaceRepository()
        val vm =
            MainViewModel(
                auxiliaryRepository = FakeUvRepository(currentUv = 2.0),
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
                auxiliaryRepository = FakeUvRepository(),
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
                auxiliaryRepository = FakeUvRepository(currentUv = 6.2),
                settingsViewModel = SettingsViewModel(FakeUserPreferencesRepository()),
                locationProvider = FakeLocationProvider(LocationResult.Timeout),
                forecastRepository = forecastRepository,
                nowMillis = { NOW_MILLIS },
            )
        settle()

        // init() goes straight to locate() when both repositories are configured, so
        // the auxiliary repository (and its 6.2 reading) is never consulted here —
        // the UV index stays at its untouched default.
        assertEquals(0.0, vm.state.value.uvIndex, 0.0)
        assertFalse(vm.state.value.uvAvailable)
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
                auxiliaryRepository = FakeUvRepository(),
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
            FakeUvRepository(), SettingsViewModel(FakeUserPreferencesRepository()),
            FakeLocationProvider(LocationResult.Success(PRECISE_FIX)), FakeForecastRepository(),
            nowMillis = { NOW_MILLIS },
        )
        vm.onUseCurrentLocation()
        mainDispatcher.scheduler.runCurrent()
        mainDispatcher.scheduler.advanceTimeBy(3000)
        mainDispatcher.scheduler.runCurrent()
        val remaining = vm.state.value.remainingSeconds
        vm.onRefresh()
        mainDispatcher.scheduler.runCurrent()
        assertEquals(remaining, vm.state.value.remainingSeconds)
        assertEquals(vm.state.value.totalBurnSeconds - 3, remaining)
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
            force: Boolean,
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
