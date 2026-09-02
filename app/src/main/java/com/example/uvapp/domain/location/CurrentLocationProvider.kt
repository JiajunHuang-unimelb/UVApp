package com.example.uvapp.domain.location

import com.example.uvapp.domain.model.Coordinates

interface CurrentLocationProvider {
    suspend fun getCurrentLocation(): Coordinates
}

class LocationUnavailableException(
    cause: Throwable? = null,
) : Exception("Current location is unavailable", cause)
