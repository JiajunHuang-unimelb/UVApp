package com.example.uvapp.domain.model

/**
 * Fitzpatrick skin phototype scale (I–VI). Exposure thresholds live in the
 * exposure domain model so the countdown has one calculation source.
 */
enum class SkinType(val label: String, val description: String) {
    I("Type I", "Very fair"),
    II("Type II", "Fair"),
    III("Type III", "Medium"),
    IV("Type IV", "Olive"),
    V("Type V", "Brown"),
    VI("Type VI", "Dark");

    fun displayName(): String = "$label – $description"
}
