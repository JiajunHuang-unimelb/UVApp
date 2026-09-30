package com.example.uvapp.domain.environment

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraLuminanceClassifierTest {
    @Test
    fun `dark Y plane is classified as dark`() {
        val buffer = ByteBuffer.wrap(ByteArray(16) { 30 })

        val reading =
            CameraLuminanceClassifier.sampleYPlane(
                buffer = buffer,
                width = 4,
                height = 4,
                rowStride = 4,
                pixelStride = 1,
                sampleStep = 1,
            )

        assertEquals(CameraLightContext.DARK, reading?.context)
        assertEquals(6, reading?.luminancePercent)
    }

    @Test
    fun `bright Y plane is classified as bright`() {
        val buffer = ByteBuffer.wrap(ByteArray(16) { 220.toByte() })

        val reading =
            CameraLuminanceClassifier.sampleYPlane(
                buffer = buffer,
                width = 4,
                height = 4,
                rowStride = 4,
                pixelStride = 1,
                sampleStep = 1,
            )

        assertEquals(CameraLightContext.BRIGHT, reading?.context)
        assertEquals(93, reading?.luminancePercent)
    }

    @Test
    fun `row and pixel stride are respected`() {
        val bytes = ByteArray(16)
        listOf(0, 2, 8, 10).forEach { bytes[it] = 125 }

        val reading =
            CameraLuminanceClassifier.sampleYPlane(
                buffer = ByteBuffer.wrap(bytes),
                width = 2,
                height = 2,
                rowStride = 8,
                pixelStride = 2,
                sampleStep = 1,
            )

        assertEquals(50, reading?.luminancePercent)
        assertEquals(CameraLightContext.NORMAL, reading?.context)
    }

    @Test
    fun `invalid dimensions are unavailable`() {
        assertNull(
            CameraLuminanceClassifier.sampleYPlane(
                buffer = ByteBuffer.allocate(0),
                width = 0,
                height = 0,
                rowStride = 0,
                pixelStride = 0,
            ),
        )
    }
}
