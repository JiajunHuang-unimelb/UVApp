package com.example.uvapp.domain.environment

/** Validates the accuracy and monotonic age of a location used for indoor detection. */
object LocationFixValidator {
    /**
     * Whether a new fix is precise enough to replace the one already held. Indoors the fused
     * provider sometimes falls back to Wi-Fi or cell positions hundreds of metres wide; letting
     * one of those replace a recent precise fix would end a confirmed indoor state for nothing.
     */
    fun shouldReplace(
        accuracyMeters: Float?,
        maxAccuracyMeters: Float,
    ): Boolean =
        accuracyMeters != null &&
            accuracyMeters.isFinite() &&
            accuracyMeters >= 0f &&
            accuracyMeters <= maxAccuracyMeters

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
