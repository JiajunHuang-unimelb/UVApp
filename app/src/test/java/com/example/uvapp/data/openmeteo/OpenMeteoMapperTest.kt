package com.example.uvapp.data.openmeteo

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class OpenMeteoMapperTest {
    private val mapper = DefaultOpenMeteoMapper()

    @Test
    fun `maps aligned hourly values using the response timezone`() {
        val response =
            response(
                time = listOf("2026-08-26T10:00", "2026-08-26T11:00"),
                uvIndex = listOf(2.3, 3.1),
                clearSkyUvIndex = listOf(2.8, 3.6),
                cloudCover = listOf(40, 35),
            )

        val readings = mapper.toDomain(response)

        val expectedFirstTime =
            LocalDateTime
                .parse("2026-08-26T10:00")
                .atZone(ZoneId.of("Australia/Melbourne"))
                .toInstant()
                .toEpochMilli()
        assertEquals(2, readings.size)
        assertEquals(expectedFirstTime, readings.first().forecastTimeMillis)
        assertEquals(2.3, readings.first().uvIndex, 0.0)
        assertEquals(2.8, readings.first().clearSkyUvIndex ?: 0.0, 0.0)
        assertEquals(40, readings.first().cloudCoverPercent)
    }

    @Test
    fun `rejects misaligned hourly arrays`() {
        val response =
            response(
                time = listOf("2026-08-26T10:00", "2026-08-26T11:00"),
                uvIndex = listOf(2.3),
                clearSkyUvIndex = listOf(2.8, 3.6),
                cloudCover = listOf(40, 35),
            )

        assertThrows(IllegalArgumentException::class.java) {
            mapper.toDomain(response)
        }
    }

    @Test
    fun `rejects a missing UV value instead of treating it as zero`() {
        val response =
            response(
                time = listOf("2026-08-26T10:00"),
                uvIndex = listOf(null),
                clearSkyUvIndex = listOf(2.8),
                cloudCover = listOf(40),
            )

        assertThrows(IllegalArgumentException::class.java) {
            mapper.toDomain(response)
        }
    }

    private fun response(
        time: List<String>,
        uvIndex: List<Double?>,
        clearSkyUvIndex: List<Double?>,
        cloudCover: List<Int?>,
    ): OpenMeteoResponseDto =
        OpenMeteoResponseDto(
            latitude = -37.81,
            longitude = 144.96,
            timezone = "Australia/Melbourne",
            hourly =
                OpenMeteoHourlyDto(
                    time = time,
                    uvIndex = uvIndex,
                    uvIndexClearSky = clearSkyUvIndex,
                    cloudCover = cloudCover,
                ),
        )
}
