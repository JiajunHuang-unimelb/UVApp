package com.example.uvapp.domain.environment

import com.example.uvapp.domain.model.IndoorLocation
import com.example.uvapp.domain.model.contains

/**
 * Decides from the last precise fix whether the user is at one of their saved indoor places.
 *
 * Coarse fixes never reach this rule (see [LocationFixValidator.shouldReplace]), so the held fix
 * is always precise. While step data says the user has not walked, they cannot have left the
 * place that fix put them in, so it is held without an age limit and only a new precise fix can
 * change the answer. Once they walk, or when step data is unavailable, the fix expires after
 * [maxAgeMillis] measured from when it was taken.
 */
object IndoorProximityRule {
    fun isNear(
        fix: HeldFix?,
        savedLocations: List<IndoorLocation>,
        nowElapsedMillis: Long,
        userStationary: Boolean,
        maxAccuracyMeters: Float,
        maxAgeMillis: Long,
    ): Boolean {
        if (fix == null) return false
        val usable =
            LocationFixValidator.isUsable(
                accuracyMeters = fix.accuracyMeters,
                fixElapsedMillis = fix.elapsedMillis,
                nowElapsedMillis = nowElapsedMillis,
                maxAccuracyMeters = maxAccuracyMeters,
                maxAgeMillis = if (userStationary) Long.MAX_VALUE else maxAgeMillis,
            )
        return usable && savedLocations.any { it.contains(fix.latitude, fix.longitude) }
    }

    data class HeldFix(
        val latitude: Double,
        val longitude: Double,
        val accuracyMeters: Float,
        val elapsedMillis: Long?,
    )
}
