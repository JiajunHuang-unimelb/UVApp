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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.ui.components.SettingsRadioRow
import com.example.uvapp.ui.components.SettingsSwitchRow
import com.example.uvapp.ui.components.SpfSlider
import com.example.uvapp.ui.components.UvSwitch
import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.ui.theme.accentPalette
import com.example.uvapp.viewmodel.AccentColor
import com.example.uvapp.viewmodel.SettingsUiState
import com.example.uvapp.viewmodel.SettingsViewModel
import com.example.uvapp.viewmodel.ThemeMode

/** Settings tab: skin type, SPF, notifications, theme, developer mode. */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    state: SettingsUiState,
    modifier: Modifier = Modifier,
) {
    val colors = UvTheme
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 88.dp),
    ) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)

        Spacer(Modifier.height(24.dp))
        Text("Skin type", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        SkinType.entries.forEach { type ->
            SettingsRadioRow(
                label = type.displayName(),
                selected = state.skinType == type,
                onClick = { viewModel.selectSkinType(type) },
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("SPF", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground, modifier = Modifier.weight(1f))
            Text(state.spf.toString(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.accent)
        }
        Spacer(Modifier.height(8.dp))
        SpfSlider(state.spf, viewModel::setSpf)

        Spacer(Modifier.height(20.dp))
        SettingsSwitchRow(
            title = "Notifications",
            subtitle = "Remind me to reapply sunscreen",
            checked = state.notificationsEnabled,
            onToggle = { viewModel.setNotificationsEnabled(!state.notificationsEnabled) },
        )

        Spacer(Modifier.height(24.dp))
        Text("Theme", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
        ThemeMode.entries.forEach { mode ->
            SettingsRadioRow(
                label = mode.label,
                selected = state.themeMode == mode,
                onClick = { viewModel.setThemeMode(mode) },
            )
        }

        Spacer(Modifier.height(24.dp))
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

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.errorBanner)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Developer mode",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            UvSwitch(
                checked = state.devModeEnabled,
                onToggle = { viewModel.setDevModeEnabled(!state.devModeEnabled) },
                onColor = colors.accent,
            )
        }
    }
}
