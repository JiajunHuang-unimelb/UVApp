package com.example.uvapp.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.uvapp.domain.model.*
import com.example.uvapp.viewmodel.*

@Composable
fun IndoorLocationsPanel(vm: IndoorLocationsViewModel, state: IndoorLocationsUiState, requestSave: () -> Unit, enableSuggestions: () -> Unit, settings: Boolean = false, developerMode: Boolean = false) {
    var editing by remember { mutableStateOf<IndoorLocation?>(null) }
    var deleting by remember { mutableStateOf<IndoorLocation?>(null) }
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Indoor locations", style = MaterialTheme.typography.titleMedium)
        if (developerMode) {
            Row { Checkbox(checked = state.demoEnabled, onCheckedChange = vm::setDemoEnabled); Text("Demo: University Square radius") }
            if (state.demoEnabled) Text("Demonstration coordinates only; not a saved indoor building.")
        }
        if (state.data.locations.isEmpty()) Text("Automatic indoor detection needs a saved location. Manual tracking is available.")
        if (!state.data.invitationDismissed && state.data.locations.isEmpty()) {
            Text("Save Home, University or Work to get started.")
            TextButton(onClick = { vm.dismissInvitation() }) { Text("Not now") }
        }
        Button(onClick = requestSave, enabled = !state.loading && !state.saving) { Text(if (state.loading) "Finding location…" else "I’m indoors here") }
        if (settings) {
            Row { Checkbox(checked = state.data.suggestionsEnabled, onCheckedChange = { if (it) enableSuggestions() else vm.enableSuggestions(false) }); Text("Suggest saving after I pause in low light") }
            state.data.locations.forEach { place ->
                Text(place.name)
                Row {
                    TextButton(onClick = { editing = place; name = place.name }) { Text("Rename") }
                    TextButton(onClick = { deleting = place }) { Text("Delete") }
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = requestSave) { Text("Retry location") } }
    }
    editing?.let { place -> AlertDialog(onDismissRequest = { editing = null }, title = { Text("Rename location") }, text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }) }, confirmButton = { TextButton(onClick = { vm.rename(place.id, name); editing = null }, enabled = name.isNotBlank()) { Text("Save") } }, dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }) }
    deleting?.let { place -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${place.name}?") }, confirmButton = { TextButton(onClick = { vm.delete(place.id); deleting = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }) }
}

@Composable
fun IndoorSuggestionDialog(vm: IndoorLocationsViewModel, state: IndoorLocationsUiState) {
    val pending = state.data.pending ?: return
    if (!state.visible) return
    val duplicate = state.data.locations.firstOrNull { it.contains(pending.latitude, pending.longitude) }
    AlertDialog(onDismissRequest = { if (!state.saving) vm.dismiss() }, title = { Text("Save this indoor location?") }, text = {
        Column {
            Text("Save the location captured when you requested it. GPS proximity is only an estimate of being indoors.")
            duplicate?.let { Text("Already near ${it.name}. Use this place or enter a new name to rename it."); TextButton(onClick = { vm.dismiss() }) { Text("Use ${it.name}") } }
            OutlinedTextField(value = state.name, onValueChange = vm::setName, label = { Text("Location name") }, enabled = !state.saving)
            Row { listOf("Home", "University", "Work").forEach { label -> TextButton(onClick = { vm.setName(label) }) { Text(label) } } }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = vm::confirm, enabled = !state.saving) { Text(if (state.saving) "Saving…" else "Save") } }, dismissButton = { TextButton(onClick = { vm.dismiss() }, enabled = !state.saving) { Text("Not now") } })
}
