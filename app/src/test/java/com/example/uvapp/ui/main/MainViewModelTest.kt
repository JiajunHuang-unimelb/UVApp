package com.example.uvapp.ui.main

import com.example.uvapp.domain.location.CurrentLocationProvider
import com.example.uvapp.domain.model.Coordinates
import com.example.uvapp.domain.model.UvReading
import com.example.uvapp.domain.repository.CurrentUvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `refresh requests UV for current device coordinates`() =
        runTest {
            val repository = RecordingUvRepository()
            val viewModel =
                MainViewModel(
                    locationProvider = FakeLocationProvider,
                    repository = repository,
                )

            viewModel.refresh()

            assertEquals(-33.8688, repository.latitude, 0.0)
            assertEquals(151.2093, repository.longitude, 0.0)
            assertEquals(6.4, checkNotNull(viewModel.uiState.value.uvIndex), 0.0)
            assertEquals("2026-08-26T12:00", viewModel.uiState.value.observedAt)
        }

    private object FakeLocationProvider : CurrentLocationProvider {
        override suspend fun getCurrentLocation(): Coordinates =
            Coordinates(latitude = -33.8688, longitude = 151.2093)
    }

    private class RecordingUvRepository : CurrentUvRepository {
        var latitude = 0.0
        var longitude = 0.0

        override suspend fun getCurrentUv(
            latitude: Double,
            longitude: Double,
        ): UvReading {
            this.latitude = latitude
            this.longitude = longitude
            return UvReading(index = 6.4, observedAt = "2026-08-26T12:00")
        }
    }
}
