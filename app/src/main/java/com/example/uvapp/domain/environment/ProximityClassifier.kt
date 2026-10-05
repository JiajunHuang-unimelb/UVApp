package com.example.uvapp.domain.environment

/** Converts Android proximity readings into a stable near/far signal. */
object ProximityClassifier {
    fun isOccluded(
        distance: Float?,
        maximumRange: Float,
    ): Boolean? {
        if (distance == null || !distance.isFinite() || distance < 0f) return null
        if (!maximumRange.isFinite() || maximumRange <= 0f) return null
        return distance < maximumRange
    }
}
