package com.example.uvapp.data.nominatim

import com.example.uvapp.data.db.PlaceNameDao
import com.example.uvapp.data.db.PlaceNameEntity
import com.example.uvapp.domain.model.Coordinates
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NominatimRepositorySmokeTest {
    @Test
    fun `failed request without cache returns failure for coordinate fallback`() =
        runBlocking {
            val repository =
                DefaultPlaceRepository(
                    api = FakeNominatimApi(IOException("offline")),
                    dao = FakePlaceNameDao(),
                    rateLimiter = NominatimRateLimiter(),
                )

            val result = repository.reverseGeocode(Coordinates(-37.8136, 144.9631))

            assertTrue(result.isFailure)
            assertEquals("offline", result.exceptionOrNull()?.message)
        }

    @Test
    fun `reverse geocodes Melbourne and reuses the Room cache`() =
        runBlocking {
            val api = FakeNominatimApi()
            val dao = FakePlaceNameDao()
            val repository =
                DefaultPlaceRepository(
                    api = api,
                    dao = dao,
                    rateLimiter = NominatimRateLimiter(),
                    nowMillis = { 1_234L },
                )
            val coordinates = Coordinates(latitude = -37.8136, longitude = 144.9631)

            val first = repository.reverseGeocode(coordinates).getOrThrow()
            val second = repository.reverseGeocode(coordinates).getOrThrow()

            assertEquals("Melbourne, City of Melbourne", first.label)
            assertEquals(first, second)
            assertEquals(1, api.callCount)
            assertEquals(1_234L, dao.stored?.fetchedAtMillis)
            assertTrue(dao.stored?.displayName?.contains("Victoria") == true)
        }

    private class FakeNominatimApi(
        private val error: Exception? = null,
    ) : NominatimApi {
        var callCount = 0
            private set

        override suspend fun reverseGeocode(
            latitude: Double,
            longitude: Double,
            format: String,
            addressDetails: Int,
        ): NominatimResponseDto {
            callCount += 1
            error?.let { throw it }
            return NominatimResponseDto(
                displayName = "Melbourne, City of Melbourne, Victoria, Australia",
                address =
                    NominatimAddressDto(
                        suburb = "Melbourne",
                        city = "City of Melbourne",
                        state = "Victoria",
                        country = "Australia",
                    ),
            )
        }
    }

    private class FakePlaceNameDao : PlaceNameDao {
        var stored: PlaceNameEntity? = null

        override suspend fun getPlaceName(locationKey: String): PlaceNameEntity? =
            stored?.takeIf { entity -> entity.locationKey == locationKey }

        override suspend fun upsert(placeName: PlaceNameEntity) {
            stored = placeName
        }
    }
}
