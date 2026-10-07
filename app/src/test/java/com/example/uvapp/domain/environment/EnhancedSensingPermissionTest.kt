package com.example.uvapp.domain.environment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhancedSensingPermissionTest {
    @Test
    fun `denying one optional permission preserves other sensor capabilities`() {
        val allGranted =
            EnhancedSensingPermissions(
                microphoneGranted = true,
                cameraGranted = true,
                activityRecognitionGranted = true,
            )

        val microphoneDenied = allGranted.copy(microphoneGranted = false)
        val cameraDenied = allGranted.copy(cameraGranted = false)
        val activityDenied = allGranted.copy(activityRecognitionGranted = false)

        assertTrue(microphoneDenied.cameraGranted)
        assertTrue(microphoneDenied.activityRecognitionGranted)
        assertTrue(cameraDenied.microphoneGranted)
        assertTrue(cameraDenied.activityRecognitionGranted)
        assertTrue(activityDenied.microphoneGranted)
        assertTrue(activityDenied.cameraGranted)
        assertTrue(microphoneDenied.hasAnyGrantedCapability)
        assertTrue(cameraDenied.hasAnyGrantedCapability)
        assertTrue(activityDenied.hasAnyGrantedCapability)
        assertFalse(EnhancedSensingPermissions().hasAnyGrantedCapability)
    }
}
