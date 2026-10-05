package com.example.uvapp.platform.environment

import com.example.uvapp.domain.environment.EnvironmentContextProvider
import com.example.uvapp.domain.environment.EnvironmentSample
import com.example.uvapp.domain.environment.DevicePosture
import com.example.uvapp.domain.environment.AcousticContext
import com.example.uvapp.domain.environment.StepActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Deterministic source for unit tests and developer-controlled environmental input. */
class MockEnvironmentContextProvider(
    initialLux: Int = 38_200,
    initiallyNearIndoorLocation: Boolean = false,
    initiallyDeviceOccluded: Boolean? = false,
) : EnvironmentContextProvider {
    private val _samples =
        MutableStateFlow(
            EnvironmentSample(
                lux = initialLux,
                nearIndoorLocation = initiallyNearIndoorLocation,
                deviceOccluded = initiallyDeviceOccluded,
            ),
        )

    override val samples: StateFlow<EnvironmentSample> = _samples.asStateFlow()

    fun setLux(lux: Int) {
        _samples.update { it.copy(lux = lux.coerceIn(0, MAX_LUX)) }
    }

    fun setNearIndoorLocation(isNear: Boolean) {
        _samples.update { it.copy(nearIndoorLocation = isNear) }
    }

    fun setDeviceOccluded(isOccluded: Boolean?) {
        _samples.update { it.copy(deviceOccluded = isOccluded) }
    }

    fun setMotion(
        posture: DevicePosture?,
        isMoving: Boolean?,
    ) {
        _samples.update { it.copy(posture = posture, isMoving = isMoving) }
    }

    fun setStepActivity(
        stepsSinceStart: Int?,
        stepsPerMinute: Int?,
        recentSteps: Int? = null,
        lastStepElapsedMillis: Long? = null,
        activity: StepActivity = StepActivity.UNKNOWN,
    ) {
        _samples.update {
            it.copy(
                stepsSinceStart = stepsSinceStart,
                recentSteps = recentSteps,
                stepsPerMinute = stepsPerMinute,
                lastStepElapsedMillis = lastStepElapsedMillis,
                stepActivity = activity,
            )
        }
    }

    fun setAcoustic(
        soundLevelDb: Double?,
        context: AcousticContext?,
    ) {
        _samples.update { it.copy(soundLevelDb = soundLevelDb, acousticContext = context) }
    }

    private companion object {
        const val MAX_LUX = 100_000
    }
}
