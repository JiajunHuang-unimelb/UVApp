package com.example.uvapp.domain.exposure

import com.example.uvapp.domain.environment.AcousticContext
import com.example.uvapp.domain.environment.DevicePosture
import org.junit.Assert.assertEquals
import org.junit.Test

class ExposureContextDetectorTest {
    private val detector = ExposureContextDetector()

    @Test
    fun `occluded phone near saved location does not trust low lux`() {
        val result =
            detector.detect(
                input(
                    lux = 50,
                    deviceOccluded = true,
                    nearIndoorLocation = true,
                    isMoving = false,
                ),
            )

        // Location says where the phone is, not whether its light sensor is exposed.
        assertEquals(ExposureContext.UNKNOWN, result)
    }

    private fun input(
        lux: Int = 500,
        deviceOccluded: Boolean? = false,
        isMoving: Boolean? = null,
        nearIndoorLocation: Boolean = false,
        indoorDetected: Boolean = false,
        acousticContext: AcousticContext? = null,
        posture: DevicePosture? = null,
    ) =
        ExposureContextInput(
            indoorDetected = indoorDetected,
            lux = lux,
            deviceOccluded = deviceOccluded,
            isMoving = isMoving,
            nearIndoorLocation = nearIndoorLocation,
            acousticContext = acousticContext,
            posture = posture,
        )
}
