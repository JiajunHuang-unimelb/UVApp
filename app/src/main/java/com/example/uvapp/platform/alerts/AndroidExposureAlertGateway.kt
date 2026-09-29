package com.example.uvapp.platform.alerts

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.uvapp.domain.alerts.ExposureAlertGateway

/** Provides the short haptic used when indoor detection automatically pauses exposure. */
class AndroidExposureAlertGateway(
    context: Context,
) : ExposureAlertGateway {
    private val vibrator = context.applicationContext.getSystemService(Vibrator::class.java)

    override fun notifyIndoorAutoPause() {
        val deviceVibrator = vibrator ?: return
        if (!deviceVibrator.hasVibrator()) return

        try {
            deviceVibrator.vibrate(
                VibrationEffect.createOneShot(
                    VIBRATION_DURATION_MILLIS,
                    VibrationEffect.DEFAULT_AMPLITUDE,
                ),
            )
        } catch (_: SecurityException) {
            // Vibration is a best-effort alert; exposure tracking must continue without it.
        }
    }

    private companion object {
        const val VIBRATION_DURATION_MILLIS = 250L
    }
}
