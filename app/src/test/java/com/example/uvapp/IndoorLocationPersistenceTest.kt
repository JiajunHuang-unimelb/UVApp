package com.example.uvapp

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.example.uvapp.data.preferences.DataStoreIndoorLocationRepository
import com.example.uvapp.domain.model.IndoorLocation
import com.example.uvapp.domain.model.IndoorSuggestion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Assert.assertEquals

class IndoorLocationPersistenceTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun `locations and pending candidate survive reopening disk store`() = runBlocking {
        val file = java.io.File(folder.root, "places.preferences_pb")
        val job = SupervisorJob()
        val first = DataStoreIndoorLocationRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file })
        val place = IndoorLocation("home", "Home", -37.8, 144.96, 1000)
        val pending = IndoorSuggestion("candidate", -37.81, 144.95, 2000, "University")
        first.update { it.copy(locations = listOf(place), pending = pending, suggestionsEnabled = true, invitationDismissed = true) }
        job.cancelAndJoin()
        val secondJob = SupervisorJob()
        try {
            val reopened = DataStoreIndoorLocationRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + secondJob)) { file })
            val data = reopened.data.first()
            assertEquals(place, data.locations.single())
            assertEquals(pending, data.pending)
            assertEquals(true, data.suggestionsEnabled)
            assertEquals(true, data.invitationDismissed)
        } finally { secondJob.cancelAndJoin() }
    }
}
