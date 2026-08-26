package com.example.uvapp.ui.main

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.uvapp.platform.location.FusedCurrentLocationProvider
import java.util.Locale

@Composable
fun MainScreen(
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val factory =
        remember(context) {
            viewModelFactory {
                initializer {
                    MainViewModel(
                        locationProvider = FusedCurrentLocationProvider(context.applicationContext),
                    )
                }
            }
        }
    val viewModel: MainViewModel = viewModel(factory = factory)
    val state by viewModel.uiState.collectAsState()
    var hasLocationPermission by remember { mutableStateOf(context.hasLocationPermission()) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }
    var permissionDenied by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            hasLocationPermission = permissions.values.any { it }
            permissionDenied = !hasLocationPermission
            if (hasLocationPermission) {
                viewModel.refresh()
            }
        }

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            viewModel.refresh()
        } else if (!permissionRequested) {
            permissionRequested = true
            permissionLauncher.launch(LOCATION_PERMISSIONS)
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Current location")

        if (!hasLocationPermission) {
            Text(
                if (permissionDenied) {
                    "Location permission was denied. It is needed to fetch UV for your location."
                } else {
                    "Location permission is needed to fetch UV for your location."
                },
            )
        } else if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            if (state.latitude != null && state.longitude != null) {
                Text(
                    String.format(
                        Locale.US,
                        "Coordinates: %.4f, %.4f",
                        state.latitude,
                        state.longitude,
                    ),
                )
            }
            state.uvIndex?.let { Text("UV index: $it") }
            state.observedAt?.let { Text("Observed at: $it") }
            state.errorMessage?.let { Text("Request failed: $it") }
        }

        Button(
            onClick = {
                if (hasLocationPermission) {
                    viewModel.refresh()
                } else {
                    permissionRequested = true
                    permissionLauncher.launch(LOCATION_PERMISSIONS)
                }
            },
            enabled = !state.isLoading,
        ) {
            Text(
                when {
                    !hasLocationPermission -> "Grant location permission"
                    state.errorMessage != null -> "Retry"
                    else -> "Refresh"
                },
            )
        }
    }
}

private val LOCATION_PERMISSIONS =
    arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

private fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
