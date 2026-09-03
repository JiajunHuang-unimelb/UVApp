package com.example.uvapp.data.nominatim

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in single-request smoke test for the public Nominatim service. */
class NominatimLiveApiTest {
    @Test
    fun `reverse geocodes a real Melbourne coordinate`() =
        runBlocking {
            assumeTrue(
                "Set RUN_LIVE_NOMINATIM_TEST=true to run this network test",
                System.getenv("RUN_LIVE_NOMINATIM_TEST") == "true",
            )

            val response = NominatimClient.create().reverseGeocode(-37.8136, 144.9631)
            val place = DefaultNominatimMapper().toDomain(response)

            assertTrue(place.label.isNotBlank())
            println("Nominatim live response")
            println("label=${place.label}")
            println("displayName=${place.displayName}")
        }
}
