package com.example.uvapp.data.nominatim

/** Network boundary for reverse geocoding through Nominatim. */
interface NominatimApi {
    suspend fun reverseGeocode(
        latitude: Double,
        longitude: Double,
        format: String = "jsonv2",
        addressDetails: Int = 1,
    ): NominatimResponseDto
}
