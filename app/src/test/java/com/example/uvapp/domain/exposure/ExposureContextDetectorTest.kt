package com.example.uvapp.domain.exposure

import com.example.uvapp.domain.environment.AcousticContext
import com.example.uvapp.domain.environment.DevicePosture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExposureContextDetectorTest {
    private val detector = ExposureContextDetector()

    @Test
    fun `confirmed indoor context takes priority over conflicting sensor readings`() {
        assertEquals(
            ExposureContext.INDOOR,
            detector.detect(input(indoorDetected = true, lux = 100_000, deviceOccluded = true, isMoving = true)),
        )
    }

    @Test
    fun `light classification respects zero and shade boundary`() {
        mapOf(-1 to ExposureContext.UNKNOWN, 0 to ExposureContext.UNKNOWN,
            1 to ExposureContext.SHADE, 19_999 to ExposureContext.SHADE,
            20_000 to ExposureContext.DIRECT_SUN, 100_000 to ExposureContext.DIRECT_SUN,
        ).forEach { (lux, expected) -> assertEquals("lux=$lux", expected, detector.detect(input(lux = lux))) }
    }

    @Test
    fun `saved location alone never establishes indoor context`() {
        val evidence = input(nearIndoorLocation = true)
        assertTrue(detector.canEnterIndoor(evidence))
        assertEquals(ExposureContext.SHADE, detector.detect(evidence))
        assertFalse(detector.canEnterIndoor(evidence.copy(nearIndoorLocation = false)))
    }

    @Test
    fun `indoor entry requires low light with no contradicting evidence`() {
        val evidence = input(nearIndoorLocation = true)
        listOf(
            evidence.copy(deviceOccluded = true), evidence.copy(isMoving = true),
            evidence.copy(posture = DevicePosture.FACE_DOWN),
            evidence.copy(acousticContext = AcousticContext.ACTIVE_OUTDOOR_LIKELY),
        ).forEach { assertFalse("Must reject $it", detector.canEnterIndoor(it)) }
        assertTrue(detector.canEnterIndoor(evidence.copy(deviceOccluded = null, isMoving = null)))
    }

    @Test
    fun `indoor entry and exit thresholds provide a hysteresis band`() {
        assertFalse(detector.canEnterIndoor(input(lux = -1, nearIndoorLocation = true)))
        assertTrue(detector.canEnterIndoor(input(lux = 0, nearIndoorLocation = true)))
        assertTrue(detector.canEnterIndoor(input(lux = 999, nearIndoorLocation = true)))
        assertFalse(detector.canEnterIndoor(input(lux = 1_000, nearIndoorLocation = true)))
        assertFalse(detector.canExitIndoor(input(lux = 2_000, nearIndoorLocation = true)))
        assertTrue(detector.canExitIndoor(input(lux = 2_001, nearIndoorLocation = true)))
    }

    @Test
    fun `covered light cannot trigger exit but leaving saved location can`() {
        val evidence = input(lux = 100_000, nearIndoorLocation = true, deviceOccluded = true)
        assertFalse(detector.canExitIndoor(evidence))
        assertTrue(detector.canExitIndoor(evidence.copy(nearIndoorLocation = false)))
    }

    @Test
    fun `outdoor activity does not reject unobstructed bright sunlight`() {
        assertEquals(ExposureContext.DIRECT_SUN, detector.detect(input(
            lux = 20_000, isMoving = true, acousticContext = AcousticContext.ACTIVE_OUTDOOR_LIKELY,
        )))
    }

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
