package com.example.uvapp.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndoorLocationBoundaryTest {
    @Test
    fun `saved radius distinguishes points immediately inside and outside boundary`() {
        val place = indoorLocation(latitude = 0.0, longitude = 0.0, radiusMeters = 100.0)
        val insideLongitude = Math.toDegrees(99.9 / EARTH_RADIUS_METERS)
        val outsideLongitude = Math.toDegrees(100.1 / EARTH_RADIUS_METERS)

        assertTrue(place.contains(latitude = 0.0, longitude = insideLongitude))
        assertFalse(place.contains(latitude = 0.0, longitude = outsideLongitude))
    }

    @Test
    fun `saved radius works across international date line`() {
        val place = indoorLocation(latitude = 0.0, longitude = 179.9998, radiusMeters = 100.0)

        assertTrue(place.contains(latitude = 0.0, longitude = -179.9998))
    }

    @Test
    fun `zero radius contains only exact saved coordinate`() {
        val place = indoorLocation(latitude = -37.8, longitude = 144.96, radiusMeters = 0.0)

        assertTrue(place.contains(latitude = -37.8, longitude = 144.96))
        assertFalse(place.contains(latitude = -37.800_001, longitude = 144.96))
    }

    @Test
    fun `indoor quality accepts exact limits and rejects values beyond them`() {
        val fix =
            LocationFix(
                latitude = -37.8,
                longitude = 144.96,
                accuracyMeters = 50f,
                capturedAtMillis = 10_000L,
                isApproximate = false,
                isMock = true,
            )

        assertTrue(fix.usableForIndoor(now = 40_000L))
        assertFalse(fix.usableForIndoor(now = 40_001L))
        assertFalse(fix.copy(accuracyMeters = 50.01f).usableForIndoor(now = 10_000L))
        assertFalse(fix.copy(isApproximate = true).usableForIndoor(now = 10_000L))
        assertFalse(fix.usableForIndoor(now = 9_999L))
    }

    private fun indoorLocation(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ) =
        IndoorLocation(
            id = "test-place",
            name = "Test place",
            latitude = latitude,
            longitude = longitude,
            createdAtMillis = 0L,
            radiusMeters = radiusMeters,
        )

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
