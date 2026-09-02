package com.example.uvapp.domain.model

/** A single current-UV reading. */
data class UvReading(val index: Double) {
    val band: UvBand get() = UvBand.fromIndex(index)
}
