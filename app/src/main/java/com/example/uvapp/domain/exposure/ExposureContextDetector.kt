package com.example.uvapp.domain.exposure

import com.example.uvapp.domain.environment.AcousticContext
import com.example.uvapp.domain.environment.DevicePosture
import com.example.uvapp.domain.model.LightContext

/**
 * Sensor/environment evidence used to determine the current exposure context.
 *
 * This class only determines whether the user is likely:
 * - directly exposed to sunlight,
 * - in shade,
 * - indoors,
 * - or in an uncertain environment.
 *
 * It does not control the exposure session lifecycle.
 */
data class ExposureContextInput(
    val indoorDetected: Boolean,
    val lux: Int,
    val deviceOccluded: Boolean?,
    val isMoving: Boolean?,
    val nearIndoorLocation: Boolean,
    val acousticContext: AcousticContext?,
    val posture: DevicePosture?,
)

/**
 * Determines the user's current UV exposure context from multiple
 * environmental signals.
 *
 * The detector deliberately does not treat low lux as proof of being indoors.
 * This is important when the phone is occluded, for example when the user
 * is walking outdoors with the phone inside a jeans pocket.
 */
class ExposureContextDetector {

    fun detect(input: ExposureContextInput): ExposureContext {
        /*
         * An already-confirmed indoor state has the highest priority.
         * The indoor state itself is established by the ViewModel's
         * indoor transition/debounce logic.
         */
        if (input.indoorDetected) {
            return ExposureContext.INDOOR
        }

        /*
         * If the phone is occluded, its light sensor may be measuring
         * the inside of a pocket or bag rather than the environment.
         *
         * Therefore, low lux must NOT automatically mean indoors.
         */
        if (input.deviceOccluded == true) {
            return detectOccludedContext(input)
        }

        /*
         * Low light can also be misleading when the proximity sensor does not
         * report occlusion. A face-down phone, confirmed movement, or sustained
         * outdoor-like sound conflicts with treating that reading as shade.
         */
        val hasConflictingLowLightEvidence =
            input.isMoving == true ||
                input.posture == DevicePosture.FACE_DOWN ||
                input.acousticContext == AcousticContext.ACTIVE_OUTDOOR_LIKELY
        if (input.lux in 1 until LightContext.SHADE_MAX_LUX && hasConflictingLowLightEvidence) {
            return ExposureContext.UNKNOWN
        }

        /*
         * When the phone is not occluded, the light sensor is a useful
         * primary signal.
         */
        return when {
            input.lux >= LightContext.SHADE_MAX_LUX ->
                ExposureContext.DIRECT_SUN

            input.lux > 0 ->
                ExposureContext.SHADE

            else ->
                ExposureContext.UNKNOWN
        }
    }

    private fun detectOccludedContext(
        input: ExposureContextInput,
    ): ExposureContext {
        /*
         * A user moving while the phone is occluded is a strong reason
         * not to classify low lux as indoor.
         *
         * Example:
         *   outdoor walking + phone in jeans pocket + lux = 20
         *
         * The light sensor alone would suggest "indoor", but motion
         * indicates that the low reading may simply be caused by occlusion.
         */
        if (input.isMoving == true) {
            return ExposureContext.UNKNOWN
        }

        /*
         * A saved location cannot make an occluded lux reading trustworthy:
         * the user may still be outdoors near their home with the phone in a
         * pocket. Keep the result conservative until unobstructed evidence is
         * available or the indoor state was confirmed before the occlusion.
         */
        return ExposureContext.UNKNOWN
    }
}
