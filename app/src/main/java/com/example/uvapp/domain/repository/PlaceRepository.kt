package com.example.uvapp.domain.repository

import com.example.uvapp.domain.model.Coordinates
import com.example.uvapp.domain.model.PlaceName
import com.example.uvapp.domain.model.PlaceSearchResult

interface PlaceRepository {
    /** User-submitted searches only; do not call on every keystroke for autocomplete. */
    suspend fun searchPlaces(query: String): Result<List<PlaceSearchResult>>

    suspend fun reverseGeocode(coordinates: Coordinates): Result<PlaceName>
}
