package com.example.uvapp.data.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UvReadingDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: UvReadingDao

    @Before
    fun createDatabase() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    InstrumentationRegistry.getInstrumentation().targetContext,
                    AppDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dao = database.uvReadingDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun replaceForecastSortsRowsAndPreservesOtherLocations() =
        runBlocking {
            dao.insertReadings(listOf(entity(OTHER_LOCATION, forecastTime = 500L)))

            dao.replaceForecast(
                LOCATION_KEY,
                listOf(
                    entity(LOCATION_KEY, forecastTime = 200L, fetchedAt = 2_000L),
                    entity(LOCATION_KEY, forecastTime = 100L, fetchedAt = 2_000L),
                ),
            )

            assertEquals(listOf(100L, 200L), dao.getForecast(LOCATION_KEY).map { it.forecastTimeMillis })
            assertEquals(2_000L, dao.latestFetchTime(LOCATION_KEY))
            assertEquals(1, dao.getForecast(OTHER_LOCATION).size)

            dao.replaceForecast(
                LOCATION_KEY,
                listOf(entity(LOCATION_KEY, forecastTime = 300L, fetchedAt = 3_000L)),
            )

            assertEquals(listOf(300L), dao.getForecast(LOCATION_KEY).map { it.forecastTimeMillis })
            assertEquals(3_000L, dao.latestFetchTime(LOCATION_KEY))
            assertEquals(1, dao.getForecast(OTHER_LOCATION).size)
        }

    private fun entity(
        locationKey: String,
        forecastTime: Long,
        fetchedAt: Long = 1_000L,
    ): UvReadingEntity =
        UvReadingEntity(
            locationKey = locationKey,
            forecastTimeMillis = forecastTime,
            latitude = -37.81,
            longitude = 144.96,
            uvIndex = 3.2,
            clearSkyUvIndex = 3.8,
            cloudCoverPercent = 25,
            fetchedAtMillis = fetchedAt,
        )

    private companion object {
        const val LOCATION_KEY = "-37.81,144.96"
        const val OTHER_LOCATION = "-33.87,151.21"
    }
}
