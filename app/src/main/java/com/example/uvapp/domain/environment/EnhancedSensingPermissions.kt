package com.example.uvapp.domain.environment

/** Permission-backed optional sensors are independent and may degrade separately. */
data class EnhancedSensingPermissions(
    val microphoneGranted: Boolean = false,
    val cameraGranted: Boolean = false,
    val activityRecognitionGranted: Boolean = false,
) {
    val hasAnyGrantedCapability: Boolean
        get() = microphoneGranted || cameraGranted || activityRecognitionGranted
}
