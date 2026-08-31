package com.example.uvapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Custom slider reproducing the high-fi thumb (white disc, accent stroke,
 * inner dot) and rounded track. fraction is 0..1; caller snaps to steps.
 */
@Composable
fun SunSlider(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color,
    fillColor: Color,
    thumbStroke: Color,
    trackHeight: Dp = 4.dp,
    thumbOuterRadius: Dp = 10.dp,
    thumbInnerRadius: Dp = 3.5.dp,
    thumbStrokeWidth: Dp = 2.5.dp,
) {
    val controlHeight = trackHeight + thumbOuterRadius * 2
    Canvas(
        modifier
            .fillMaxWidth()
            .height(controlHeight)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onFractionChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    onFractionChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val cy = size.height / 2
        // Track
        drawLine(trackColor, Offset(0f, cy), Offset(size.width, cy), strokeWidth = trackHeight.toPx(), cap = StrokeCap.Round)
        // Fill
        drawLine(fillColor, Offset(0f, cy), Offset(size.width * fraction.coerceIn(0f, 1f), cy), strokeWidth = trackHeight.toPx(), cap = StrokeCap.Round)
        // Thumb
        val tx = size.width * fraction.coerceIn(0f, 1f)
        drawCircle(Color.White, radius = thumbOuterRadius.toPx(), center = Offset(tx, cy))
        drawCircle(
            thumbStroke,
            radius = thumbOuterRadius.toPx(),
            center = Offset(tx, cy),
            style = Stroke(width = thumbStrokeWidth.toPx()),
        )
        drawCircle(thumbStroke, radius = thumbInnerRadius.toPx(), center = Offset(tx, cy))
    }
}

/** Snaps a fraction to the nearest of [stepCount] intervals (0..stepCount). */
fun snapFraction(fraction: Float, stepCount: Int): Int =
    (fraction.coerceIn(0f, 1f) * stepCount).roundToInt().coerceIn(0, stepCount)
