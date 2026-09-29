package com.example.uvapp.platform.environment

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.example.uvapp.domain.environment.SoundLevelClassifier
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Samples relative sound level while the app is visible. It never stores microphone audio. */
class AndroidMicrophoneEnvironmentMonitor(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private var task: Future<*>? = null
    @Volatile private var recorder: AudioRecord? = null

    @Synchronized
    @SuppressLint("MissingPermission")
    fun start() {
        if (task?.isDone == false) return
        if (
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            AndroidEnvironmentContextProvider.updateAcoustic(null)
            return
        }

        val minimumBuffer =
            AudioRecord.getMinBufferSize(
                SAMPLE_RATE_HZ,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
        if (minimumBuffer <= 0) {
            AndroidEnvironmentContextProvider.updateAcoustic(null)
            return
        }

        val audioRecord =
            try {
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE_HZ)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build(),
                    ).setBufferSizeInBytes(minimumBuffer * 2)
                    .build()
            } catch (_: RuntimeException) {
                AndroidEnvironmentContextProvider.updateAcoustic(null)
                return
            }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            AndroidEnvironmentContextProvider.updateAcoustic(null)
            return
        }

        recorder = audioRecord
        task =
            executor.submit {
                val classifier = SoundLevelClassifier()
                val samples = ShortArray(minimumBuffer / Short.SIZE_BYTES)
                try {
                    audioRecord.startRecording()
                    while (!Thread.currentThread().isInterrupted) {
                        val count = audioRecord.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                        if (count > 0) {
                            AndroidEnvironmentContextProvider.updateAcoustic(classifier.classify(samples, count))
                        }
                    }
                } catch (_: IllegalStateException) {
                    AndroidEnvironmentContextProvider.updateAcoustic(null)
                } catch (_: SecurityException) {
                    AndroidEnvironmentContextProvider.updateAcoustic(null)
                } finally {
                    releaseRecorder(audioRecord)
                }
            }
    }

    @Synchronized
    fun stop() {
        task?.cancel(true)
        recorder?.let { audioRecord ->
            try {
                if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) audioRecord.stop()
            } catch (_: IllegalStateException) {
                // Stopping is best-effort; the worker's finally block releases the recorder.
            }
        }
        AndroidEnvironmentContextProvider.updateAcoustic(null)
    }

    private fun releaseRecorder(audioRecord: AudioRecord) {
        try {
            if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) audioRecord.stop()
        } catch (_: IllegalStateException) {
            // The recorder may already have been stopped by the other lifecycle path.
        }
        audioRecord.release()
        if (recorder === audioRecord) {
            recorder = null
            task = null
        }
    }

    private companion object {
        const val SAMPLE_RATE_HZ = 16_000
    }
}
