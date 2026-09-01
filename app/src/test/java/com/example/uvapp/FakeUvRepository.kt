package com.example.uvapp

import com.example.uvapp.data.ApiStatus
import com.example.uvapp.data.SensorContext
import com.example.uvapp.data.UvRepository
import com.example.uvapp.domain.advisor.BurnCalculator
import com.example.uvapp.domain.model.ForecastDay
import com.example.uvapp.domain.model.HourlyUv
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.model.UvReading

/** Deterministic [UvRepository] fake for ViewModel tests: fixed values, no delays. */
class FakeUvRepository(
    var currentUv: Double = 8.4,
    var placeName: String = "Southbank, Melbourne",
    var sensorContext: SensorContext = SensorContext(LightContext.DIRECT_SUN, 38_200, 84),
    var forecastDays: List<ForecastDay> = listOf(
        ForecastDay(
            weekday = "Wed",
            dayOfMonth = 13,
            maxUv = 8.5,
            sunriseMinutes = 403,
            sunsetMinutes = 1211,
            hourly = listOf(HourlyUv(12.0, 8.5)),
        ),
    ),
) : UvRepository {

    var forecastCallCount = 0
        private set

    override suspend fun getCurrentUv(): UvReading = UvReading(currentUv)

    override suspend fun getPlaceName(): String = placeName

    override suspend fun getForecastDays(): List<ForecastDay> {
        forecastCallCount++
        return forecastDays
    }

    override suspend fun getSensorContext(): SensorContext = sensorContext

    override suspend fun getBurnMinutes(skinType: SkinType, spf: Int, uvIndex: Double, context: LightContext): Int =
        BurnCalculator.burnMinutes(skinType, spf, uvIndex, context)

    override suspend fun getApiStatuses(): List<ApiStatus> = listOf(ApiStatus("Open-Meteo", true, "fake"))
}
