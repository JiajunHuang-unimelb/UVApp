package com.example.uvapp.domain.model

/** A single current-UV reading. */
data class UvReading(
    val index: Double,
    val observedAt: String? = null,
) {
    val band: UvBand get() = UvBand.fromIndex(index)
}
