package com.example.uvapp.platform.alerts

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.uvapp.domain.alerts.ExposureAlertGateway

/** Provides the short haptic used when indoor detection automatically pauses exposure. */
class AndroidExposureAlertGateway(
    context: Context,
) : ExposureAlertGateway {
    private val vibrator = context.applicationContext.getSystemService(Vibrator::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun notifyIndoorAutoPause() {
        vibrate(VibrationEffect.createOneShot(SHORT_VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    override fun notifyExposureLimitReached() {
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 250, 120, 350), -1))
        playTone(ToneGenerator.TONE_PROP_BEEP2, LONG_TONE_MILLIS)
    }

    override fun previewReapplyReminder() {
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 180), -1))
        playTone(ToneGenerator.TONE_PROP_ACK, SHORT_TONE_MILLIS)
    }

    override fun previewBandWarning() {
        vibrate(VibrationEffect.createOneShot(SHORT_VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE))
        playTone(ToneGenerator.TONE_PROP_BEEP, SHORT_TONE_MILLIS)
    }

    private fun vibrate(effect: VibrationEffect) {
        val deviceVibrator = vibrator ?: return
        if (!deviceVibrator.hasVibrator()) return

        try {
            deviceVibrator.vibrate(effect)
        } catch (_: SecurityException) {
            // Vibration is a best-effort alert; exposure tracking must continue without it.
        }
    }

    private fun playTone(
        tone: Int,
        durationMillis: Int,
    ) {
        val generator =
            try {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME_PERCENT)
            } catch (_: RuntimeException) {
                return
            }
        if (!generator.startTone(tone, durationMillis)) {
            generator.release()
            return
        }
        mainHandler.postDelayed({ generator.release() }, durationMillis.toLong() + RELEASE_DELAY_MILLIS)
    }

    private companion object {
        const val SHORT_VIBRATION_MILLIS = 250L
        const val SHORT_TONE_MILLIS = 250
        const val LONG_TONE_MILLIS = 600
        const val RELEASE_DELAY_MILLIS = 100L
        const val TONE_VOLUME_PERCENT = 80
    }
}
