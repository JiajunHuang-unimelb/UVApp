package com.example.uvapp.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Cached reverse-geocoding result for an approximate location. */
@Entity(tableName = "place_names")
data class PlaceNameEntity(
    @PrimaryKey
    val locationKey: String,
    val latitude: Double,
    val longitude: Double,
    val label: String,
    val locality: String?,
    val city: String?,
    val state: String?,
    val country: String?,
    val displayName: String,
    val fetchedAtMillis: Long,
)
