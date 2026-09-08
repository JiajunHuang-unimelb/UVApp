package com.example.uvapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uvapp.ui.theme.UvTheme
import kotlin.math.abs

/** Material-style switch (44 x 24, knob 18) matching the high-fi drawings. */
@Composable
fun UvSwitch(
    checked: Boolean,
    onToggle: () -> Unit,
    onColor: Color,
    offColor: Color = UvTheme.outline,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(44.dp, 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (checked) onColor else offColor)
            .clickable(onClick = onToggle),
    ) {
        val knobLeft = if (checked) 26.dp else 5.dp
        Box(
            Modifier
                .offset(x = knobLeft, y = 3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Radio row (Fitzpatrick types, theme choice). */
@Composable
fun SettingsRadioRow(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = UvTheme
    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, if (selected) colors.accent else colors.outline, CircleShape),
            )
            if (selected) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(colors.accent))
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = colors.onBackground,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** SPF slider with preset ticks None/15/30/50/100. */
@Composable
fun SpfSlider(spf: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = UvTheme
    val steps = listOf(0, 15, 30, 50, 100)
    val fractions = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    Column(modifier.fillMaxWidth()) {
        SunSlider(
            fraction = fractions[steps.indexOf(spf).coerceAtLeast(0)],
            onFractionChange = { f ->
                val index = fractions.indices.minByOrNull { abs(fractions[it] - f) } ?: 0
                onChange(steps[index])
            },
            trackColor = colors.outline,
            fillColor = colors.accent,
            thumbStroke = colors.accent,
            trackHeight = 4.dp,
            thumbOuterRadius = 10.dp,
            thumbInnerRadius = 3.5.dp,
        )
        Spacer(Modifier.height(4.dp))
        Layout(
            modifier = Modifier.fillMaxWidth(),
            content = {
                steps.forEach { value ->
                    Text(if (value == 0) "None" else value.toString(), color = colors.textSecondary, fontSize = 10.sp)
                }
            },
        ) { measurables, constraints ->
            val labels = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
            layout(constraints.maxWidth, labels.maxOf { it.height }) {
                labels.forEachIndexed { index, label ->
                    val x = (constraints.maxWidth * fractions[index] - label.width / 2).toInt()
                        .coerceIn(0, constraints.maxWidth - label.width)
                    label.placeRelative(x, 0)
                }
            }
        }
    }
}

/** Settings row with a title, optional subtitle and a trailing switch. */
@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onColor: Color = UvTheme.accent,
) {
    val colors = UvTheme
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.onBackground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(subtitle, color = colors.textSecondary, fontSize = 12.sp)
            }
        }
        UvSwitch(checked = checked, onToggle = onToggle, onColor = onColor)
    }
}
