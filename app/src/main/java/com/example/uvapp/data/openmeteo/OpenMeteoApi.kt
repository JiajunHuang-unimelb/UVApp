package com.example.uvapp.data.openmeteo

import retrofit2.http.GET
import retrofit2.http.Query

internal interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getCurrentUv(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String,
        @Query("timezone") timezone: String,
    ): OpenMeteoUvResponse
}
