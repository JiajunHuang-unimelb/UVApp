package com.example.uvapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uvapp.domain.model.ForecastDay
import com.example.uvapp.domain.model.UvBand
import com.example.uvapp.ui.theme.BandPalettes
import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.viewmodel.SEEK_END_MINUTES
import com.example.uvapp.viewmodel.SEEK_START_MINUTES
import com.example.uvapp.viewmodel.SEEK_STEP_MINUTES
import com.example.uvapp.viewmodel.SEEK_STEPS
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

fun minutesToHhMm(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return "%02d:%02d".format(h, m)
}

/** Seven day chips tinted by each day's max UV band; selected chip outlined. */
@Composable
fun DayChipsRow(
    days: List<ForecastDay>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UvTheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp)) {
        days.forEachIndexed { index, day ->
            val palette = if (colors.isDark) BandPalettes.dark(day.band) else BandPalettes.light(day.band)
            val selected = index == selectedIndex
            Column(
                Modifier
                    .weight(1f)
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.tint)
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, colors.accent, RoundedCornerShape(12.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(index) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            ) {
                Text(
                    day.weekday,
                    color = palette.text,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                )
                Box(Modifier.size(6.dp).clip(CircleShape).background(palette.dot))
                Text(
                    day.dayOfMonth.toString(),
                    color = palette.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/** "Time" caption + selected hour value. */
@Composable
fun TimeRow(selectedTimeMinutes: Int, modifier: Modifier = Modifier) {
    val colors = UvTheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Time", color = colors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(
            minutesToHhMm(selectedTimeMinutes),
            color = colors.onBackground,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Interactive 24 h UV forecast curve card (band zones, gridlines, area, line, cursor). */
@Composable
fun UvChartCard(
    day: ForecastDay,
    selectedTimeMinutes: Int,
    onTimeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UvTheme
    val textMeasurer = rememberTextMeasurer()

    /** Maps a pointer x (px) inside the chart to a snapped 30-min time. */
    fun timeFromX(xPx: Float, widthPx: Int): Int {
        val fraction = (xPx / widthPx).coerceIn(0f, 1f)
        val index = snapFraction(fraction, SEEK_STEPS)
        return SEEK_START_MINUTES + index * SEEK_STEP_MINUTES
    }

    SunCard(
        modifier.fillMaxWidth().height(210.dp),
        containerColor = colors.surface,
        borderColor = colors.outline,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                "HOURLY UV FORECAST",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp),
            )
            Spacer(Modifier.height(2.dp))
            val data = remember(day) { day.hourly }
            val selectedHour = selectedTimeMinutes / 60f
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset -> onTimeChange(timeFromX(offset.x, size.width)) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            onTimeChange(timeFromX(change.position.x, size.width))
                        }
                    },
            ) {
                // The canvas spans the full card width (380 dp). The chart
                // area covers card-local x 0..380, y 28..176 (UV 12 at the
                // top, 0 at the bottom), x-axis labels at y 186. The extra
                // height below y=176 keeps the axis text clear of the edge.
                val sx = size.width / 380f
                val sy = size.height / 176f
                fun px(mockX: Float) = mockX * sx
                fun py(mockY: Float) = (mockY - 28f) * sy
                val chartLeft = 0f
                val chartRight = size.width
                val chartWidth = chartRight - chartLeft
                val chartBottom = py(176f)

                // Band zones (UV 0..12), same vertical extents as the mockup,
                // hues sourced from the same band palettes as the hero/day chips.
                val bands = listOf(
                    BandPalettes.light(UvBand.EXTREME).dot to floatArrayOf(28f, 40.3f), // Extreme
                    BandPalettes.light(UvBand.VERY_HIGH).dot to floatArrayOf(40.3f, 77.3f), // Very High
                    BandPalettes.light(UvBand.HIGH).dot to floatArrayOf(77.3f, 102f), // High
                    BandPalettes.light(UvBand.MODERATE).dot to floatArrayOf(102f, 139f), // Moderate
                    BandPalettes.light(UvBand.LOW).dot to floatArrayOf(139f, 176f), // Low
                )
                bands.forEach { (color, yRange) ->
                    drawRect(
                        color = color.copy(alpha = 0.10f),
                        topLeft = Offset(chartLeft, py(yRange[0])),
                        size = androidx.compose.ui.geometry.Size(chartWidth, py(yRange[1]) - py(yRange[0])),
                    )
                }

                // Dashed threshold gridlines at 11/8/6/3.
                val dash = PathEffect.dashPathEffect(floatArrayOf(3f * sx, 4f * sx))
                listOf(40.3f, 77.3f, 102f, 139f).forEach { yMock ->
                    val yPx = py(yMock)
                    drawLine(colors.outline, Offset(chartLeft, yPx), Offset(chartRight, yPx), strokeWidth = 1f * sx, pathEffect = dash)
                }

                // Right-edge band labels.
                val labelStyle = TextStyle(fontSize = 9.sp, color = colors.textSecondary)
                listOf("11" to 33.5f, "8" to 70.5f, "6" to 95.5f, "3" to 132.5f).forEach { (text, yMock) ->
                    val layout = textMeasurer.measure(AnnotatedString(text), style = labelStyle)
                    drawText(layout, topLeft = Offset(px(360f) - layout.size.width, py(yMock) - layout.size.height / 2f))
                }

                // Hour -> x: the chart time axis matches the slider window
                // (SEEK_START..SEEK_END), so the marker always sits under the thumb.
                val windowStart = SEEK_START_MINUTES / 60f
                val windowEnd = SEEK_END_MINUTES / 60f
                val windowHours = windowEnd - windowStart
                fun hourX(hour: Float) = chartLeft + (hour - windowStart) / windowHours * chartWidth
                fun uvY(uv: Float) = chartBottom - uv * 12.333f * sy

                // Only points inside the slider window are drawn (the rest are clipped).
                val points = data.mapNotNull { hp ->
                    val hour = hp.hour.toFloat()
                    val uv = hp.uv ?: return@mapNotNull null
                    if (hour < windowStart || hour > windowEnd) null else hourX(hour) to uvY(uv.toFloat())
                }

                // Area under the curve.
                if (points.isNotEmpty()) {
                    val area = Path().apply {
                        moveTo(points.first().first, chartBottom)
                        points.forEach { (x, y) -> lineTo(x, y) }
                        lineTo(points.last().first, chartBottom)
                        close()
                    }
                    drawPath(area, color = colors.accent.copy(alpha = 0.14f))
                }

                // Curve line.
                if (points.size > 1) {
                    val line = Path().apply {
                        moveTo(points.first().first, points.first().second)
                        points.drop(1).forEach { (x, y) -> lineTo(x, y) }
                    }
                    drawPath(line, color = colors.accent, style = Stroke(width = 3f * sx, join = StrokeJoin.Round, cap = StrokeCap.Round))
                }

                // A single marker for the selected time.
                val peakUv = day.uvAt(selectedHour.toDouble())
                if (peakUv != null) {
                    val peakHour = selectedHour.toDouble()
                    val pkx = hourX(peakHour.toFloat())
                    val pky = uvY(peakUv.toFloat())
                    drawLine(
                        colors.accent.copy(alpha = 0.5f),
                        Offset(pkx, 0f),
                        Offset(pkx, pky),
                        strokeWidth = 1.5f * sx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f * sx, 3f * sx)),
                    )
                    drawCircle(Color.White, radius = 7f * sx, center = Offset(pkx, pky))
                    drawCircle(colors.accent, radius = 5f * sx, center = Offset(pkx, pky))
                    // Selected-time pill.
                    val badgeStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.accent)
                    val badgeLayout = textMeasurer.measure(AnnotatedString("${minutesToHhMm(selectedTimeMinutes)} - ${formatUv(peakUv)}"), style = badgeStyle)
                    val padH = 10f * sx
                    val pillW = badgeLayout.size.width + padH * 2
                    val pillH = 22f * sx
                    val pillLeft = (pkx - pillW / 2f).coerceIn(0f, size.width - pillW)
                    val pillTop = 2f * sx
                    val pillBg = if (colors.isDark) colors.surface else colors.background
                    val corner = CornerRadius(pillH / 2f, pillH / 2f)
                    drawRoundRect(pillBg, topLeft = Offset(pillLeft, pillTop), size = Size(pillW, pillH), cornerRadius = corner)
                    drawRoundRect(
                        colors.accent,
                        topLeft = Offset(pillLeft, pillTop),
                        size = Size(pillW, pillH),
                        cornerRadius = corner,
                        style = Stroke(width = 1f * sx),
                    )
                    drawText(
                        badgeLayout,
                        topLeft = Offset(pillLeft + (pillW - badgeLayout.size.width) / 2f, pillTop + (pillH - badgeLayout.size.height) / 2f),
                    )
                }

                // X-axis time labels across the slider window.
                val axisStyle = TextStyle(fontSize = 10.sp, color = colors.textSecondary)
                listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { fraction ->
                    val minutes = SEEK_START_MINUTES + kotlin.math.round((SEEK_END_MINUTES - SEEK_START_MINUTES) * fraction).toInt()
                    val layout = textMeasurer.measure(AnnotatedString(minutesToHhMm(minutes)), style = axisStyle)
                    val lx = chartLeft + chartWidth * fraction
                    val labelX = when (fraction) {
                        0f -> chartLeft
                        1f -> chartRight - layout.size.width
                        else -> lx - layout.size.width / 2f
                    }
                    drawText(layout, topLeft = Offset(labelX, py(186f) - layout.size.height / 2f))
                }
            }
        }
    }
}
