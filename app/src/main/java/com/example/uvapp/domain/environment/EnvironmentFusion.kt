package com.example.uvapp.domain.environment

data class EnvironmentEvidence(
    val indoorConfidence: Double,
    val contributors: Set<EnvironmentContributor>,
)

enum class EnvironmentContributor {
    SAVED_INDOOR_LOCATION,
    DEVICE_OCCLUDED,
    CAMERA_DARK,
    QUIET_SOUND,
    FACE_DOWN,
    STATIONARY,
}

/** Weighted fusion for supporting low-light indoor or pocket detection. */
object EnvironmentFusion {
    fun evaluate(
        nearIndoorLocation: Boolean?,
        deviceOccluded: Boolean?,
        cameraLightContext: CameraLightContext?,
        acousticContext: AcousticContext?,
        posture: DevicePosture?,
        isMoving: Boolean?,
    ): EnvironmentEvidence {
        var confidence = 0.0
        val contributors = mutableSetOf<EnvironmentContributor>()

        fun add(
            contributor: EnvironmentContributor,
            weight: Double,
        ) {
            confidence += weight
            contributors += contributor
        }

        if (nearIndoorLocation == true) add(EnvironmentContributor.SAVED_INDOOR_LOCATION, LOCATION_WEIGHT)
        if (deviceOccluded == true) add(EnvironmentContributor.DEVICE_OCCLUDED, OCCLUSION_WEIGHT)
        if (cameraLightContext == CameraLightContext.DARK) add(EnvironmentContributor.CAMERA_DARK, CAMERA_DARK_WEIGHT)
        if (acousticContext == AcousticContext.QUIET_INDOOR_LIKELY) add(EnvironmentContributor.QUIET_SOUND, QUIET_WEIGHT)
        if (posture == DevicePosture.FACE_DOWN) add(EnvironmentContributor.FACE_DOWN, FACE_DOWN_WEIGHT)
        if (isMoving == false) add(EnvironmentContributor.STATIONARY, STATIONARY_WEIGHT)

        if (cameraLightContext == CameraLightContext.BRIGHT) confidence -= CAMERA_BRIGHT_PENALTY
        if (acousticContext == AcousticContext.ACTIVE_OUTDOOR_LIKELY) confidence -= ACTIVE_SOUND_PENALTY
        if (isMoving == true) confidence -= MOVEMENT_PENALTY

        return EnvironmentEvidence(
            indoorConfidence = confidence.coerceIn(0.0, 1.0),
            contributors = contributors,
        )
    }

    fun supportsIndoor(evidence: EnvironmentEvidence): Boolean =
        evidence.indoorConfidence >= INDOOR_SUPPORT_THRESHOLD

    private const val LOCATION_WEIGHT = 0.80
    private const val OCCLUSION_WEIGHT = 0.80
    private const val CAMERA_DARK_WEIGHT = 0.20
    private const val QUIET_WEIGHT = 0.20
    private const val FACE_DOWN_WEIGHT = 0.15
    private const val STATIONARY_WEIGHT = 0.05
    private const val CAMERA_BRIGHT_PENALTY = 0.25
    private const val ACTIVE_SOUND_PENALTY = 0.15
    private const val MOVEMENT_PENALTY = 0.05
    private const val INDOOR_SUPPORT_THRESHOLD = 0.35
}
