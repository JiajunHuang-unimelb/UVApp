package com.example.uvapp.data.openmeteo

import retrofit2.http.GET
import retrofit2.http.Query

/** Network boundary for the hourly Open-Meteo UV forecast endpoint. */
interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getUvForecast(
        @Query("latitude")
        latitude: Double,
        @Query("longitude")
        longitude: Double,
        @Query("hourly")
        hourly: String = "uv_index,uv_index_clear_sky,cloud_cover",
        @Query("daily")
        daily: String = "uv_index_max",
        @Query("timezone")
        timezone: String = "auto",
        @Query("forecast_days")
        forecastDays: Int = 7,
    ): OpenMeteoResponseDto

}
