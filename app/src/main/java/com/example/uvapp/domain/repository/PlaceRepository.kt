package com.example.uvapp.domain.repository

import com.example.uvapp.domain.model.Coordinates
import com.example.uvapp.domain.model.PlaceName

interface PlaceRepository {
    suspend fun reverseGeocode(coordinates: Coordinates): Result<PlaceName>
}
