package com.example.uvapp.data.repository

import com.example.uvapp.data.db.UvReadingDao
import com.example.uvapp.data.db.UvReadingEntity
import com.example.uvapp.data.openmeteo.OpenMeteoApi
import com.example.uvapp.data.openmeteo.OpenMeteoHourlyDto
import com.example.uvapp.data.openmeteo.OpenMeteoResponseDto
import com.example.uvapp.domain.model.UvDataSource
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultUvRepositoryTest {
    @Test
    fun `successful refresh writes Room data and publishes network state`() =
        runBlocking {
            val api = FakeOpenMeteoApi { validResponse() }
            val dao = FakeUvReadingDao()
            val repository = repository(api = api, dao = dao, nowMillis = { 1234L })

            val result = repository.refresh(LATITUDE, LONGITUDE, force = false)
            val state = repository.observeForecast(LATITUDE, LONGITUDE).first()

            assertTrue(result.isSuccess)
            assertEquals(1, api.callCount)
            assertEquals(1, dao.getForecast(LOCATION_KEY).size)
            assertEquals(UvDataSource.NETWORK, state.source)
            assertEquals(1, state.readings.size)
            assertEquals(1234L, state.lastUpdatedMillis)
            assertFalse(state.isRefreshing)
        }

    @Test
    fun `fresh cache skips the network`() =
        runBlocking {
            val api = FakeOpenMeteoApi { validResponse() }
            val dao = FakeUvReadingDao(listOf(cachedEntity(fetchedAtMillis = 1_000L)))
            val repository = repository(api = api, dao = dao, nowMillis = { 2_000L })

            val result = repository.refresh(LATITUDE, LONGITUDE, force = false)
            val state = repository.observeForecast(LATITUDE, LONGITUDE).first()

            assertTrue(result.isSuccess)
            assertEquals(0, api.callCount)
            assertEquals(UvDataSource.CACHE, state.source)
            assertEquals(1, state.readings.size)
        }

    @Test
    fun `forced refresh still respects the one hour service limit`() =
        runBlocking {
            val api = FakeOpenMeteoApi { validResponse() }
            val dao = FakeUvReadingDao(listOf(cachedEntity(fetchedAtMillis = 1_000L)))
            val repository = repository(api = api, dao = dao, nowMillis = { 2_000L })

            val result = repository.refresh(LATITUDE, LONGITUDE, force = true)

            assertTrue(result.isSuccess)
            assertEquals(0, api.callCount)
        }

    @Test
    fun `failed refresh keeps stale Room data as cache`() =
        runBlocking {
            val api = FakeOpenMeteoApi { throw IOException("network unavailable") }
            val dao = FakeUvReadingDao(listOf(cachedEntity(fetchedAtMillis = 1_000L)))
            val repository = repository(api = api, dao = dao, nowMillis = { 7_200_000L })

            val result = repository.refresh(LATITUDE, LONGITUDE, force = false)
            val state = repository.observeForecast(LATITUDE, LONGITUDE).first()

            assertTrue(result.isFailure)
            assertEquals(1, api.callCount)
            assertEquals(UvDataSource.CACHE, state.source)
            assertEquals(1, state.readings.size)
            assertEquals("network unavailable", state.errorMessage)
            assertFalse(state.isRefreshing)
        }

    @Test
    fun `failed refresh without cache does not invent UV data`() =
        runBlocking {
            val api = FakeOpenMeteoApi { throw IOException("network unavailable") }
            val repository = repository(api = api, dao = FakeUvReadingDao())

            val result = repository.refresh(LATITUDE, LONGITUDE, force = true)
            val state = repository.observeForecast(LATITUDE, LONGITUDE).first()

            assertTrue(result.isFailure)
            assertEquals(UvDataSource.NONE, state.source)
            assertTrue(state.readings.isEmpty())
            assertEquals("network unavailable", state.errorMessage)
            assertFalse(state.isRefreshing)
        }

    private fun repository(
        api: OpenMeteoApi,
        dao: UvReadingDao,
        nowMillis: () -> Long = { 7_200_000L },
    ): DefaultUvRepository =
        DefaultUvRepository(
            api = api,
            dao = dao,
            nowMillis = nowMillis,
        )

    private class FakeOpenMeteoApi(
        private val response: () -> OpenMeteoResponseDto,
    ) : OpenMeteoApi {
        var callCount: Int = 0
            private set

        override suspend fun getUvForecast(
            latitude: Double,
            longitude: Double,
            hourly: String,
            daily: String,
            timezone: String,
            forecastDays: Int,
        ): OpenMeteoResponseDto {
            callCount += 1
            return response()
        }
    }

    private class FakeUvReadingDao(
        initialReadings: List<UvReadingEntity> = emptyList(),
    ) : UvReadingDao {
        private val readings = MutableStateFlow(initialReadings)

        override fun observeForecast(locationKey: String): Flow<List<UvReadingEntity>> =
            readings.map { entities ->
                entities
                    .filter { entity -> entity.locationKey == locationKey }
                    .sortedBy { entity -> entity.forecastTimeMillis }
            }

        override suspend fun getForecast(locationKey: String): List<UvReadingEntity> =
            readings.value
                .filter { entity -> entity.locationKey == locationKey }
                .sortedBy { entity -> entity.forecastTimeMillis }

        override suspend fun latestFetchTime(locationKey: String): Long? =
            getForecast(locationKey).maxOfOrNull { entity -> entity.fetchedAtMillis }

        override suspend fun insertReadings(readings: List<UvReadingEntity>) {
            val incomingKeys = readings.map { entity -> entity.locationKey to entity.forecastTimeMillis }.toSet()
            this.readings.update { current ->
                current.filterNot { entity ->
                    entity.locationKey to entity.forecastTimeMillis in incomingKeys
                } + readings
            }
        }

        override suspend fun deleteForecast(locationKey: String) {
            readings.update { current ->
                current.filterNot { entity -> entity.locationKey == locationKey }
            }
        }

        override suspend fun replaceForecast(
            locationKey: String,
            readings: List<UvReadingEntity>,
        ) {
            this.readings.update { current ->
                current.filterNot { entity -> entity.locationKey == locationKey } + readings
            }
        }
    }

    private companion object {
        const val LATITUDE = -37.81
        const val LONGITUDE = 144.96
        const val LOCATION_KEY = "-37.81,144.96"

        fun validResponse(): OpenMeteoResponseDto =
            OpenMeteoResponseDto(
                timezone = "Australia/Melbourne",
                hourly =
                    OpenMeteoHourlyDto(
                        time = listOf("2026-08-26T10:00"),
                        uvIndex = listOf(2.3),
                        uvIndexClearSky = listOf(2.8),
                        cloudCover = listOf(40),
                    ),
            )

        fun cachedEntity(fetchedAtMillis: Long): UvReadingEntity =
            UvReadingEntity(
                locationKey = LOCATION_KEY,
                forecastTimeMillis = 1_777_000_000_000L,
                latitude = LATITUDE,
                longitude = LONGITUDE,
                uvIndex = 2.3,
                clearSkyUvIndex = 2.8,
                cloudCoverPercent = 40,
                fetchedAtMillis = fetchedAtMillis,
            )
    }
}
