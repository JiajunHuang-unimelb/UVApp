package com.example.uvapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** UI theme preference. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** User-selectable main (accent) colour of the app. */
enum class AccentColor(val label: String) {
    AMBER("Amber"),
    TEAL("Teal"),
    BLUE("Blue"),
    GREEN("Green"),
    PURPLE("Purple"),
    ROSE("Rose"),
}

/** Immutable snapshot of the Settings screen. */
data class SettingsUiState(
    val onboardingCompleted: Boolean = false,
    val skinType: SkinType = SkinType.II,
    val spf: Int = 15,
    val notificationsEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: AccentColor = AccentColor.AMBER,
    val devModeEnabled: Boolean = false,
)

/** Settings screen state + user actions. */
class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                _state.update {
                    it.copy(
                        onboardingCompleted = preferences.onboardingCompleted,
                        skinType = preferences.skinType,
                        spf = preferences.spf,
                    )
                }
            }
        }
    }

    fun selectSkinType(type: SkinType) {
        _state.update { it.copy(skinType = type) }
        viewModelScope.launch { preferencesRepository.setSkinType(type) }
    }

    fun setSpf(spf: Int) {
        _state.update { it.copy(spf = spf) }
        viewModelScope.launch { preferencesRepository.setSpf(spf) }
    }

    fun completeOnboarding(skinType: SkinType, spf: Int) {
        _state.update {
            it.copy(
                onboardingCompleted = true,
                skinType = skinType,
                spf = spf,
            )
        }
        viewModelScope.launch { preferencesRepository.completeOnboarding(skinType, spf) }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        _state.update { it.copy(notificationsEnabled = enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        _state.update { it.copy(themeMode = mode) }
    }

    fun setAccent(accent: AccentColor) {
        _state.update { it.copy(accent = accent) }
    }

    fun setDevModeEnabled(enabled: Boolean) {
        _state.update { it.copy(devModeEnabled = enabled) }
    }
}
