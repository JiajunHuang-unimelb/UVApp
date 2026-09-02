package com.example.uvapp.data.openmeteo

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenMeteoCurrentUvRepositoryTest {
    @Test
    fun `Open-Meteo response parses and maps to domain reading`() {
        val json = checkNotNull(javaClass.classLoader?.getResource("open_meteo_current_uv.json")).readText()
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<OpenMeteoUvResponse>(json)

        val reading = response.toDomain()

        assertEquals(3.7, reading.index, 0.0)
        assertEquals("2026-08-25T04:00", reading.observedAt)
    }

    @Test
    fun `repository requests current UV and returns domain reading`() =
        runBlocking {
            val api = RecordingOpenMeteoApi()
            val repository = OpenMeteoCurrentUvRepository(api)

            val reading = repository.getCurrentUv(latitude = -37.8136, longitude = 144.9631)

            assertEquals(-37.8136, api.latitude, 0.0)
            assertEquals(144.9631, api.longitude, 0.0)
            assertEquals("uv_index", api.current)
            assertEquals("Australia/Melbourne", api.timezone)
            assertEquals(5.2, reading.index, 0.0)
            assertEquals("2026-08-25T05:00", reading.observedAt)
        }

    private class RecordingOpenMeteoApi : OpenMeteoCurrentApi {
        var latitude = 0.0
        var longitude = 0.0
        var current = ""
        var timezone = ""

        override suspend fun getCurrentUv(
            latitude: Double,
            longitude: Double,
            current: String,
            timezone: String,
        ): OpenMeteoUvResponse {
            this.latitude = latitude
            this.longitude = longitude
            this.current = current
            this.timezone = timezone
            return OpenMeteoUvResponse(CurrentUvDto(time = "2026-08-25T05:00", uvIndex = 5.2))
        }
    }
}
