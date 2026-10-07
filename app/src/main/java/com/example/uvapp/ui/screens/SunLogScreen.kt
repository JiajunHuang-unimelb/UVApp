package com.example.uvapp.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import com.example.uvapp.viewmodel.SUN_LOG_WEEK_COUNT
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong
import kotlinx.coroutines.launch

private val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private val weekLabelFormat = DateTimeFormatter.ofPattern("d MMM", Locale.US)

/**
 * Sun log tab: same top layout as Home/Forecast (hero row without the address bar), then a
 * pager over the last [SUN_LOG_WEEK_COUNT] Mon-Sun weeks. Each page (week label + chart card)
 * follows the finger; the arrows and the %/Time pill stay fixed.
 */
@Composable
fun SunLogScreen(
    state: MainUiState,
    onPageSettled: (Int) -> Unit,
    onShowTime: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weeks = state.sunLogWeeks
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 80.dp),
    ) {
        // Same geometry as HeroRow: 132 dp tall, 224 dp left card, skin card fills the rest.
        Row(Modifier.fillMaxWidth().height(132.dp), verticalAlignment = Alignment.Top) {
            SunTimeCard(weeks.getOrNull(state.sunLogPage)?.activeDurationMillis, Modifier.width(224.dp).fillMaxHeight())
            Spacer(Modifier.width(8.dp))
            SkinSpfCard(state.skinType, state.spf, Modifier.weight(1f).fillMaxHeight())
        }
        if (weeks.isEmpty()) return@Column

        val today = LocalDate.now()
        val limitSed = ExposureCalculator.calculatePersonalDoseLimit(state.skinType)
        val pagerState = rememberPagerState(initialPage = state.sunLogPage, pageCount = { weeks.size })
        val scope = rememberCoroutineScope()

        // Settled page -> ViewModel, so the week survives tab switches.
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }.collect { page -> onPageSettled(page) }
        }
        // ViewModel -> pager, for a widget tap that resets to this week while the page is open.
        LaunchedEffect(state.sunLogPage) {
            if (pagerState.settledPage != state.sunLogPage && !pagerState.isScrollInProgress) {
                pagerState.animateScrollToPage(state.sunLogPage)
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth()) {
            HorizontalPager(state = pagerState, verticalAlignment = Alignment.Top) { page ->
                val week = weeks[page]
                Column(Modifier.fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                        Text(
                            weekLabel(week.weekStart, today),
                            color = UvTheme.onBackground,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    WeekChartCard(days = week.days, limitSed = limitSed, today = today, showTime = state.sunLogShowsTime)
                }
            }
            // Drawn over the pager's label row so they stay put while the weeks slide.
            WeekArrow(
                rotation = 90f,
                description = "Previous week",
                enabled = pagerState.currentPage > 0,
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                modifier = Modifier.align(Alignment.TopStart),
            )
            WeekArrow(
                rotation = -90f,
                description = "Next week",
                enabled = pagerState.currentPage < weeks.size - 1,
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }

        Spacer(Modifier.height(12.dp))
        ViewToggle(showTime = state.sunLogShowsTime, onShowTime = onShowTime)
    }
}

private fun weekLabel(weekStart: LocalDate, today: LocalDate): String {
    if (weekStart.plusDays(7).isAfter(today)) return "This week"
    return weekStart.format(weekLabelFormat) + " - " + weekStart.plusDays(6).format(weekLabelFormat)
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
            // Crossfades when the settled week changes, so the new total is noticed.
            Crossfade(
                targetState = if (activeDurationMillis == null) "--" else formatSunTime(activeDurationMillis),
                label = "weeklyTotal",
            ) { total ->
                Text(
                    total,
                    color = colors.accent,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            Text("in the sun", color = colors.accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun WeekArrow(
    rotation: Float,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UvTheme
    Box(
        modifier
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
 * chosen with the segmented pill below the pager.
 */
@Composable
private fun WeekChartCard(
    days: List<ExposureDailySummary>,
    limitSed: Double,
    today: LocalDate,
    showTime: Boolean,
) {
    val colors = UvTheme
    SunCard(
        Modifier.fillMaxWidth(),
        containerColor = colors.surface,
        borderColor = colors.outline,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
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
