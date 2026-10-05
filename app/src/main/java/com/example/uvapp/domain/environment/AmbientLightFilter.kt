package com.example.uvapp.domain.environment

import kotlin.math.roundToInt

/** Removes isolated ambient-light spikes with a small rolling median window. */
class AmbientLightFilter(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
) {
    private val samples = ArrayDeque<Int>()

    init {
        require(windowSize > 0 && windowSize % 2 == 1) {
            "Ambient-light window size must be a positive odd number"
        }
    }

    /** Returns the filtered lux value, or `null` when the hardware sample is invalid. */
    fun update(lux: Float): Int? {
        if (!lux.isFinite() || lux < 0f) return null

        samples.addLast(lux.roundToInt())
        while (samples.size > windowSize) samples.removeFirst()
        return samples.sorted()[samples.size / 2]
    }

    private companion object {
        const val DEFAULT_WINDOW_SIZE = 5
    }
}
