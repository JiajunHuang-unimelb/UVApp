package com.example.uvapp.data.openmeteo

import retrofit2.http.GET
import retrofit2.http.Query

/** Network boundary for the current Open-Meteo UV value. */
internal interface OpenMeteoCurrentApi {
    @GET("v1/forecast")
    suspend fun getCurrentUv(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String,
        @Query("timezone") timezone: String,
    ): OpenMeteoUvResponse
}
