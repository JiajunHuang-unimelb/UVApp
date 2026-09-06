package com.example.uvapp.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.model.UserPreferences
import com.example.uvapp.domain.repository.UserPreferencesRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

class DataStoreUserPreferencesRepository(context: Context) : UserPreferencesRepository {
    private val dataStore = context.applicationContext.userPreferencesDataStore

    override val preferences: Flow<UserPreferences> =
        dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }.map { values ->
                UserPreferences(
                    onboardingCompleted = values[Keys.ONBOARDING_COMPLETED] ?: false,
                    skinType = values[Keys.SKIN_TYPE].toSkinType(),
                    spf = values[Keys.SPF] ?: 15,
                )
            }

    override suspend fun setSkinType(skinType: SkinType) {
        dataStore.edit { it[Keys.SKIN_TYPE] = skinType.name }
    }

    override suspend fun setSpf(spf: Int) {
        dataStore.edit { it[Keys.SPF] = spf }
    }

    override suspend fun completeOnboarding(skinType: SkinType, spf: Int) {
        dataStore.edit {
            it[Keys.ONBOARDING_COMPLETED] = true
            it[Keys.SKIN_TYPE] = skinType.name
            it[Keys.SPF] = spf
        }
    }

    private fun String?.toSkinType(): SkinType =
        runCatching { SkinType.valueOf(requireNotNull(this)) }.getOrDefault(SkinType.II)

    private object Keys {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val SKIN_TYPE = stringPreferencesKey("skin_type")
        val SPF = intPreferencesKey("spf")
    }
}
