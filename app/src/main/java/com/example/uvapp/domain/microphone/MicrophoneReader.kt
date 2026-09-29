package com.example.uvapp.domain.microphone
import com.example.uvapp.domain.model.AudioReading
import kotlinx.coroutines.flow.Flow

/**
 * Domain boundary for microphone sensor, continuously produce audio level readings.
 */
interface MicrophoneSensor {
    val readings: Flow<MicrophoneReadResult>

    fun startSampling()
    fun stopSampling()
}

/**
 * Domain-level outcomes for microphone sensor, UI can handle each case.
 */
sealed interface MicrophoneReadResult {
    data class Success(val reading: AudioReading) : MicrophoneReadResult
    data object PermissionDenied : MicrophoneReadResult
    data object Unavailable : MicrophoneReadResult
}