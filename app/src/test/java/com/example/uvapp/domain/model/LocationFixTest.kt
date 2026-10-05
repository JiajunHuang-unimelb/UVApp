package com.example.uvapp.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LocationFixTest {
    @Test
    fun `rejects latitude outside the valid range`() {
        assertThrows(IllegalArgumentException::class.java) {
            validFix().copy(latitude = 90.1)
        }
    }

    @Test
    fun `rejects longitude outside the valid range`() {
        assertThrows(IllegalArgumentException::class.java) {
            validFix().copy(longitude = -180.1)
        }
    }

    @Test
    fun `rejects invalid accuracy`() {
        assertThrows(IllegalArgumentException::class.java) {
            validFix().copy(accuracyMeters = Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validFix().copy(accuracyMeters = -0.1f)
        }
    }

    @Test
    fun `rejects non finite coordinates and negative timestamp`() {
        assertThrows(IllegalArgumentException::class.java) {
            validFix().copy(latitude = Double.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validFix().copy(longitude = Double.POSITIVE_INFINITY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            validFix().copy(capturedAtMillis = -1L)
        }
    }

    @Test
    fun `accepts coordinate accuracy and timestamp boundaries`() {
        val fix =
            validFix().copy(
                latitude = -90.0,
                longitude = 180.0,
                accuracyMeters = 0f,
                capturedAtMillis = 0L,
            )

        assertEquals(-90.0, fix.latitude, 0.0)
        assertEquals(180.0, fix.longitude, 0.0)
        assertEquals(0f, fix.accuracyMeters, 0f)
        assertEquals(0L, fix.capturedAtMillis)
    }

    private fun validFix() =
        LocationFix(
            latitude = -37.8136,
            longitude = 144.9631,
            accuracyMeters = 20f,
            capturedAtMillis = 1_000L,
            isApproximate = false,
            isMock = false,
        )
}
