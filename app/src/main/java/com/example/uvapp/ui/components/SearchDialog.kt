package com.example.uvapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uvapp.domain.model.PlaceSearchResult
import com.example.uvapp.ui.icons.UvIcons
import com.example.uvapp.ui.theme.UvTheme

/**
 * Address-search overlay: scrim over the page content (bottom nav stays
 * visible, per the high-fi drawing), then the dialog. The keyboard Search key
 * submits the query; results come from Nominatim via the ViewModel.
 */
@Composable
fun SearchDialogOverlay(
    query: String,
    results: List<PlaceSearchResult>,
    status: String?,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    onSelectPlace: (PlaceSearchResult) -> Unit,
    onUseCurrentLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UvTheme
    Box(modifier.fillMaxSize()) {
        // Scrim (tap to dismiss)
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.scrim)
                .clickable { onDismiss() },
        )
        // Dialog
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 150.dp)
                .width(360.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
                .padding(20.dp),
        ) {
            Text(
                "Search address",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(14.dp))

            // Focused search field
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface)
                    .border(1.5.dp, colors.accent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(UvIcons.Search, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = colors.onBackground, fontSize = 15.sp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    cursorBrush = SolidColor(colors.accent),
                )
                if (query.isNotEmpty()) {
                    Icon(
                        UvIcons.Close,
                        contentDescription = "Clear search",
                        tint = colors.textSecondary,
                        modifier = Modifier.minimumInteractiveComponentSize().size(20.dp).clickable { onQueryChange("") },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            // Use current location
            Row(
                Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .height(36.dp)
                    .clickable { onUseCurrentLocation() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(UvIcons.Locate, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Use current location", fontSize = 15.sp, color = colors.onBackground)
            }
            Spacer(Modifier.height(6.dp))
            Hairline(Modifier.fillMaxWidth())
            Spacer(Modifier.height(2.dp))

            // Search status or results
            if (status != null) {
                Spacer(Modifier.height(24.dp))
                Text(status, color = colors.textSecondary, fontSize = 14.sp)
            }
            results.forEach { place ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectPlace(place) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        place.displayName,
                        fontSize = 15.sp,
                        color = colors.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            PlaceDataAttribution()
        }
    }
}
