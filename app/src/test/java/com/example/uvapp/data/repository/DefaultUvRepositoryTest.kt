package com.example.uvapp.data.repository

import com.example.uvapp.data.openmeteo.DefaultOpenMeteoMapper
import com.example.uvapp.data.openmeteo.OpenMeteoApi
import com.example.uvapp.data.openmeteo.OpenMeteoHourlyDto
import com.example.uvapp.data.openmeteo.OpenMeteoResponseDto
import com.example.uvapp.domain.model.UvDataSource
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultUvRepositoryTest {
    @Test
    fun `successful refresh publishes network data`() =
        runBlocking {
            val repository =
                DefaultUvRepository(
                    api = FakeOpenMeteoApi { validResponse() },
                    mapper = DefaultOpenMeteoMapper(),
                    nowMillis = { 1234L },
                )

            val result = repository.refresh(-37.81, 144.96, force = false)
            val state = repository.observeForecast(-37.81, 144.96).first()

            assertTrue(result.isSuccess)
            assertEquals(UvDataSource.NETWORK, state.source)
            assertEquals(1, state.readings.size)
            assertEquals(1234L, state.lastUpdatedMillis)
            assertFalse(state.isRefreshing)
        }

    @Test
    fun `failed refresh reports an error without inventing UV data`() =
        runBlocking {
            val repository =
                DefaultUvRepository(
                    api = FakeOpenMeteoApi { throw IOException("network unavailable") },
                    mapper = DefaultOpenMeteoMapper(),
                )

            val result = repository.refresh(-37.81, 144.96, force = false)
            val state = repository.observeForecast(-37.81, 144.96).first()

            assertTrue(result.isFailure)
            assertEquals(UvDataSource.NONE, state.source)
            assertTrue(state.readings.isEmpty())
            assertEquals("network unavailable", state.errorMessage)
            assertFalse(state.isRefreshing)
        }

    private class FakeOpenMeteoApi(
        private val response: () -> OpenMeteoResponseDto,
    ) : OpenMeteoApi {
        override suspend fun getUvForecast(
            latitude: Double,
            longitude: Double,
            hourly: String,
            daily: String,
            timezone: String,
            forecastDays: Int,
        ): OpenMeteoResponseDto = response()
    }

    private companion object {
        fun validResponse(): OpenMeteoResponseDto =
            OpenMeteoResponseDto(
                latitude = -37.81,
                longitude = 144.96,
                timezone = "Australia/Melbourne",
                hourly =
                    OpenMeteoHourlyDto(
                        time = listOf("2026-08-26T10:00"),
                        uvIndex = listOf(2.3),
                        uvIndexClearSky = listOf(2.8),
                        cloudCover = listOf(40),
                    ),
            )
    }
}
