package com.example.uvapp

import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.viewmodel.AccentColor
import com.example.uvapp.viewmodel.SettingsViewModel
import com.example.uvapp.viewmodel.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {

    @Test
    fun `defaults match the mockup profile`() {
        val state = SettingsViewModel().state.value
        assertEquals(SkinType.II, state.skinType)
        assertEquals(15, state.spf)
        assertEquals(ThemeMode.SYSTEM, state.themeMode)
        assertEquals(AccentColor.AMBER, state.accent)
        assertTrue(state.notificationsEnabled)
        assertFalse(state.devModeEnabled)
    }

    @Test
    fun `each setter updates only its own field and earlier updates persist`() {
        val vm = SettingsViewModel()

        vm.selectSkinType(SkinType.V)
        vm.setSpf(50)
        vm.setNotificationsEnabled(false)
        vm.setThemeMode(ThemeMode.DARK)
        vm.setAccent(AccentColor.TEAL)
        vm.setDevModeEnabled(true)

        val state = vm.state.value
        assertEquals(SkinType.V, state.skinType)
        assertEquals(50, state.spf)
        assertFalse(state.notificationsEnabled)
        assertEquals(ThemeMode.DARK, state.themeMode)
        assertEquals(AccentColor.TEAL, state.accent)
        assertTrue(state.devModeEnabled)
    }
}
