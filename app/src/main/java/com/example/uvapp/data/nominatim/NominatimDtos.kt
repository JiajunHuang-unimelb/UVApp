package com.example.uvapp.data.nominatim

/** Subset of a Nominatim reverse-geocoding response required by the app. */
data class NominatimResponseDto(
    val displayName: String,
    val address: NominatimAddressDto?,
)

data class NominatimAddressDto(
    val suburb: String?,
    val city: String?,
    val state: String?,
    val country: String?,
)
