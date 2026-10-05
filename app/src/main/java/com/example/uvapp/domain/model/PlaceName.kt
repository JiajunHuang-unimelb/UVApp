package com.example.uvapp.domain.model

/** Human-readable location returned by reverse geocoding. */
data class PlaceName(
    val label: String,
    val locality: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val displayName: String,
)
