package com.example.uvapp.domain.advisor

/**
 * Countdown display formatting. ExposureSessionManager owns timer calculations.
 */
object BurnCalculator {
    /** Shows seconds on every countdown tick. */
    fun formatRemaining(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val hours = minutes / 60
        val mins = minutes % 60
        val seconds = (totalSeconds % 60).toString().padStart(2, '0')
        val mm = mins.toString().padStart(2, '0')
        return if (hours > 0) "$hours:$mm:$seconds" else "$mm:$seconds"
    }
}
