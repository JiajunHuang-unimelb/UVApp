package com.example.uvapp.data.openmeteo

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in smoke test for manually checking the real Open-Meteo service. */
class OpenMeteoLiveApiTest {
    @Test
    fun `fetches a real Melbourne forecast`() =
        runBlocking {
            assumeTrue(
                "Set RUN_LIVE_API_TEST=true to run this network test",
                System.getenv("RUN_LIVE_API_TEST") == "true",
            )

            val response = OpenMeteoClient.create().getUvForecast(-37.81, 144.96)
            val readings = response.toForecastReadings()

            assertTrue("Expected at least 24 hourly readings", readings.size >= 24)
            assertTrue("UV values must not be negative", readings.all { it.uvIndex >= 0.0 })

            println("Open-Meteo live response")
            println("timezone=${response.timezone}")
            println("hourlyRows=${readings.size}")
            println("time | uv_index | uv_index_clear_sky | cloud_cover")
            response.hourly.time.indices.take(24).forEach { index ->
                println(
                    "${response.hourly.time[index]} | " +
                        "${response.hourly.uvIndex[index]} | " +
                        "${response.hourly.uvIndexClearSky[index]} | " +
                        "${response.hourly.cloudCover[index]}%",
                )
            }
        }
}
