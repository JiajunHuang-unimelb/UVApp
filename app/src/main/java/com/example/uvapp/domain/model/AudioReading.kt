package com.example.uvapp.domain.model

/** A single microphone audio level reading */
data class AudioReading(
    val decibels: Float,
    val timestamp: Long = System.currentTimeMillis()
) {
    init {
        require(decibels.isFinite() && decibels in 0f..120f) {
            "Decibels must be finite and between 0 and 120"
        }
        require(timestamp >= 0L) {
            "Audio reading timestamp must be non-negative"
        }
    }
}