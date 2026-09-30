package com.example.uvapp.data.nominatim

import retrofit2.http.GET
import retrofit2.http.Query

/** Network boundary for submitted place searches and reverse geocoding. */
interface NominatimApi {
    @GET("search")
    suspend fun searchPlaces(
        @Query("q") query: String,
        @Query("format") format: String = "jsonv2",
        @Query("limit") limit: Int = 5,
    ): List<NominatimSearchResultDto>

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
