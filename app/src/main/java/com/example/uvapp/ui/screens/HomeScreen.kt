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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uvapp.R
import com.example.uvapp.domain.exposure.ExposureStatus
import com.example.uvapp.ui.components.CachedIndicator
import com.example.uvapp.ui.components.ContextCard
import com.example.uvapp.ui.components.DevCard
import com.example.uvapp.ui.components.ErrorBanner
import com.example.uvapp.ui.components.IndoorStatusBanner
import com.example.uvapp.ui.components.SafeTimerCard
import com.example.uvapp.ui.components.TopChrome
import com.example.uvapp.ui.components.indoorStatusMessage
import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.viewmodel.MainUiState
import com.example.uvapp.viewmodel.MainViewModel

/** Home tab: hero, address, safe timer, exposure indicator, cached note, dev card. */
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    state: MainUiState,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
    indoorContent: @Composable () -> Unit = {},
) {
    var showResetConfirm by rememberSaveable { mutableStateOf(false) }

    val onPrimaryAction: () -> Unit
    val onResetClick: (() -> Unit)?

    when (state.exposureStatus) {
        ExposureStatus.NOT_STARTED -> {
            onPrimaryAction = viewModel::onStartExposure
            onResetClick = null
        }
        ExposureStatus.RUNNING -> {
            onPrimaryAction = viewModel::onPauseExposure
            onResetClick = null
        }
        ExposureStatus.PAUSED -> {
            onPrimaryAction = viewModel::onResumeExposure
            // PAUSED点击Reset，打开弹窗，不直接重置
            onResetClick = { showResetConfirm = true }
        }
        ExposureStatus.COMPLETE -> {
            onPrimaryAction = viewModel::onResetTimer
            onResetClick = null
        }
    }

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
                exposureStatus = state.exposureStatus,
                onPrimaryAction = onPrimaryAction,
                onReset = onResetClick,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.exposure_estimate_note),
                color = UvTheme.textSecondary,
                fontSize = 12.sp,
            )

            val indoorMessage = indoorStatusMessage(state.pauseReason, state.indoorDetected)
            if (indoorMessage != null) {
                Spacer(Modifier.height(10.dp))
                IndoorStatusBanner(indoorMessage)
            }

            Spacer(Modifier.height(12.dp))
            ContextCard(
                context = state.lightReadingContext,
                lux = state.displayLux,
                onLuxChange = viewModel::onLuxChange,
                interactive = !state.exposureStarted,
            )

            Spacer(Modifier.height(12.dp))
            indoorContent()
            if (state.isCached) {
                Spacer(Modifier.height(10.dp))
                CachedIndicator()
            }

            if (state.devModeEnabled) {
                Spacer(Modifier.height(12.dp))
                DevCard(
                    apiStatuses = state.apiStatuses,
                    lux = state.displayLux,
                    deviceOccluded = state.effectiveDeviceOccluded,
                    devicePosture = state.devicePosture,
                    isMoving = state.effectiveIsMoving,
                    stepsSinceStart = state.stepsSinceStart,
                    recentSteps = state.recentSteps,
                    stepsPerMinute = state.stepsPerMinute,
                    stepActivity = state.stepActivity,
                    soundLevelDb = state.soundLevelDb,
                    acousticContext = state.effectiveAcousticContext,
                    nearIndoorLocation = state.nearIndoorLocation,
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
                    onTestReapplyAlert = viewModel::onTestReapplyAlert,
                    onTestBandWarning = viewModel::onTestBandWarning,
                )
            }
        }

        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text("Reset timer?") },
                text = { Text("This will discard your current exposure session and reset the timer. This action cannot be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        showResetConfirm = false
                        viewModel.onResetTimer()
                    }) {
                        Text("Reset")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirm = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
