package com.example.uvapp.domain.pocket

data class PocketSensorSample(
    val proximityNear: Boolean?,
    val ambientLux: Float?,
    val elapsedRealtimeMs: Long,
)

enum class PocketState {
    UNKNOWN,
    OUT_OF_POCKET,
    IN_POCKET,
}

data class PocketDetection(
    val state: PocketState,
    val isAvailable: Boolean,
)
