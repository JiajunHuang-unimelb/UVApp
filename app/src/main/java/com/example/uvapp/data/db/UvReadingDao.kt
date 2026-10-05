package com.example.uvapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** Database boundary for cached hourly UV forecasts. */
@Dao
interface UvReadingDao {
    @Query(
        """
        SELECT locationKey, latitude, longitude, MAX(fetchedAtMillis) AS fetchedAtMillis
        FROM uv_readings
        GROUP BY locationKey, latitude, longitude
        ORDER BY fetchedAtMillis DESC
        """,
    )
    suspend fun getForecastLocations(): List<CachedForecastLocation>

    @Query(
        """
        SELECT * FROM uv_readings
        WHERE locationKey = :locationKey
        ORDER BY forecastTimeMillis ASC
        """,
    )
    fun observeForecast(locationKey: String): Flow<List<UvReadingEntity>>

    @Query(
        """
        SELECT * FROM uv_readings
        WHERE locationKey = :locationKey
        ORDER BY forecastTimeMillis ASC
        """,
    )
    suspend fun getForecast(locationKey: String): List<UvReadingEntity>

    @Query("SELECT MAX(fetchedAtMillis) FROM uv_readings WHERE locationKey = :locationKey")
    suspend fun latestFetchTime(locationKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadings(readings: List<UvReadingEntity>)

    @Query("DELETE FROM uv_readings WHERE locationKey = :locationKey")
    suspend fun deleteForecast(locationKey: String)

    @Transaction
    suspend fun replaceForecast(
        locationKey: String,
        readings: List<UvReadingEntity>,
    ) {
        require(readings.all { it.locationKey == locationKey }) {
            "All readings must belong to locationKey=$locationKey"
        }
        deleteForecast(locationKey)
        insertReadings(readings)
    }
}

data class CachedForecastLocation(
    val locationKey: String,
    val latitude: Double,
    val longitude: Double,
    val fetchedAtMillis: Long,
)
