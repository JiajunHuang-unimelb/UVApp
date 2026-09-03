package com.example.uvapp.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface PlaceNameDao {
    @Query("SELECT * FROM place_names WHERE locationKey = :locationKey LIMIT 1")
    suspend fun getPlaceName(locationKey: String): PlaceNameEntity?

    @Upsert
    suspend fun upsert(placeName: PlaceNameEntity)
}
