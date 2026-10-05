package com.example.uvapp

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.uvapp.data.history.ExposureHistoryRepositoryFactory
import com.example.uvapp.data.preferences.DataStoreUserPreferencesRepository
import com.example.uvapp.domain.exposure.ExposureCalculator
import com.example.uvapp.domain.model.ExposureDailySummary
import com.example.uvapp.ui.components.formatSunTime
import com.example.uvapp.viewmodel.Tab
import java.time.LocalDate
import kotlinx.coroutines.flow.first

// At or above this height (about 2 rows) the total goes above the bars instead of beside them.
private val TALL_MIN_HEIGHT = 110.dp
// Space taken by padding, day letters and (tall layout) the total line; the bars get the rest.
private val WIDE_RESERVED_HEIGHT = 34.dp
private val TALL_RESERVED_HEIGHT = 72.dp
private val MIN_BAR_HEIGHT = 12.dp

private val backgroundColor = Color(0xFF1C1C1A)
private val textColor = Color(0xFFF1EFE8)
private val subTextColor = Color(0xFFB4B2A9)
private val trackColor = Color(0xFF2C2C2A)
private val barColor = Color(0xFFEF9F27)
private val overLimitColor = Color(0xFFE24B4A)
private val lastWeekColor = Color(0xFF5F5E5A)
private val todayColor = Color(0xFFFAC775)
private val dividerColor = Color(0xFF5F5E5A)

private val BAR_WIDTH = 8.dp
private const val DAY_LETTERS = "MTWTFSS"

/**
 * Home-screen widget: time in the sun this week plus the last 7 days as bars
 * (% of the personal daily limit). Separate from [MyAppWidget]. Refreshed after
 * every history save and every 30 min (see weekly_exposure_widget_info.xml).
 */
class WeeklyExposureWidget : GlanceAppWidget() {

    // Exact, not Responsive: LocalSize is then the real widget size, so the bars can
    // fill whatever row height the launcher gives.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val days =
            ExposureHistoryRepositoryFactory.create(context)
                .observeDaily(today.minusDays(6), today.plusDays(1))
                .first()
        val skinType = DataStoreUserPreferencesRepository(context).preferences.first().skinType
        val limitSed = ExposureCalculator.calculatePersonalDoseLimit(skinType)

        provideContent {
            WeeklyContent(days, limitSed, today)
        }
    }
}

@Composable
private fun WeeklyContent(days: List<ExposureDailySummary>, limitSed: Double, today: LocalDate) {
    val size = LocalSize.current
    // Mon = 1 ... Sun = 7: the last daysThisWeek bars belong to the current week.
    val daysThisWeek = today.dayOfWeek.value
    var weekMillis = 0L
    for (day in days.takeLast(daysThisWeek)) {
        weekMillis += day.activeDurationMillis
    }
    val total = formatSunTime(weekMillis)
    val openSunLog =
        actionStartActivity<MainActivity>(
            actionParametersOf(ActionParameters.Key<String>(MainActivity.EXTRA_TAB) to Tab.SUN_LOG.name),
        )

    Box(
        GlanceModifier
            .fillMaxSize()
            .background(backgroundColor)
            .cornerRadius(16.dp)
            .clickable(openSunLog),
        contentAlignment = Alignment.Center,
    ) {
        if (size.height >= TALL_MIN_HEIGHT) {
            val barHeight = maxOf(size.height - TALL_RESERVED_HEIGHT, MIN_BAR_HEIGHT)
            Column(GlanceModifier.fillMaxSize().padding(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(total, style = textStyle(textColor, 20, true))
                    Spacer(GlanceModifier.width(6.dp))
                    Text("in the sun this week", style = textStyle(subTextColor, 11, false))
                }
                Spacer(GlanceModifier.height(6.dp))
                WeekBars(days, limitSed, today, barHeight)
            }
        } else {
            val barHeight = maxOf(size.height - WIDE_RESERVED_HEIGHT, MIN_BAR_HEIGHT)
            Row(
                GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(total, style = textStyle(textColor, 16, true))
                    Text("this week", style = textStyle(subTextColor, 10, false))
                }
                Spacer(GlanceModifier.width(8.dp))
                Box(GlanceModifier.defaultWeight()) {
                    WeekBars(days, limitSed, today, barHeight)
                }
            }
        }
    }
}

/** Seven bars; a divider marks the start of this week and earlier days are dimmed. */
@Composable
private fun WeekBars(
    days: List<ExposureDailySummary>,
    limitSed: Double,
    today: LocalDate,
    barHeight: Dp,
) {
    // On Sunday the whole window is this week, so the divider index is 0 and none is drawn.
    val dividerIndex = 7 - today.dayOfWeek.value
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        for (index in days.indices) {
            val day = days[index]
            if (index == dividerIndex && dividerIndex > 0) {
                // Same bar + letter stack as a day column, so the line spans the bars only.
                Column {
                    Box(GlanceModifier.width(1.dp).height(barHeight).background(dividerColor)) {}
                    Text(" ", style = textStyle(subTextColor, 10, false))
                }
            }
            val isToday = day.date == today
            var color = barColor
            if (index < dividerIndex) {
                color = lastWeekColor
            } else if (day.doseSed > limitSed) {
                color = overLimitColor
            }
            val fraction = ExposureCalculator.calculateExposureFraction(limitSed, day.doseSed).toFloat()

            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    GlanceModifier.width(BAR_WIDTH).height(barHeight).background(trackColor),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (fraction > 0f) {
                        Box(GlanceModifier.width(BAR_WIDTH).height(barHeight * fraction).background(color)) {}
                    }
                }
                val letter = DAY_LETTERS[day.date.dayOfWeek.value - 1].toString()
                Text(letter, style = textStyle(if (isToday) todayColor else subTextColor, 10, isToday))
            }
        }
    }
}

private fun textStyle(color: Color, sizeSp: Int, bold: Boolean): TextStyle =
    TextStyle(
        color = ColorProvider(color),
        fontSize = sizeSp.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
