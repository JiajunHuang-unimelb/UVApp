package com.example.uvapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uvapp.domain.model.*
import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.viewmodel.*
import kotlin.math.roundToInt

/**
 * Saved indoor places. Settings (settings = true) shows the full list inside its Sensing card;
 * Home shows a compact card with the status line and the "I'm indoors here" button.
 */
@Composable
fun IndoorLocationsPanel(vm: IndoorLocationsViewModel, state: IndoorLocationsUiState, requestSave: () -> Unit, enableSuggestions: () -> Unit, settings: Boolean = false) {
    val colors = UvTheme
    var editing by remember { mutableStateOf<IndoorLocation?>(null) }
    var deleting by remember { mutableStateOf<IndoorLocation?>(null) }
    var name by remember { mutableStateOf("") }
    val panelModifier =
        if (settings) {
            Modifier.fillMaxWidth()
        } else {
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(12.dp))
                .padding(16.dp)
        }
    Column(panelModifier) {
        if (settings) {
            Text("Indoor locations", color = colors.onBackground, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                if (state.data.locations.isEmpty()) "Automatic indoor detection needs a saved location. Manual tracking is available."
                else "Used to pause tracking when you're inside.",
                color = colors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
        } else {
            Text("Indoor locations", style = MaterialTheme.typography.titleMedium)
            if (state.data.locations.isEmpty()) {
                Text("Automatic indoor detection needs a saved location. Manual tracking is available.", color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Saved: " + state.data.locations.joinToString(", ") { it.name }, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!state.data.invitationDismissed && state.data.locations.isEmpty()) {
            Text("Save Home, University or Work to get started.", color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
            // Plain clickable text (not TextButton) so it lines up with the text above it.
            Text(
                "Not now",
                color = if (settings) colors.accent else Color.Unspecified,
                lineHeight = 20.sp,
                modifier = Modifier.clickable { vm.dismissInvitation() }.padding(vertical = 14.dp),
            )
        }
        if (settings) {
            Spacer(Modifier.height(8.dp))
            SettingsSwitchRow(
                title = "Suggest indoor places",
                subtitle = "Ask to save a place after I pause in low light",
                checked = state.data.suggestionsEnabled,
                onToggle = { if (!state.data.suggestionsEnabled) enableSuggestions() else vm.enableSuggestions(false) },
            )
            state.data.locations.forEach { place ->
                HorizontalDivider(color = colors.outline)
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(place.name, color = colors.onBackground, fontSize = 15.sp)
                        Text("Within ${place.radiusMeters.roundToInt()} m", color = colors.textSecondary, fontSize = 12.sp)
                    }
                    TextButton(onClick = { editing = place; name = place.name }) { Text("Edit", color = colors.accent, fontWeight = FontWeight.Bold) }
                    TextButton(onClick = { deleting = place }) { Text("Remove", color = colors.accent, fontWeight = FontWeight.Bold) }
                }
            }
            if (state.data.locations.isNotEmpty()) HorizontalDivider(color = colors.outline)
        }
        Spacer(Modifier.height(8.dp))
        FilledTonalButton(onClick = requestSave, enabled = !state.loading && !state.saving) { Text(if (state.loading) "Finding location…" else "I’m indoors here") }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = requestSave) { Text("Retry location") } }
    }
    editing?.let { place ->
        AlertDialog(
            onDismissRequest = { editing = null },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.textSecondary,
            title = { Text("Edit location") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    Row { INDOOR_PLACE_PRESETS.forEach { label -> TextButton(onClick = { name = label }) { Text(label, color = colors.accent) } } }
                }
            },
            confirmButton = { TextButton(onClick = { vm.rename(place.id, name); editing = null }, enabled = name.isNotBlank()) { Text("Save", color = colors.accent) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel", color = colors.textSecondary) } },
        )
    }
    deleting?.let { place ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = colors.surface,
            titleContentColor = colors.onBackground,
            textContentColor = colors.textSecondary,
            title = { Text("Remove ${place.name}?") },
            text = { Text("Automatic indoor detection will stop using this place.") },
            confirmButton = { TextButton(onClick = { vm.delete(place.id); deleting = null }) { Text("Remove", color = colors.accent) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = colors.textSecondary) } },
        )
    }
}

/** Quick-pick names offered when saving or editing an indoor place. */
private val INDOOR_PLACE_PRESETS = listOf("Home", "University", "Work")

/** Developer-only switch for the in-memory demo radius; lives in the Settings Developer card. */
@Composable
fun IndoorDemoToggle(vm: IndoorLocationsViewModel, state: IndoorLocationsUiState) {
    // Same row as the other Settings toggles, so it lines up with them.
    SettingsSwitchRow(
        title = "Demo: University Square radius",
        subtitle = if (state.demoEnabled) "Demonstration coordinates only; not a saved indoor building." else null,
        checked = state.demoEnabled,
        onToggle = { vm.setDemoEnabled(!state.demoEnabled) },
    )
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
            Row { INDOOR_PLACE_PRESETS.forEach { label -> TextButton(onClick = { vm.setName(label) }) { Text(label) } } }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = vm::confirm, enabled = !state.saving) { Text(if (state.saving) "Saving…" else "Save") } }, dismissButton = { TextButton(onClick = { vm.dismiss() }, enabled = !state.saving) { Text("Not now") } })
}
