package com.example.uvapp

import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.model.UserPreferences
import com.example.uvapp.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeUserPreferencesRepository(initial: UserPreferences = UserPreferences()) : UserPreferencesRepository {
    private val mutablePreferences = MutableStateFlow(initial)
    override val preferences = mutablePreferences.asStateFlow()

    override suspend fun setSkinType(skinType: SkinType) {
        mutablePreferences.value = mutablePreferences.value.copy(skinType = skinType)
    }

    override suspend fun setSpf(spf: Int) {
        mutablePreferences.value = mutablePreferences.value.copy(spf = spf)
    }

    override suspend fun completeOnboarding(skinType: SkinType, spf: Int) {
        mutablePreferences.value =
            UserPreferences(
                onboardingCompleted = true,
                skinType = skinType,
                spf = spf,
            )
    }
}
