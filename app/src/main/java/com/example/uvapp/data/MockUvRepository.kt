package com.example.uvapp.data

import com.example.uvapp.domain.advisor.BurnCalculator
import com.example.uvapp.domain.model.ForecastDay
import com.example.uvapp.domain.model.HourlyUv
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.model.UvReading
import kotlinx.coroutines.delay

/**
 * Mock implementation used until the backend team ships the real API layer.
 *
 * Values are hand-picked to reproduce the high-fidelity mockups exactly:
 *  - current UV 8.4 "Very High" (Southbank, Melbourne)
 *  - 7-day forecast Mon 11 .. Sun 17, selected day Wed 13
 *  - Wed curve: hourly 04:00..21:00, peak 8.5 around noon
 *  - sunrise 06:43 / sunset 20:11
 *  - direct sun, 38 200 lux, 84 steps/min
 */
class MockUvRepository : UvRepository {

    override suspend fun getCurrentUv(): UvReading {
        delay(300)
        return UvReading(index = 8.4)
    }

    override suspend fun getPlaceName(): String {
        delay(300)
        return "Southbank, Melbourne"
    }

    override suspend fun getForecastDays(): List<ForecastDay> {
        delay(300)
        val days = listOf(
            tripleOf("Mon", 11, 2.4),
            tripleOf("Tue", 12, 6.8),
            tripleOf("Wed", 13, 8.5),
            tripleOf("Thu", 14, 7.4),
            tripleOf("Fri", 15, 4.2),
            tripleOf("Sat", 16, 1.8),
            tripleOf("Sun", 17, 2.2),
        )
        return days.map { (weekday, dayOfMonth, maxUv) ->
            ForecastDay(
                weekday = weekday,
                dayOfMonth = dayOfMonth,
                maxUv = maxUv,
                sunriseMinutes = 6 * 60 + 43,
                sunsetMinutes = 20 * 60 + 11,
                hourly = curveFor(maxUv),
            )
        }
    }

    override suspend fun getSensorContext(): SensorContext {
        delay(200)
        return SensorContext(
            lightContext = LightContext.DIRECT_SUN,
            lux = 38_200,
            stepsPerMinute = 84,
        )
    }

    override suspend fun getBurnMinutes(
        skinType: SkinType,
        spf: Int,
        uvIndex: Double,
        context: LightContext,
    ): Int = BurnCalculator.burnMinutes(skinType, spf, uvIndex, context)

    override suspend fun getApiStatuses(): List<ApiStatus> {
        delay(150)
        return listOf(
            ApiStatus(name = "Open-Meteo", ok = true, detail = "200 OK · 24 h"),
            ApiStatus(name = "Nominatim", ok = true, detail = "200 OK · Southbank"),
        )
    }

    private fun tripleOf(weekday: String, dayOfMonth: Int, maxUv: Double) =
        Triple(weekday, dayOfMonth, maxUv)

    /**
     * Hourly curve for one day, hours 04:00..21:00 (hours 0..3 and 22..23 are
     * null = gaps in the chart line). Shape is the mockup's Wed curve scaled to
     * the day's max UV.
     */
    private fun curveFor(maxUv: Double): List<HourlyUv> {
        // Normalised shape of the mockup curve, hourly 04:00..21:00
        // (peak 1.0 at 12:00-13:00).
        val shape = doubleArrayOf(
            0.035, 0.200, 0.282, 0.470, 0.659, 0.824, 0.918, 0.988,
            1.000, 1.000, 0.976, 0.906, 0.812, 0.659, 0.435, 0.271,
            0.200, 0.035,
        )
        // 30-minute resolution over the chart window (06:30..20:30) so the
        // curve reaches the full width of the chart.
        val result = ArrayList<HourlyUv>(29)
        var h = 6.5
        while (h <= 20.5) {
            val base = h - 4.0
            val i0 = kotlin.math.floor(base).toInt()
            val i1 = kotlin.math.ceil(base).toInt().coerceAtMost(shape.size - 1)
            val t = base - kotlin.math.floor(base)
            val s = if (i0 == i1) shape[i0] else shape[i0] + (shape[i1] - shape[i0]) * t
            val v = maxUv * s
            result += HourlyUv(hour = h, uv = ((v * 10).toInt() / 10.0))
            h += 0.5
        }
        return result
    }
}
