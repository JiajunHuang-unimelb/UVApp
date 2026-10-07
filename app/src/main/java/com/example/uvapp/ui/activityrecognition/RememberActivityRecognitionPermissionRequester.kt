package com.example.uvapp.ui.activityrecognition

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
fun rememberActivityRecognitionPermissionRequester(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: (permanentlyDenied: Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnGranted by rememberUpdatedState(onPermissionGranted)
    val currentOnDenied by rememberUpdatedState(onPermissionDenied)

    var permissionRequestedBefore by rememberSaveable { mutableStateOf(false) }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var awaitingSettingsReturn by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        if (context.hasActivityRecognitionPermission()) {
            permanentlyDenied = false
            currentOnGranted()
        } else {
            val activity = context.findActivity()
            val canShowRationale = activity != null &&
                    ActivityCompat.shouldShowRequestPermissionRationale(
                        activity,
                        Manifest.permission.ACTIVITY_RECOGNITION
                    )
            permanentlyDenied = permissionRequestedBefore && !canShowRationale
            currentOnDenied(permanentlyDenied)
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingSettingsReturn) {
                awaitingSettingsReturn = false
                if (context.hasActivityRecognitionPermission()) {
                    permanentlyDenied = false
                    currentOnGranted()
                } else {
                    currentOnDenied(true)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    return remember(context, launcher, permanentlyDenied) {
        {
            when {
                context.hasActivityRecognitionPermission() -> currentOnGranted()
                permanentlyDenied -> {
                    awaitingSettingsReturn = true
                    context.openApplicationSettings()
                }
                else -> {
                    permissionRequestedBefore = true
                    launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            }
        }
    }
}

private fun Context.hasActivityRecognitionPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACTIVITY_RECOGNITION
    ) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun Context.openApplicationSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    startActivity(intent)
}
