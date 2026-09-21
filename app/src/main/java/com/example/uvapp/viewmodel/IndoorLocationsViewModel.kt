package com.example.uvapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.uvapp.domain.location.*
import com.example.uvapp.domain.model.*
import com.example.uvapp.domain.repository.IndoorLocationRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID

data class IndoorLocationsUiState(val data: IndoorLocationsData = IndoorLocationsData(), val loading: Boolean = false, val saving: Boolean = false, val error: String? = null, val name: String = "", val visible: Boolean = true, val demoEnabled: Boolean = false)

class IndoorLocationsViewModel(
    private val repository: IndoorLocationRepository,
    private val location: CurrentLocationProvider,
    private val main: MainViewModel,
    private val notifySuggestion: (IndoorSuggestion?) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val mutable = MutableStateFlow(IndoorLocationsUiState())
    val state = mutable.asStateFlow()
    private var fix: LocationFix? = main.state.value.locationFix
    private var request: Job? = null
    private var asked = false
    private var notified: String? = null
    private var notificationsEnabled = true

    init {
        viewModelScope.launch {
            repository.data.catch { mutable.update { it.copy(error = "Unable to read saved locations.") } }.collect { data ->
                mutable.update { it.copy(data = data, name = if (it.data.pending?.id != data.pending?.id) data.pending?.name.orEmpty() else it.name) }
                proximity()
                deliverNotification()
            }
        }
        viewModelScope.launch {
            var previous = main.state.value
            main.state.collect { current ->
                if (current.exposureSessionId != previous.exposureSessionId) asked = false
                if (current.locationFix != previous.locationFix) { fix = current.locationFix; proximity() }
                if (previous.exposureRunning && current.pauseReason == com.example.uvapp.domain.exposure.ExposurePauseReason.MANUAL && current.displayLux < 1_000 && !asked && state.value.data.suggestionsEnabled) {
                    asked = true
                    val candidate = fix?.takeIf { it.usableForIndoor(now()) }
                    if (candidate != null && state.value.data.pending == null && state.value.data.locations.none { it.contains(candidate.latitude, candidate.longitude) }) {
                        try { persistCandidate(candidate) } catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.update { it.copy(error = "Unable to prepare location suggestion.") } }
                    }
                }
                previous = current
            }
        }
        viewModelScope.launch { while (isActive) { delay(1_000); proximity() } }
    }

    private fun proximity() {
        val candidate = fix?.takeIf { it.usableForIndoor(now()) }
        val locations = state.value.data.locations + if (state.value.demoEnabled && main.state.value.devModeEnabled) listOf(DEMO_LOCATION) else emptyList()
        main.onIndoorProximity(candidate?.let { valid -> locations.any { it.contains(valid.latitude, valid.longitude) } })
    }

    fun setVisible(visible: Boolean) { mutable.update { it.copy(visible = visible) }; deliverNotification() }
    fun setDemoEnabled(enabled: Boolean) { mutable.update { it.copy(demoEnabled = enabled) }; proximity() }
    fun setNotificationsEnabled(enabled: Boolean) { notificationsEnabled = enabled; deliverNotification() }
    private fun deliverNotification() {
        val pending = state.value.data.pending
        if (pending == null || !notificationsEnabled) { notifySuggestion(null); notified = null }
        else if (!state.value.visible && notified != pending.id) { notifySuggestion(pending); notified = pending.id }
    }

    private fun change(block: (IndoorLocationsData) -> IndoorLocationsData) = viewModelScope.launch {
        mutable.update { it.copy(saving = true, error = null) }
        try { repository.update(block) } catch (e: CancellationException) { throw e } catch (_: Exception) { mutable.update { it.copy(error = "Unable to save changes. Please retry.") } }
        finally { mutable.update { it.copy(saving = false) } }
    }
    private suspend fun persistCandidate(candidate: LocationFix) {
        repository.update { if (it.pending != null) it else it.copy(pending = IndoorSuggestion(UUID.randomUUID().toString(), candidate.latitude, candidate.longitude, candidate.capturedAtMillis)) }
    }
    fun requestSave() {
        if (state.value.data.pending != null || request?.isActive == true) return
        request = viewModelScope.launch {
            mutable.update { it.copy(loading = true, error = null) }
            try {
                val result = location.getCurrentLocation()
                val candidate = (result as? LocationResult.Success)?.fix
                require(candidate != null && candidate.usableForIndoor(now())) { "A fresh precise location with accuracy within 50 m is needed. Check location permission and retry." }
                fix = candidate
                proximity()
                persistCandidate(candidate)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { mutable.update { it.copy(error = e.message ?: "Location unavailable. Retry.") } }
            finally { mutable.update { it.copy(loading = false) } }
        }
    }
    fun permissionDenied() { mutable.update { it.copy(error = "Location permission is needed to save this place.") } }
    fun setName(name: String) {
        val value = name.take(80)
        mutable.update { it.copy(name = value) }
        val id = state.value.data.pending?.id ?: return
        viewModelScope.launch {
            try { repository.update { data -> data.copy(pending = data.pending?.let { if (it.id == id) it.copy(name = value) else it }) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(error = "Unable to save draft name.") } }
        }
    }
    fun confirm() {
        val name = state.value.name.trim()
        if (name.isEmpty()) { mutable.update { it.copy(error = "Enter a location name.") }; return }
        change { data ->
            val pending = data.pending ?: return@change data
            val existing = data.locations.firstOrNull { it.contains(pending.latitude, pending.longitude) }
            val locations = if (existing != null) data.locations.map { if (it.id == existing.id) it.copy(name = name) else it }
            else data.locations + IndoorLocation(pending.id, name, pending.latitude, pending.longitude, now())
            data.copy(locations = locations, pending = null, invitationDismissed = true)
        }
    }
    fun dismiss() = change { it.copy(pending = null) }
    fun dismissInvitation() = change { it.copy(invitationDismissed = true) }
    fun enableSuggestions(enabled: Boolean) = change { it.copy(suggestionsEnabled = enabled) }
    fun rename(id: String, name: String) { if (name.isNotBlank()) change { data -> data.copy(locations = data.locations.map { if (it.id == id) it.copy(name = name.trim().take(80)) else it }) } }
    fun delete(id: String) = change { data -> data.copy(locations = data.locations.filterNot { it.id == id }) }
    companion object {
        // Verified campus marker https://maps.unimelb.edu.au/point?poi=1001526284
        // Demo radius only: this outdoor landmark is not a claim of indoor occupancy.
        val DEMO_LOCATION = IndoorLocation("demo-campus", "Demo: University Square marker", -37.7986, 144.9602, 0L)
    }
}
