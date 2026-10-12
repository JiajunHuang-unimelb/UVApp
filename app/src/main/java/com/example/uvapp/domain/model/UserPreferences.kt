package com.example.uvapp.domain.model

/** User choices that must survive app restarts. */
data class UserPreferences(
    val onboardingCompleted: Boolean = false,
    val skinType: SkinType = SkinType.II,
    val spf: Int = 15,
)
