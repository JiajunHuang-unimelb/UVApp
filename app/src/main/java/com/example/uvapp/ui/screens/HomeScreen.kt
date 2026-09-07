package com.example.uvapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.uvapp.ui.components.CachedIndicator
import com.example.uvapp.ui.components.ContextCard
import com.example.uvapp.ui.components.DevCard
import com.example.uvapp.ui.components.ErrorBanner
import com.example.uvapp.ui.components.SafeTimerCard
import com.example.uvapp.ui.components.TopChrome
import com.example.uvapp.viewmodel.MainUiState
import com.example.uvapp.viewmodel.MainViewModel

/** Home tab: hero, address, safe timer, exposure indicator, cached note, dev card. */
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    state: MainUiState,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 80.dp),
        ) {
            state.errorMessage?.let { message ->
                ErrorBanner(message)
                Spacer(Modifier.height(10.dp))
            }

            TopChrome(
                uv = state.displayUv,
                uvAvailable = state.uvAvailable,
                skinType = state.skinType,
                spf = state.spf,
                placeName = state.placeName,
                onSearchClick = viewModel::onSearchClick,
                onLocate = onLocate,
            )
            Spacer(Modifier.height(10.dp))
            SafeTimerCard(
                remainingSeconds = state.remainingSeconds,
                totalBurnSeconds = state.totalBurnSeconds,
                isWarning = state.isWarning,
                onReset = viewModel::onResetTimer,
            )

            Spacer(Modifier.height(12.dp))
            ContextCard(
                context = state.displayContext,
                lux = state.displayLux,
                onLuxChange = viewModel::onLuxChange,
            )

            if (state.isCached) {
                Spacer(Modifier.height(10.dp))
                CachedIndicator()
            }

            if (state.devModeEnabled) {
                Spacer(Modifier.height(12.dp))
                DevCard(
                    apiStatuses = state.apiStatuses,
                    lux = state.displayLux,
                    stepsPerMinute = state.stepsPerMinute,
                    dev = state.dev,
                    onToggleSpeed = viewModel::onSpeedToggle,
                    onToggleUvOverride = viewModel::onOverrideUvToggle,
                    onUvOverride = viewModel::onUvOverride,
                    onToggleLightOverride = viewModel::onOverrideLightToggle,
                    onLightOverride = viewModel::onLightOverride,
                    onToggleAudio = viewModel::onAudioToggle,
                    onToggleOccluded = viewModel::onOccludedToggle,
                    onToggleOffline = viewModel::onOfflineToggle,
                    onToggleLocation = viewModel::onLocationToggle,
                    onToggleActive = viewModel::onActiveToggle,
                )
            }
        }
    }
}
