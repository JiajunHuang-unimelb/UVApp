package com.example.uvapp.data

import com.example.uvapp.domain.model.ForecastDay
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.model.UvReading
import com.example.uvapp.data.repository.DefaultUvRepository

/**
 * Data contract for everything the UI needs.
 *
 * This is the seam the backend team implements later (Retrofit + Open-Meteo +
 * Nominatim + Room). The front end only ever sees this interface and the
 * [MockUvRepository] fake, so swapping in the real backend is a drop-in change.
 */
interface UvRepository {
    /** Current UV index at the active location. */
    suspend fun getCurrentUv(): UvReading

    /** Human-readable place name (reverse geocoded). */
    suspend fun getPlaceName(): String

    /** Seven-day hourly forecast (today + 6 days). */
    suspend fun getForecastDays(): List<ForecastDay>

    /** Latest sensor-fused exposure context. */
    suspend fun getSensorContext(): SensorContext

    /** Personal burn time in minutes for the given profile. */
    suspend fun getBurnMinutes(skinType: SkinType, spf: Int, uvIndex: Double, context: LightContext): Int

    /** Live status of the backend services shown in the developer card. */
    suspend fun getApiStatuses(): List<ApiStatus>
}

/** Sensor-fused exposure context snapshot. */
data class SensorContext(
    val lightContext: LightContext,
    val lux: Int,
    val stepsPerMinute: Int,
)

/** Backend service status line for the developer card. */
data class ApiStatus(
    val name: String,
    val ok: Boolean,
    val detail: String,
)

/**
 * Single place where the app gets its data source.
 *
 * BACKEND TEAM: implement [UvRepository] (Retrofit + Open-Meteo + Nominatim +
 * Room) and change this ONE line — nothing else in the UI needs to move:
 *
 *     val instance: UvRepository by lazy { YourRealUvRepository() }
 */
object UvRepositoryProvider {
    val instance: UvRepository by lazy { RealUvRepository() }
}
