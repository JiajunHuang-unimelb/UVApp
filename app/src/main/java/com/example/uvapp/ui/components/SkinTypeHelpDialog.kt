package com.example.uvapp.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.uvapp.R
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.ui.theme.UvTheme

private const val ARPANSA_SKIN_TYPE_URL =
    "https://www.arpansa.gov.au/sites/default/files/documents/2022-09/Fitzpatrick%20scale.pdf"

/** Read-only Fitzpatrick guidance; opening or closing it leaves the selected skin type unchanged. */
@Composable
fun SkinTypeHelpDialog(onDismiss: () -> Unit) {
    val colors = UvTheme
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.textSecondary,
        title = { Text(stringResource(R.string.skin_type_help_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.skin_type_help_intro))
                SkinType.entries.forEach { type ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = type.displayName(),
                        color = colors.onBackground,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(stringResource(type.helpDescriptionResource()))
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.skin_type_help_sun_protection))
                TextButton(onClick = { uriHandler.openUri(ARPANSA_SKIN_TYPE_URL) }) {
                    Text(stringResource(R.string.skin_type_help_source), color = colors.accent)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.skin_type_help_close), color = colors.accent)
            }
        },
    )
}

private fun SkinType.helpDescriptionResource(): Int = when (this) {
    SkinType.I -> R.string.skin_type_help_i
    SkinType.II -> R.string.skin_type_help_ii
    SkinType.III -> R.string.skin_type_help_iii
    SkinType.IV -> R.string.skin_type_help_iv
    SkinType.V -> R.string.skin_type_help_v
    SkinType.VI -> R.string.skin_type_help_vi
}
