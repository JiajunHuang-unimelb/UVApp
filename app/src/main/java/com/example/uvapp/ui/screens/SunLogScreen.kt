package com.example.uvapp.ui.screens

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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uvapp.domain.exposure.ExposureCalculator
import com.example.uvapp.domain.model.ExposureDailySummary
import com.example.uvapp.ui.components.SunCard
import com.example.uvapp.ui.components.formatSunTime
import com.example.uvapp.ui.icons.UvIcons
import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.viewmodel.MainUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
private val weekLabelFormat = DateTimeFormatter.ofPattern("d MMM", Locale.US)

/** Sun log tab: one Mon-Sun week of exposure as % of the personal daily limit. */
@Composable
fun SunLogScreen(
    state: MainUiState,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UvTheme
    val week = state.sunLogWeek
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 64.dp, bottom = 88.dp),
    ) {
        Text("Sun log", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.onBackground)
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

        Spacer(Modifier.height(12.dp))
        Text(
            formatSunTime(week.activeDurationMillis) + " in the sun",
            color = colors.accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))
        WeekChartCard(
            days = week.days,
            limitSed = ExposureCalculator.calculatePersonalDoseLimit(state.skinType),
            today = today,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Full bar = your daily limit for skin ${state.skinType.label}",
            color = colors.textSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
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

/** Seven bars, Monday to Sunday; height = share of the daily limit, capped at 100%. */
@Composable
private fun WeekChartCard(days: List<ExposureDailySummary>, limitSed: Double, today: LocalDate) {
    val colors = UvTheme
    SunCard(
        Modifier.fillMaxWidth(),
        containerColor = colors.surface,
        borderColor = colors.outline,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("% OF DAILY LIMIT", color = colors.textSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
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
