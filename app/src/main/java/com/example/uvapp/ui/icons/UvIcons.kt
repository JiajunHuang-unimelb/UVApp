package com.example.uvapp.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.example.uvapp.R

/**
 * Icon facade. The actual glyphs live as vector drawables in
 * res/drawable/ic_*.xml (visible/editable in the Android Studio preview),
 * converted from the exact SVG paths used by the high-fidelity mockups.
 * Tint is applied by the Icon() call site, so the fill colour here is a
 * placeholder.
 */
object UvIcons {
    val Refresh: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_refresh)

    val Search: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_search)

    val ChevronDown: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_chevron_down)

    val Locate: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_locate)

    val Home: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_home)

    val Forecast: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_forecast)

    val Settings: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_settings)

    val Warning: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_warning)

    val Close: ImageVector
        @Composable get() = ImageVector.vectorResource(R.drawable.ic_close)
}
