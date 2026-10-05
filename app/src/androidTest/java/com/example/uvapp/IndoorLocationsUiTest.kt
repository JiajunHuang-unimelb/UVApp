package com.example.uvapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.uvapp.domain.model.*
import com.example.uvapp.domain.location.*
import com.example.uvapp.domain.repository.*
import com.example.uvapp.ui.components.*
import com.example.uvapp.viewmodel.*
import kotlinx.coroutines.flow.*
import org.junit.Rule
import org.junit.Test

class IndoorLocationsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun saveRenameDeleteAndRestoreDialog() {
        val repo = object : IndoorLocationRepository {
            override val data = MutableStateFlow(IndoorLocationsData())
            override suspend fun update(transform: (IndoorLocationsData) -> IndoorLocationsData) { data.value = transform(data.value) }
        }
        val prefs = object : UserPreferencesRepository {
            override val preferences = MutableStateFlow(UserPreferences())
            override suspend fun setSkinType(skinType: SkinType) {}
            override suspend fun setSpf(spf: Int) {}
            override suspend fun completeOnboarding(skinType: SkinType, spf: Int) {}
        }
        val provider = object : CurrentLocationProvider {
            override suspend fun getCurrentLocation() = LocationResult.Success(LocationFix(-37.8, 144.96, 10f, 10_000, false, false))
        }
        lateinit var vm: IndoorLocationsViewModel
        compose.runOnUiThread { vm = IndoorLocationsViewModel(repo, provider, MainViewModel(SettingsViewModel(prefs)), now = { 10_000 }) }
        compose.setContent {
            val state by vm.state.collectAsState()
            MaterialTheme {
                IndoorLocationsPanel(vm, state, vm::requestSave, { vm.enableSuggestions(true) }, settings = true)
                IndoorSuggestionDialog(vm, state)
            }
        }
        compose.onNodeWithText("Automatic indoor detection needs a saved location. Manual tracking is available.").assertExists()
        compose.onNodeWithText("I’m indoors here").performClick()
        compose.waitUntil { repo.data.value.pending != null }
        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil { repo.data.value.locations.size == 1 }
        compose.onNodeWithText("Rename").performClick()
        compose.onNodeWithText("Name").performTextReplacement("Workplace")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil { repo.data.value.locations.single().name == "Workplace" }
        compose.onNodeWithText("Delete").performClick()
        compose.onNodeWithText("Delete Workplace?").assertExists()
        compose.onAllNodesWithText("Delete").onLast().performClick()
        compose.waitUntil { repo.data.value.locations.isEmpty() }
        compose.onNodeWithText("I’m indoors here").performClick()
        compose.waitUntil { repo.data.value.pending != null }
        compose.runOnUiThread { vm.setVisible(false) }
        compose.onNodeWithText("Save this indoor location?").assertDoesNotExist()
        compose.runOnUiThread { vm.setVisible(true) }
        compose.onNodeWithText("Save this indoor location?").assertExists()
        compose.onAllNodesWithText("Not now").onLast().performClick()
        compose.waitUntil { repo.data.value.pending == null }
    }
}
