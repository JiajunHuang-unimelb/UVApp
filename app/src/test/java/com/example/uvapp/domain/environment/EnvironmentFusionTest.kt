package com.example.uvapp.domain.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentFusionTest {
    @Test
    fun `saved location is strong indoor evidence`() {
        val evidence = evaluate(nearIndoorLocation = true)

        assertTrue(EnvironmentFusion.supportsIndoor(evidence))
        assertTrue(EnvironmentContributor.SAVED_INDOOR_LOCATION in evidence.contributors)
    }

    @Test
    fun `occlusion is strong pocket evidence`() {
        assertTrue(EnvironmentFusion.supportsIndoor(evaluate(deviceOccluded = true)))
    }

    @Test
    fun `one weak signal is insufficient`() {
        assertFalse(
            EnvironmentFusion.supportsIndoor(
                evaluate(cameraLightContext = CameraLightContext.DARK),
            ),
        )
    }

    @Test
    fun `dark camera and quiet sound combine into indoor support`() {
        val evidence =
            evaluate(
                cameraLightContext = CameraLightContext.DARK,
                acousticContext = AcousticContext.QUIET_INDOOR_LIKELY,
            )

        assertEquals(0.40, evidence.indoorConfidence, 0.001)
        assertTrue(EnvironmentFusion.supportsIndoor(evidence))
    }

    @Test
    fun `bright camera and active sound reduce weak evidence`() {
        val evidence =
            evaluate(
                cameraLightContext = CameraLightContext.BRIGHT,
                acousticContext = AcousticContext.ACTIVE_OUTDOOR_LIKELY,
                posture = DevicePosture.FACE_DOWN,
                isMoving = false,
            )

        assertEquals(0.0, evidence.indoorConfidence, 0.001)
        assertFalse(EnvironmentFusion.supportsIndoor(evidence))
    }

    private fun evaluate(
        nearIndoorLocation: Boolean? = false,
        deviceOccluded: Boolean? = false,
        cameraLightContext: CameraLightContext? = null,
        acousticContext: AcousticContext? = null,
        posture: DevicePosture? = null,
        isMoving: Boolean? = null,
    ): EnvironmentEvidence =
        EnvironmentFusion.evaluate(
            nearIndoorLocation = nearIndoorLocation,
            deviceOccluded = deviceOccluded,
            cameraLightContext = cameraLightContext,
            acousticContext = acousticContext,
            posture = posture,
            isMoving = isMoving,
        )
}
