package com.example.uvapp.domain.model

/** A single foreground location fix together with the quality signals used by the app. */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float,
    val capturedAtMillis: Long,
    val isApproximate: Boolean,
    val isMock: Boolean,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "Latitude must be finite and between -90 and 90"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "Longitude must be finite and between -180 and 180"
        }
        require(accuracyMeters.isFinite() && accuracyMeters >= 0f) {
            "Location accuracy must be finite and non-negative"
        }
        require(capturedAtMillis >= 0L) { "Location timestamp must be non-negative" }
    }
}
