package com.example.uvapp.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uvapp.domain.exposure.ExposureCalculator
import com.example.uvapp.domain.model.ExposureDailySummary
import com.example.uvapp.ui.components.SkinSpfCard
import com.example.uvapp.ui.components.SunCard
import com.example.uvapp.ui.components.formatSunTime
import com.example.uvapp.ui.icons.UvIcons
import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.viewmodel.MainUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

private val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private val weekLabelFormat = DateTimeFormatter.ofPattern("d MMM", Locale.US)

/**
 * Sun log tab: same top layout as Home/Forecast (hero row without the address bar),
 * then one Mon-Sun week of exposure as % of the personal daily limit.
 */
@Composable
fun SunLogScreen(
    state: MainUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onShowTime: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UvTheme
    val week = state.sunLogWeek
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 80.dp),
    ) {
        // Same geometry as HeroRow: 132 dp tall, 224 dp left card, skin card fills the rest.
        Row(Modifier.fillMaxWidth().height(132.dp), verticalAlignment = Alignment.Top) {
            SunTimeCard(week?.activeDurationMillis, Modifier.width(224.dp).fillMaxHeight())
            Spacer(Modifier.width(8.dp))
            SkinSpfCard(state.skinType, state.spf, Modifier.weight(1f).fillMaxHeight())
        }
        if (week == null) return@Column

        val today = LocalDate.now()
        val isCurrentWeek = week.weekStart.plusDays(7).isAfter(today)
        val weekLabel =
            if (isCurrentWeek) {
                "This week"
            } else {
                week.weekStart.format(weekLabelFormat) + " - " + week.weekStart.plusDays(6).format(weekLabelFormat)
            }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WeekArrow(rotation = 90f, description = "Previous week", enabled = true, onClick = onPreviousWeek)
            Text(
                weekLabel,
                color = colors.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            WeekArrow(rotation = -90f, description = "Next week", enabled = !isCurrentWeek, onClick = onNextWeek)
        }

        // Swipe right on the chart = earlier week, swipe left = later week (the ViewModel ignores
        // "next" on the current week). The arrows above stay as the tap alternative.
        val swipeThresholdPx = 80f * LocalDensity.current.density
        Spacer(Modifier.height(12.dp))
        WeekChartCard(
            days = week.days,
            limitSed = ExposureCalculator.calculatePersonalDoseLimit(state.skinType),
            today = today,
            showTime = state.sunLogShowsTime,
            onShowTime = onShowTime,
            modifier =
                Modifier.pointerInput(Unit) {
                    var dragTotal = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dragTotal = 0f },
                        onDragEnd = {
                            if (dragTotal > swipeThresholdPx) {
                                onPreviousWeek()
                            } else if (dragTotal < -swipeThresholdPx) {
                                onNextWeek()
                            }
                        },
                        onHorizontalDrag = { _, dragAmount -> dragTotal += dragAmount },
                    )
                },
        )
    }
}

/** Takes the UV hero card's place: the week's total time in the sun, in accent tint. */
@Composable
private fun SunTimeCard(activeDurationMillis: Long?, modifier: Modifier) {
    val colors = UvTheme
    SunCard(modifier, containerColor = colors.activePill, shape = RoundedCornerShape(12.dp)) {
        Column(
            Modifier.fillMaxSize().padding(top = 16.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("WEEKLY TOTAL", color = colors.accent.copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                if (activeDurationMillis == null) "--" else formatSunTime(activeDurationMillis),
                color = colors.accent,
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text("in the sun", color = colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WeekArrow(rotation: Float, description: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = UvTheme
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .border(1.dp, colors.outline, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            UvIcons.ChevronDown,
            contentDescription = description,
            tint = if (enabled) colors.accent else colors.outline,
            modifier = Modifier.size(20.dp).rotate(rotation),
        )
    }
}

/**
 * Seven bars, Monday to Sunday; height = share of the daily limit, capped at 100%.
 * The number row above shows either that share (uncapped) or the time in the sun,
 * chosen with the segmented pill at the top of the card.
 */
@Composable
private fun WeekChartCard(
    days: List<ExposureDailySummary>,
    limitSed: Double,
    today: LocalDate,
    showTime: Boolean,
    onShowTime: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UvTheme
    SunCard(
        modifier.fillMaxWidth(),
        containerColor = colors.surface,
        borderColor = colors.outline,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            ViewToggle(showTime = showTime, onShowTime = onShowTime)

            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                for (day in days) {
                    var label = ""
                    var labelColor = colors.onBackground
                    // Days still ahead in this week stay blank; past days with nothing show a real zero.
                    if (!day.date.isAfter(today)) {
                        if (showTime) {
                            label = formatSunTime(day.activeDurationMillis)
                        } else {
                            label = (day.doseSed / limitSed * 100).roundToLong().toString() + "%"
                            if (day.doseSed > limitSed) labelColor = colors.error
                        }
                    }
                    Text(
                        label,
                        color = labelColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            Canvas(Modifier.fillMaxWidth().height(140.dp)) {
                val slot = size.width / 7f
                val barWidth = slot * 0.5f
                val corner = CornerRadius(4.dp.toPx())
                for (index in days.indices) {
                    val day = days[index]
                    val left = slot * index + (slot - barWidth) / 2f
                    drawRoundRect(
                        colors.outline.copy(alpha = 0.35f),
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = corner,
                    )
                    val fraction = ExposureCalculator.calculateExposureFraction(limitSed, day.doseSed).toFloat()
                    if (fraction > 0f) {
                        val barHeight = size.height * fraction
                        drawRoundRect(
                            if (day.doseSed > limitSed) colors.error else colors.accent,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = corner,
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                for (index in days.indices) {
                    val isToday = days[index].date == today
                    Text(
                        dayLetters[index],
                        color = if (isToday) colors.accent else colors.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            // The pill only changes the numbers, so the bars' meaning is stated in both views.
            Spacer(Modifier.height(8.dp))
            Text(
                "Bar height = share of your daily limit",
                color = colors.textSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Segmented pill choosing what the number row shows: % of the daily limit or time in the sun. */
@Composable
private fun ViewToggle(showTime: Boolean, onShowTime: (Boolean) -> Unit) {
    val colors = UvTheme
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(shape)
            .border(1.dp, colors.accent, shape),
    ) {
        ViewToggleSegment(
            text = "% of daily limit",
            selected = !showTime,
            onClick = { onShowTime(false) },
            modifier = Modifier.weight(1f),
        )
        ViewToggleSegment(
            text = "Time in sun",
            selected = showTime,
            onClick = { onShowTime(true) },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * One half of [ViewToggle]. The selected half is filled with the accent; its text uses the
 * surface colour so it stays readable on both the light and the brighter dark-theme accent.
 */
@Composable
private fun ViewToggleSegment(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = UvTheme
    Box(
        modifier
            .fillMaxHeight()
            .background(if (selected) colors.accent else Color.Transparent)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) colors.surface else colors.accent,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
