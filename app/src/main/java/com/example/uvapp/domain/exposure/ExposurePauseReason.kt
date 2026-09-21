package com.example.uvapp.domain.exposure

/** Explains who owns a paused exposure session so UI and resume logic stay accurate. */
enum class ExposurePauseReason {
    MANUAL,
    INDOOR_DETECTED,
}
