package com.example.uvapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uvapp.R
import com.example.uvapp.ui.theme.UvTheme

internal object DataSourceLinks {
    const val OPEN_METEO = "https://open-meteo.com/"
    const val CC_BY = "https://creativecommons.org/licenses/by/4.0/"
    const val NOMINATIM = "https://nominatim.org/"
    const val OPEN_STREET_MAP_COPYRIGHT = "https://www.openstreetmap.org/copyright"
    const val ODBL = "https://opendatacommons.org/licenses/odbl/1-0/"
}

@Composable
fun UvDataAttribution(modifier: Modifier = Modifier) {
    SourceLink(stringResource(R.string.uv_data_attribution), DataSourceLinks.OPEN_METEO, modifier)
}

@Composable
fun PlaceDataAttribution(modifier: Modifier = Modifier) {
    SourceLink(stringResource(R.string.place_data_attribution), DataSourceLinks.OPEN_STREET_MAP_COPYRIGHT, modifier)
}

@Composable
private fun SourceLink(label: String, url: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Text(
        text = label,
        modifier = modifier.minimumInteractiveComponentSize().clickable(role = Role.Button) { uriHandler.openUri(url) },
        color = UvTheme.accent,
        fontSize = 12.sp,
        textDecoration = TextDecoration.Underline,
    )
}

@Composable
fun DataSourcesDialog(onDismiss: () -> Unit) {
    val colors = UvTheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.onBackground,
        textContentColor = colors.textSecondary,
        title = { Text(stringResource(R.string.data_sources)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.uv_forecasts_source), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.uv_source_description))
                UvDataAttribution()
                SourceLink(stringResource(R.string.cc_by_license), DataSourceLinks.CC_BY)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.place_names_source), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.place_source_description))
                SourceLink(stringResource(R.string.nominatim_service), DataSourceLinks.NOMINATIM)
                PlaceDataAttribution()
                SourceLink(stringResource(R.string.odbl_license), DataSourceLinks.ODBL)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.data_processing_description))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.exposure_estimates), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.exposure_estimate_note))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close_data_sources), color = colors.accent)
            }
        },
    )
}
