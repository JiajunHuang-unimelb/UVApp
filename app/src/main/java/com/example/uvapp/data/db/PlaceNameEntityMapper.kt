package com.example.uvapp.data.db

import com.example.uvapp.domain.model.PlaceName

fun PlaceName.toEntity(
    locationKey: String,
    latitude: Double,
    longitude: Double,
    fetchedAtMillis: Long,
): PlaceNameEntity =
    PlaceNameEntity(
        locationKey = locationKey,
        latitude = latitude,
        longitude = longitude,
        label = label,
        locality = locality,
        city = city,
        state = state,
        country = country,
        displayName = displayName,
        fetchedAtMillis = fetchedAtMillis,
    )

fun PlaceNameEntity.toDomain(): PlaceName =
    PlaceName(
        label = label,
        locality = locality,
        city = city,
        state = state,
        country = country,
        displayName = displayName,
    )
