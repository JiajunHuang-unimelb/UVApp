package com.example.uvapp.domain.model

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
