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

/** Classifies accelerometer samples using posture plus short-window magnitude variance. */
class MotionClassifier(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
) {
    private val magnitudes = ArrayDeque<Double>()

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
        val isMoving =
            magnitudes.size >= MIN_SAMPLES &&
                standardDeviation(magnitudes) >= MOVEMENT_STANDARD_DEVIATION
        return MotionReading(posture = posture, isMoving = isMoving)
    }

    private fun standardDeviation(values: Collection<Double>): Double {
        val average = values.average()
        return sqrt(values.sumOf { value -> (value - average) * (value - average) } / values.size)
    }

    private companion object {
        const val DEFAULT_WINDOW_SIZE = 20
        const val MIN_SAMPLES = 5
        const val FACE_UP_RATIO = 0.72
        const val UPRIGHT_RATIO = 0.35
        const val MOVEMENT_STANDARD_DEVIATION = 0.45
    }
}
