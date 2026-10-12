package com.example.uvapp.domain.repository

import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow

interface UserPreferencesRepository {
    val preferences: Flow<UserPreferences>

    suspend fun setSkinType(skinType: SkinType)

    suspend fun setSpf(spf: Int)

    suspend fun completeOnboarding(skinType: SkinType, spf: Int)
}
