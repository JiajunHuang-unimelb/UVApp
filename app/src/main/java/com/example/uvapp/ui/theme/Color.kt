package com.example.uvapp.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.uvapp.viewmodel.AccentColor

// Semantic colour palettes only. Everything else lives inline in UvTheme.kt,
// so there are no one-to-one "constant -> field" duplicates.

/** User-selectable main (accent) colour: light/dark pair per preset. */
fun accentPalette(accent: AccentColor): Pair<Color, Color> = when (accent) {
    AccentColor.AMBER -> Color(0xFFE8590C) to Color(0xFFFF8A50)
    AccentColor.TEAL -> Color(0xFF0E7C6B) to Color(0xFF5FD6C4)
    AccentColor.BLUE -> Color(0xFF2E63B8) to Color(0xFF7FAEFF)
    AccentColor.GREEN -> Color(0xFF3A7D44) to Color(0xFF84D08C)
    AccentColor.PURPLE -> Color(0xFF7C4DCC) to Color(0xFFBE9DF5)
    AccentColor.ROSE -> Color(0xFFC7466A) to Color(0xFFF28FA8)
}

/** One UV band: text (strong), tint (surface shade, derived), dot (marker). */
data class BandPalette(val text: Color, val tint: Color, val dot: Color)

private fun lightPalette(text: Color, dot: Color) =
    BandPalette(text, lerp(text, Color.White, 0.85f), dot)

private fun darkPalette(text: Color, dot: Color) =
    BandPalette(text, lerp(text, Color.Black, 0.74f), dot)

object BandPalettes {
    fun light(band: com.example.uvapp.domain.model.UvBand): BandPalette = when (band) {
        com.example.uvapp.domain.model.UvBand.LOW -> lightPalette(Color(0xFF1E7A34), Color(0xFF4CAF50))
        com.example.uvapp.domain.model.UvBand.MODERATE -> lightPalette(Color(0xFF8A6100), Color(0xFFF9A825))
        com.example.uvapp.domain.model.UvBand.HIGH -> lightPalette(Color(0xFFE65100), Color(0xFFFB8C00))
        com.example.uvapp.domain.model.UvBand.VERY_HIGH -> lightPalette(Color(0xFFC62828), Color(0xFFE53935))
        com.example.uvapp.domain.model.UvBand.EXTREME -> lightPalette(Color(0xFF7B1FA2), Color(0xFFAB47BC))
    }

    fun dark(band: com.example.uvapp.domain.model.UvBand): BandPalette = when (band) {
        com.example.uvapp.domain.model.UvBand.LOW -> darkPalette(Color(0xFF8FE3A0), Color(0xFF66BB6A))
        com.example.uvapp.domain.model.UvBand.MODERATE -> darkPalette(Color(0xFFFFD966), Color(0xFFFFD54F))
        com.example.uvapp.domain.model.UvBand.HIGH -> darkPalette(Color(0xFFFFB066), Color(0xFFFFA726))
        com.example.uvapp.domain.model.UvBand.VERY_HIGH -> darkPalette(Color(0xFFFF8A80), Color(0xFFEF5350))
        com.example.uvapp.domain.model.UvBand.EXTREME -> darkPalette(Color(0xFFD9A6F2), Color(0xFFCE93D8))
    }
}
