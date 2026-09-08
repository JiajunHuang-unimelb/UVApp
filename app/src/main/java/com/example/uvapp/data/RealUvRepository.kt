package com.example.uvapp.data

import com.example.uvapp.data.openmeteo.OpenMeteoClient
import com.example.uvapp.domain.advisor.BurnCalculator
import com.example.uvapp.domain.model.ForecastDay
import com.example.uvapp.domain.model.HourlyUv
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.model.UvReading
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.floor

import java.time.LocalTime

/**
 * There is no non-mock uv repository
 */
class RealUvRepository : UvRepository {



    override suspend fun getCurrentUv(): UvReading {
        delay(300)
        //need to feed in correct lat long
        val rating = OpenMeteoClient.create().getUvForecast(-37.81, 144.96).hourly.uvIndex[LocalTime.now().hour]
        val safeRating:Double = rating ?: 0.1
        return UvReading(index = safeRating)
    }

    override suspend fun getPlaceName(): String {
        delay(300)
        return "Placeholder, Placeholder"
    }

    override suspend fun getForecastDays(): List<ForecastDay> {
        delay(300)
        val days = listOf(
            tripleOf("Mon", 11, 2.0),
            tripleOf("Tue", 12, 2.0),
            tripleOf("Wed", 13, 2.0),
            tripleOf("Thu", 14, 2.0),
            tripleOf("Fri", 15, 2.0),
            tripleOf("Sat", 16, 2.0),
            tripleOf("Sun", 17, 2.0),
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
            ApiStatus(name = "Open-Meteo", ok = false, detail = "placeholder"),
            ApiStatus(name = "Nominatim", ok = false, detail = "placeholder"),
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
            val i0 = floor(base).toInt()
            val i1 = ceil(base).toInt().coerceAtMost(shape.size - 1)
            val t = base - floor(base)
            val s = if (i0 == i1) shape[i0] else shape[i0] + (shape[i1] - shape[i0]) * t
            val v = maxUv * s
            result += HourlyUv(hour = h, uv = ((v * 10).toInt() / 10.0))
            h += 0.5
        }
        return result
    }
}
