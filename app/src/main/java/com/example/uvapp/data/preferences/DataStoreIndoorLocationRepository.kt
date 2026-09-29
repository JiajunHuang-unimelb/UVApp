package com.example.uvapp.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.uvapp.domain.model.IndoorLocationsData
import com.example.uvapp.domain.repository.IndoorLocationRepository
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

private val Context.indoorLocationsStore by preferencesDataStore(name = "indoor_locations")

class DataStoreIndoorLocationRepository(private val store: DataStore<Preferences>) : IndoorLocationRepository {
    constructor(context: Context) : this(context.applicationContext.indoorLocationsStore)
    private val key = stringPreferencesKey("data")
    private val json = Json { ignoreUnknownKeys = true }
    override val data = store.data.map { values -> values[key]?.let { json.decodeFromString<IndoorLocationsData>(it) } ?: IndoorLocationsData() }
    override suspend fun update(transform: (IndoorLocationsData) -> IndoorLocationsData) {
        store.edit { values ->
            val old = values[key]?.let { json.decodeFromString<IndoorLocationsData>(it) } ?: IndoorLocationsData()
            values[key] = json.encodeToString(transform(old))
        }
    }
}
