package com.example.uvapp.domain.environment

/** Validates the accuracy and monotonic age of a location used for indoor detection. */
object LocationFixValidator {
    fun isUsable(
        accuracyMeters: Float,
        fixElapsedMillis: Long?,
        nowElapsedMillis: Long,
        maxAccuracyMeters: Float,
        maxAgeMillis: Long,
    ): Boolean {
        if (!accuracyMeters.isFinite() || accuracyMeters < 0f) return false
        if (!maxAccuracyMeters.isFinite() || maxAccuracyMeters < 0f) return false
        if (fixElapsedMillis == null || fixElapsedMillis < 0L || nowElapsedMillis < 0L) return false
        if (maxAgeMillis < 0L || fixElapsedMillis > nowElapsedMillis) return false

        return accuracyMeters <= maxAccuracyMeters &&
            nowElapsedMillis - fixElapsedMillis <= maxAgeMillis
    }
}
