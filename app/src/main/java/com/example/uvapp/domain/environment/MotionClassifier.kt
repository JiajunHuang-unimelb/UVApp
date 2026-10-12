package com.example.uvapp.domain.environment

import kotlin.math.abs
import kotlin.math.sqrt

enum class DevicePosture {
    FACE_UP,
    FACE_DOWN,
    UPRIGHT,
    TILTED,
}

data class MotionReading(
    val posture: DevicePosture,
    val isMoving: Boolean,
)

/**
 * Classifies accelerometer samples using posture plus sustained deviation from the window median.
 *
 * A single knock on the table produces one or two outlying samples. Counting how many recent
 * samples stray from the median, instead of using the window's variance, keeps such a spike from
 * marking the phone as moving for the whole window, while walking or shaking keeps enough samples
 * outlying to be detected within about a second.
 */
class MotionClassifier(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
) {
    private val magnitudes = ArrayDeque<Double>()
    private var moving = false

    init {
        require(windowSize >= MIN_SAMPLES)
    }

    fun update(
        x: Float,
        y: Float,
        z: Float,
    ): MotionReading? {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null

        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
        if (magnitude <= 0.0) return null
        magnitudes.addLast(magnitude)
        while (magnitudes.size > windowSize) magnitudes.removeFirst()

        val zRatio = z / magnitude
        val posture =
            when {
                zRatio >= FACE_UP_RATIO -> DevicePosture.FACE_UP
                zRatio <= -FACE_UP_RATIO -> DevicePosture.FACE_DOWN
                abs(zRatio) <= UPRIGHT_RATIO -> DevicePosture.UPRIGHT
                else -> DevicePosture.TILTED
            }
        moving = magnitudes.size >= MIN_SAMPLES && isSustainedMovement()
        return MotionReading(posture = posture, isMoving = moving)
    }

    private fun isSustainedMovement(): Boolean {
        val median = magnitudes.sorted().let { sorted -> sorted[sorted.size / 2] }
        val outlying =
            magnitudes
                .takeLast(RECENT_SAMPLES)
                .count { magnitude -> abs(magnitude - median) >= MOVEMENT_DEVIATION }
        // Hysteresis: start on clear evidence, stop once the shaking has mostly left the recent samples.
        return outlying >= if (moving) STOP_OUTLYING_SAMPLES else START_OUTLYING_SAMPLES
    }

    private companion object {
        const val DEFAULT_WINDOW_SIZE = 20
        const val MIN_SAMPLES = 5
        const val FACE_UP_RATIO = 0.72
        const val UPRIGHT_RATIO = 0.35
        const val MOVEMENT_DEVIATION = 0.6
        const val RECENT_SAMPLES = 8
        const val START_OUTLYING_SAMPLES = 4
        const val STOP_OUTLYING_SAMPLES = 2
    }
}
