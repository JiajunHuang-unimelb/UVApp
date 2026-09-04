package com.example.uvapp.domain.location

import com.example.uvapp.domain.model.LocationFix

/** Foreground, one-shot location boundary. No background tracking is performed. */
interface CurrentLocationProvider {
    suspend fun getCurrentLocation(): LocationResult
}

/** Domain-level outcomes that let the UI offer the correct recovery action. */
sealed interface LocationResult {
    data class Success(
        val fix: LocationFix,
    ) : LocationResult

    data object PermissionDenied : LocationResult

    data object LocationDisabled : LocationResult

    data object Timeout : LocationResult

    data object Unavailable : LocationResult
}
