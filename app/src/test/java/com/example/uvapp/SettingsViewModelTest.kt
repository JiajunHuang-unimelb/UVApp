package com.example.uvapp

import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.model.UserPreferences
import com.example.uvapp.viewmodel.AccentColor
import com.example.uvapp.viewmodel.SettingsViewModel
import com.example.uvapp.viewmodel.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `defaults match the mockup profile`() {
        val state = SettingsViewModel(FakeUserPreferencesRepository()).state.value
        assertEquals(SkinType.II, state.skinType)
        assertEquals(15, state.spf)
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertEquals(AccentColor.AMBER, state.accent)
        assertTrue(state.notificationsEnabled)
        assertFalse(state.devModeEnabled)
    }

    @Test
    fun `each setter updates only its own field and earlier updates persist`() = runTest(dispatcher) {
        val repository = FakeUserPreferencesRepository()
        val vm = SettingsViewModel(repository)
        runCurrent()

        vm.selectSkinType(SkinType.V)
        vm.setSpf(50)
        vm.setNotificationsEnabled(false)
        vm.setThemeMode(ThemeMode.DARK)
        vm.setAccent(AccentColor.TEAL)
        vm.setDevModeEnabled(true)
        runCurrent()

        assertEquals(SkinType.V, repository.preferences.value.skinType)
        assertEquals(50, repository.preferences.value.spf)

        val state = vm.state.value
        assertEquals(SkinType.V, state.skinType)
        assertEquals(50, state.spf)
        assertFalse(state.notificationsEnabled)
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals(AccentColor.TEAL, state.accent)
        assertTrue(state.devModeEnabled)
    }

    @Test
    fun `stored profile is restored and onboarding completion is saved`() = runTest(dispatcher) {
        val repository = FakeUserPreferencesRepository(
            UserPreferences(onboardingCompleted = false, skinType = SkinType.IV, spf = 30),
        )
        val vm = SettingsViewModel(repository)
        runCurrent()

        assertEquals(SkinType.IV, vm.state.value.skinType)
        assertEquals(30, vm.state.value.spf)

        vm.completeOnboarding(SkinType.V, 50)
        runCurrent()

        assertTrue(repository.preferences.value.onboardingCompleted)
        assertEquals(SkinType.V, repository.preferences.value.skinType)
        assertEquals(50, repository.preferences.value.spf)
    }
}
