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
        @Query("zoom")
        zoom: Int = 13,
        @Query("layer")
        layer: String = "address",
        @Query("accept-language")
        acceptLanguage: String = "en-AU,en",
    ): NominatimResponseDto
}
