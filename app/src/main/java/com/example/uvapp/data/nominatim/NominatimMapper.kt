package com.example.uvapp.data.nominatim

import com.example.uvapp.domain.model.PlaceName

interface NominatimMapper {
    fun toDomain(response: NominatimResponseDto): PlaceName
}

class DefaultNominatimMapper : NominatimMapper {
    override fun toDomain(response: NominatimResponseDto): PlaceName {
        val address = response.address
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
                        ?: response.displayName
                }

        require(label.isNotBlank()) { "Nominatim returned no usable place name" }

        return PlaceName(
            label = label,
            locality = locality,
            city = city,
            state = address?.state,
            country = address?.country,
            displayName = response.displayName,
        )
    }
}
