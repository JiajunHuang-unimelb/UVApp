package com.example.uvapp.domain.environment

import java.nio.ByteBuffer
import kotlin.math.roundToInt

enum class CameraLightContext {
    DARK,
    NORMAL,
    BRIGHT,
}

data class CameraLuminanceReading(
    val luminancePercent: Int,
    val context: CameraLightContext,
)

/** Samples the Y plane and converts camera luminance into a weak contextual signal. */
object CameraLuminanceClassifier {
    fun sampleYPlane(
        buffer: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        sampleStep: Int = DEFAULT_SAMPLE_STEP,
    ): CameraLuminanceReading? {
        if (width <= 0 || height <= 0 || rowStride <= 0 || pixelStride <= 0 || sampleStep <= 0) return null

        val pixels = buffer.duplicate()
        val base = pixels.position()
        val limit = pixels.limit()
        var total = 0L
        var count = 0
        var row = 0
        while (row < height) {
            var column = 0
            while (column < width) {
                val index = base + row * rowStride + column * pixelStride
                if (index in base until limit) {
                    total += pixels.get(index).toInt() and 0xff
                    count++
                }
                column += sampleStep
            }
            row += sampleStep
        }
        if (count == 0) return null

        val averageY = total.toDouble() / count
        val percent = (((averageY - YUV_BLACK) / YUV_RANGE) * 100.0).roundToInt().coerceIn(0, 100)
        val context =
            when {
                percent < DARK_PERCENT -> CameraLightContext.DARK
                percent > BRIGHT_PERCENT -> CameraLightContext.BRIGHT
                else -> CameraLightContext.NORMAL
            }
        return CameraLuminanceReading(luminancePercent = percent, context = context)
    }

    private const val DEFAULT_SAMPLE_STEP = 8
    private const val YUV_BLACK = 16.0
    private const val YUV_RANGE = 219.0
    private const val DARK_PERCENT = 25
    private const val BRIGHT_PERCENT = 70
}
