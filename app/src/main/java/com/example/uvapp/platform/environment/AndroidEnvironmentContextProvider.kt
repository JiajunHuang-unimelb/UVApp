package com.example.uvapp.platform.environment

import com.example.uvapp.domain.environment.EnvironmentContextProvider
import com.example.uvapp.domain.environment.EnvironmentSample
import com.example.uvapp.domain.environment.AcousticReading
import com.example.uvapp.domain.environment.CameraLuminanceReading
import com.example.uvapp.domain.environment.MotionReading
import com.example.uvapp.domain.environment.StepActivityReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Process-scoped bridge from the monitoring service to exposure logic. */
object AndroidEnvironmentContextProvider : EnvironmentContextProvider {
    private val mutable =
        MutableStateFlow(
            EnvironmentSample(
                lux = UNAVAILABLE_LUX,
                nearIndoorLocation = false,
                deviceOccluded = null,
                posture = null,
                isMoving = null,
                stepsSinceStart = null,
                stepsPerMinute = null,
                soundLevelDb = null,
                acousticContext = null,
                cameraLuminancePercent = null,
                cameraLightContext = null,
            ),
        )

    override val samples: StateFlow<EnvironmentSample> = mutable.asStateFlow()

    fun updateLux(lux: Int) {
        mutable.update { it.copy(lux = lux.coerceIn(0, MAX_LUX)) }
    }

    fun updateIndoorProximity(isNear: Boolean) {
        mutable.update { it.copy(nearIndoorLocation = isNear) }
    }

    fun updateDeviceOcclusion(isOccluded: Boolean?) {
        mutable.update { it.copy(deviceOccluded = isOccluded) }
    }

    fun updateMotion(reading: MotionReading?) {
        mutable.update {
            it.copy(
                posture = reading?.posture,
                isMoving = reading?.isMoving,
            )
        }
    }

    fun updateSteps(reading: StepActivityReading?) {
        mutable.update {
            it.copy(
                stepsSinceStart = reading?.stepsSinceStart,
                stepsPerMinute = reading?.averageStepsPerMinute,
            )
        }
    }

    fun updateAcoustic(reading: AcousticReading?) {
        mutable.update {
            it.copy(
                soundLevelDb = reading?.decibelsFullScale,
                acousticContext = reading?.context,
            )
        }
    }

    fun updateCameraLuminance(reading: CameraLuminanceReading?) {
        mutable.update {
            it.copy(
                cameraLuminancePercent = reading?.luminancePercent,
                cameraLightContext = reading?.context,
            )
        }
    }

    /** Unknown evidence is treated as outdoor/high-light so stale indoor pauses can clear. */
    fun markUnavailable() {
        mutable.value =
            EnvironmentSample(
                lux = UNAVAILABLE_LUX,
                nearIndoorLocation = false,
                deviceOccluded = null,
                posture = null,
                isMoving = null,
                stepsSinceStart = null,
                stepsPerMinute = null,
                soundLevelDb = null,
                acousticContext = null,
                cameraLuminancePercent = null,
                cameraLightContext = null,
            )
    }

    private const val MAX_LUX = 100_000
    private const val UNAVAILABLE_LUX = MAX_LUX
}
