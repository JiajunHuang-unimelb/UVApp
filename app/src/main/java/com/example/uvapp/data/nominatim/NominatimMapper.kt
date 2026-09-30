package com.example.uvapp.data.nominatim

import com.example.uvapp.domain.model.PlaceName
import com.example.uvapp.domain.model.Coordinates
import com.example.uvapp.domain.model.PlaceSearchResult

fun NominatimSearchResultDto.toSearchResult(): PlaceSearchResult {
    require(displayName.isNotBlank()) { "Nominatim returned no usable place name" }
    return PlaceSearchResult(
        name = name?.takeIf(String::isNotBlank) ?: displayName.substringBefore(',').trim(),
        displayName = displayName,
        coordinates = Coordinates(latitude = lat.toDouble(), longitude = lon.toDouble()),
    )
}

fun NominatimResponseDto.toPlaceName(): PlaceName {
    val address = address
    val locality =
        sequenceOf(
            address?.suburb,
            address?.neighbourhood,
            address?.cityDistrict,
            address?.town,
            address?.village,
            address?.municipality,
            address?.county,
        ).firstOrNull { value -> !value.isNullOrBlank() }
    val city = address?.city ?: address?.municipality
    val label =
        listOfNotNull(locality, city)
            .filter { value -> value.isNotBlank() }
            .distinct()
            .joinToString(", ")
            .ifBlank {
                address?.state?.takeIf { value -> value.isNotBlank() }
                    ?: displayName
            }

    require(label.isNotBlank()) { "Nominatim returned no usable place name" }

    return PlaceName(
        label = label,
        locality = locality,
        city = city,
        state = address?.state,
        country = address?.country,
        displayName = displayName,
    )
}
