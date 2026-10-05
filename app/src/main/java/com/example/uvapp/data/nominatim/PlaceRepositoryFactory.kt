package com.example.uvapp.data.nominatim

import android.content.Context
import com.example.uvapp.data.db.AppDatabase
import com.example.uvapp.domain.repository.PlaceRepository

object PlaceRepositoryFactory {
    fun create(context: Context): PlaceRepository {
        val database = AppDatabase.getInstance(context)
        return DefaultPlaceRepository(
            api = NominatimClient.create(),
            dao = database.placeNameDao(),
        )
    }
}
