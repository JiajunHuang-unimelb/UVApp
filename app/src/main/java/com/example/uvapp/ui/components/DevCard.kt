package com.example.uvapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uvapp.data.ApiStatus
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.viewmodel.DevUiState

/**
 * Developer-mode debug card (visible only when enabled in Settings).
 * Mirrors the high-fi page: API status, sensor readouts, test-alert buttons
 * and all eight override toggles with expanded panels.
 */
@Composable
fun DevCard(
    apiStatuses: List<ApiStatus>,
    lux: Int,
    stepsPerMinute: Int,
    dev: DevUiState,
    onToggleSpeed: () -> Unit,
    onToggleUvOverride: () -> Unit,
    onUvOverride: (Double) -> Unit,
    onToggleLightOverride: () -> Unit,
    onLightOverride: (LightContext) -> Unit,
    onToggleAudio: () -> Unit,
    onToggleOccluded: () -> Unit,
    onToggleOffline: () -> Unit,
    onToggleLocation: () -> Unit,
    onToggleActive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UvTheme
    SunCard(
        modifier.fillMaxWidth(),
        containerColor = colors.devCard,
        borderColor = colors.devStroke,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Developer mode", color = colors.devHeader, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Debug & test controls · not shipped", color = colors.devSub, fontSize = 11.sp)
            Spacer(Modifier.height(12.dp))

            // Live status / sensor readouts (monospace)
            Column {
                apiStatuses.forEach { status ->
                    MonoLine("${status.name.padEnd(10)}·  ${status.detail}", colors)
                }
                MonoLine("Light       ·  ${formatThousands(lux)} lux", colors)
                MonoLine("Steps       ·  $stepsPerMinute / min", colors)
            }
            Spacer(Modifier.height(12.dp))

            // Test alert buttons
            Row {
                DevPillButton("Test reapply alert", Modifier.weight(1f), colors)
                Spacer(Modifier.width(8.dp))
                DevPillButton("Test band warning", Modifier.weight(1f), colors)
            }
            Spacer(Modifier.height(12.dp))
            Hairline(Modifier.fillMaxWidth(), colors.devStroke)
            Spacer(Modifier.height(4.dp))

            DevToggleRow("60× countdown speed", dev.speed60x, onToggleSpeed)
            DevToggleRow("Override UV index", dev.overrideUv, onToggleUvOverride)
            if (dev.overrideUv) {
                UvOverridePanel(dev.uvOverride, onUvOverride)
            }
            DevToggleRow("Override light context", dev.overrideLight, onToggleLightOverride)
            if (dev.overrideLight) {
                LightContextPanel(dev.lightOverride, onLightOverride)
            }
            DevToggleRow("Override audio context", dev.overrideAudio, onToggleAudio)
            DevToggleRow("Simulate occluded (in pocket)", dev.simulateOccluded, onToggleOccluded)
            DevToggleRow("Force offline (cache-hit)", dev.forceOffline, onToggleOffline)
            DevToggleRow("Override location", dev.overrideLocation, onToggleLocation)
            DevToggleRow("Simulate active (sweat signal)", dev.simulateActive, onToggleActive)
        }
    }
}

@Composable
private fun MonoLine(text: String, colors: com.example.uvapp.ui.theme.UvColors) {
    Text(
        text,
        color = colors.devHeader,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    )
}

@Composable
private fun DevPillButton(label: String, modifier: Modifier, colors: com.example.uvapp.ui.theme.UvColors) {
    Box(
        modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(colors.devButton)
            .border(1.dp, colors.devButtonStroke, RoundedCornerShape(15.dp))
            .clickable { /* mock: alert would fire here */ },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = colors.devHeader, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DevToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    val colors = UvTheme
    Row(
        Modifier.fillMaxWidth().height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.devHeader, fontSize = 14.sp, modifier = Modifier.weight(1f))
        UvSwitch(checked, onToggle, onColor = colors.devToggleOn, offColor = colors.devToggleOff)
    }
}

@Composable
private fun UvOverridePanel(value: Double, onChange: (Double) -> Unit) {
    val colors = UvTheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.devExpandedBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            "UV index: ${String.format(java.util.Locale.US, "%.1f", value)}",
            color = colors.devHeader,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        SunSlider(
            fraction = (value / 11.0).coerceIn(0.0, 1.0).toFloat(),
            onFractionChange = { f -> onChange((f * 11f).toDouble().let { kotlin.math.round(it * 10) / 10.0 }) },
            trackColor = colors.devSliderTrack,
            fillColor = colors.devToggleOn,
            thumbStroke = colors.devToggleOn,
            thumbOuterRadius = 9.dp,
            thumbInnerRadius = 3.dp,
            thumbStrokeWidth = 2.5.dp,
        )
    }
}

@Composable
private fun LightContextPanel(selected: LightContext, onSelect: (LightContext) -> Unit) {
    val colors = UvTheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.devExpandedBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightContext.entries.forEach { context ->
            val isSelected = context == selected
            val bg = if (isSelected) colors.devToggleOn else colors.devButton
            val fg = if (isSelected) Color.White else colors.devHeader
            Box(
                Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(bg)
                    .border(if (isSelected) 0.dp else 1.dp, colors.devButtonStroke, RoundedCornerShape(15.dp))
                    .clickable { onSelect(context) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            ) {
                Text(
                    context.label,
                    color = fg,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
