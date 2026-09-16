package com.example.uvapp.domain.model

/**
 * Fitzpatrick skin phototype scale (I–VI).
 * baseMinutesAtUv1 are provisional values used to derive the burn countdown.
 */
enum class SkinType(val label: String, val description: String, val baseMinutesAtUv1: Int) {
    I("Type I", "Very fair", 67),
    II("Type II", "Fair", 100),
    III("Type III", "Medium", 200),
    IV("Type IV", "Olive", 300),
    V("Type V", "Brown", 400),
    VI("Type VI", "Dark", 500);

    fun displayName(): String = "$label – $description"
}
