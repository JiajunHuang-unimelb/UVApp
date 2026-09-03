package com.example.uvapp.data.nominatim

import retrofit2.http.GET
import retrofit2.http.Query

/** Network boundary for reverse geocoding through Nominatim. */
interface NominatimApi {
    @GET("reverse")
    suspend fun reverseGeocode(
        @Query("lat")
        latitude: Double,
        @Query("lon")
        longitude: Double,
        @Query("format")
        format: String = "jsonv2",
        @Query("addressdetails")
        addressDetails: Int = 1,
    ): NominatimResponseDto
}
