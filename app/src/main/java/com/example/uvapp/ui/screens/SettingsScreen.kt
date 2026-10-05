package com.example.uvapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.R
import com.example.uvapp.ui.components.DataSourcesDialog
import com.example.uvapp.ui.components.SettingsRadioRow
import com.example.uvapp.ui.components.SettingsSection
import com.example.uvapp.ui.components.SettingsSwitchRow
import com.example.uvapp.ui.components.SpfSlider
import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.ui.theme.accentPalette
import com.example.uvapp.viewmodel.AccentColor
import com.example.uvapp.viewmodel.SettingsUiState
import com.example.uvapp.viewmodel.SettingsViewModel
import com.example.uvapp.viewmodel.ThemeMode

/** Settings tab: one titled card per group - Profile, Alerts, Sensing, Appearance, Developer. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    state: SettingsUiState,
    modifier: Modifier = Modifier,
    indoorContent: @Composable () -> Unit = {},
    developerContent: @Composable () -> Unit = {},
    onEnhancedSensingToggle: () -> Unit = {
        viewModel.setEnhancedSensingEnabled(!state.enhancedSensingEnabled)
    },
) {
    val colors = UvTheme
    var showDataSources by rememberSaveable { mutableStateOf(false) }
    if (showDataSources) {
        DataSourcesDialog(onDismiss = { showDataSources = false })
    }
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 88.dp),
    ) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        Spacer(Modifier.height(16.dp))

        SettingsSection("Profile") {
            Text("Skin type", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            SkinType.entries.forEach { type ->
                SettingsRadioRow(
                    label = type.displayName(),
                    selected = state.skinType == type,
                    onClick = { viewModel.selectSkinType(type) },
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("SPF", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
                Text(state.spf.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.accent)
            }
            Spacer(Modifier.height(8.dp))
            SpfSlider(state.spf, viewModel::setSpf)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.spf_countdown_hint),
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
        }

        Spacer(Modifier.height(12.dp))
        SettingsSection("Alerts") {
            SettingsSwitchRow(
                title = "Notifications",
                subtitle = "Notify me about indoor place suggestions",
                checked = state.notificationsEnabled,
                onToggle = { viewModel.setNotificationsEnabled(!state.notificationsEnabled) },
            )
        }

        Spacer(Modifier.height(12.dp))
        SettingsSection("Sensing") {
            SettingsSwitchRow(
                title = "Enhanced sensing",
                subtitle = "Use motion, steps and sound while the app is open",
                checked = state.enhancedSensingEnabled,
                onToggle = onEnhancedSensingToggle,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.enhanced_sensing_privacy_note),
                color = colors.textSecondary,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            indoorContent()
        }

        Spacer(Modifier.height(12.dp))
        SettingsSection("Appearance") {
            Text("Theme", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            ThemeMode.entries.forEach { mode ->
                SettingsRadioRow(
                    label = mode.label,
                    selected = state.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                )
            }

            Spacer(Modifier.height(16.dp))
            Text("Theme colour", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                AccentColor.entries.forEach { preset ->
                    val (light, dark) = accentPalette(preset)
                    val swatch = if (colors.isDark) dark else light
                    val selected = state.accent == preset
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .then(
                                if (selected) {
                                    Modifier.border(2.5.dp, colors.accent, CircleShape)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { viewModel.setAccent(preset) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) {
                            Text("✓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingsSection("Developer") {
            SettingsSwitchRow(
                title = "Developer mode",
                subtitle = "Show test controls and diagnostics on Home",
                checked = state.devModeEnabled,
                onToggle = { viewModel.setDevModeEnabled(!state.devModeEnabled) },
            )
            if (state.devModeEnabled) {
                Spacer(Modifier.height(8.dp))
                developerContent()
            }
        }
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = { showDataSources = true }) {
            Text(stringResource(R.string.data_sources), color = colors.accent)
        }
    }
}
