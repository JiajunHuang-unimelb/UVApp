package com.example.uvapp.domain.model

/** A selectable search result; its coordinates can be passed directly to the UV repository. */
data class PlaceSearchResult(
    val name: String,
    val displayName: String,
    val coordinates: Coordinates,
)
