package com.example.uvapp.domain.model

/**
 * Fitzpatrick skin phototype scale (I–VI).
 * exposureLimitSed is the personalized erythemal-dose limit used by exposure tracking.
 */
enum class SkinType(val label: String, val description: String, val exposureLimitSed: Double) {
    I("Type I", "Very fair", 2.0),
    II("Type II", "Fair", 2.5),
    III("Type III", "Medium", 3.0),
    IV("Type IV", "Olive", 4.5),
    V("Type V", "Brown", 6.0),
    VI("Type VI", "Dark", 6.0);

    fun displayName(): String = "$label – $description"
}
