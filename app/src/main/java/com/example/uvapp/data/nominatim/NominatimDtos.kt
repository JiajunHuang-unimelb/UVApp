package com.example.uvapp.data.nominatim

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Subset of a Nominatim reverse-geocoding response required by the app. */
@Serializable
data class NominatimResponseDto(
    @SerialName("display_name")
    val displayName: String,
    val address: NominatimAddressDto? = null,
)

@Serializable
data class NominatimAddressDto(
    val neighbourhood: String? = null,
    val suburb: String? = null,
    @SerialName("city_district")
    val cityDistrict: String? = null,
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val municipality: String? = null,
    val county: String? = null,
    val state: String? = null,
    val country: String? = null,
)
