package com.example.uvapp.domain.model

/** Latitude/longitude value shared by location-aware repositories. */
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be finite and between -90 and 90"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be finite and between -180 and 180"
        }
    }
}
