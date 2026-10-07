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

    @Test
    fun `occluded moving phone uses conservative unknown context`() {
        val result =
            detector.detect(
                input(
                    lux = 25,
                    deviceOccluded = true,
                    nearIndoorLocation = true,
                    isMoving = true,
                ),
            )

        // Motion makes a pocket or bag more likely, so the low lux is not environmental evidence.
        assertEquals(ExposureContext.UNKNOWN, result)
    }

    @Test
    fun `sound posture and motion can reject a misleading low light reading`() {
        val activeSound =
            detector.detect(
                input(lux = 8_000, acousticContext = AcousticContext.ACTIVE_OUTDOOR_LIKELY),
            )
        val faceDown =
            detector.detect(
                input(lux = 8_000, posture = DevicePosture.FACE_DOWN),
            )
        val moving =
            detector.detect(
                input(lux = 8_000, isMoving = true),
            )
        val consistentShade =
            detector.detect(
                input(
                    lux = 8_000,
                    acousticContext = AcousticContext.QUIET_INDOOR_LIKELY,
                    posture = DevicePosture.FACE_UP,
                    isMoving = false,
                ),
            )

        // Contradictory sensors keep dose calculation conservative; consistent evidence keeps shade.
        assertEquals(ExposureContext.UNKNOWN, activeSound)
        assertEquals(ExposureContext.UNKNOWN, faceDown)
        assertEquals(ExposureContext.UNKNOWN, moving)
        assertEquals(ExposureContext.SHADE, consistentShade)
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
