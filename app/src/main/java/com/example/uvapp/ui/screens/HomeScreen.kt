package com.example.uvapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.uvapp.ui.components.indoorStatusMessage
import com.example.uvapp.ui.components.SafeTimerCard
import com.example.uvapp.ui.components.TopChrome
import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.viewmodel.MainUiState
import com.example.uvapp.viewmodel.MainViewModel
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
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
            onResetClick = { showResetConfirm = true }
        }
        ExposureStatus.COMPLETE -> {
            //Save/Discard
            onPrimaryAction = { showResetConfirm = true }
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
                title = { Text("End exposure session?") },
                text = {
                    Column {
                        Text("Save this session to your sun log, or discard it?")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showResetConfirm = false }) {
                                Text("Cancel")
                            }
                            TextButton(onClick = {
                                showResetConfirm = false
                                viewModel.onResetSession(save = false)
                            }) {
                                Text("Discard")
                            }
                            TextButton(onClick = {
                                showResetConfirm = false
                                viewModel.onResetSession(save = true)
                            }) {
                                Text("Save")
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {}
            )
        }
    }
}
