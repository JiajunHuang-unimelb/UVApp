package com.example.uvapp.data.repository

import android.content.Context
import com.example.uvapp.data.db.AppDatabase
import com.example.uvapp.data.openmeteo.OpenMeteoClient
import com.example.uvapp.domain.repository.UvRepository

/** Creates the production repository with Open-Meteo networking and Room caching. */
object UvRepositoryFactory {
    fun create(context: Context): UvRepository {
        val database = AppDatabase.getInstance(context)
        return DefaultUvRepository(
            api = OpenMeteoClient.create(),
            dao = database.uvReadingDao(),
        )
    }
}
