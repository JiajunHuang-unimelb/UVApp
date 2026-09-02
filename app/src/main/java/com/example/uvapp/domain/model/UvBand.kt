package com.example.uvapp.domain.model

/**
 * WHO Global Solar UV Index bands.
 * Boundaries: <3 Low, <6 Moderate, <8 High, <11 Very High, >=11 Extreme.
 */
enum class UvBand(val label: String, val min: Double, val maxExclusive: Double?) {
    LOW("Low", 0.0, 3.0),
    MODERATE("Moderate", 3.0, 6.0),
    HIGH("High", 6.0, 8.0),
    VERY_HIGH("Very High", 8.0, 11.0),
    EXTREME("Extreme", 11.0, null);

    companion object {
        fun fromIndex(index: Double): UvBand =
            entries.firstOrNull { index >= it.min && (it.maxExclusive == null || index < it.maxExclusive) }
                ?: EXTREME
    }
}
