package com.example.uvapp.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uvapp.ui.components.DayChipsRow
import com.example.uvapp.ui.components.TimeRow
import com.example.uvapp.ui.components.TopChrome
import com.example.uvapp.ui.components.UvChartCard
import com.example.uvapp.ui.components.minutesToHhMm
import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.viewmodel.ForecastUiState

/**
 * Forecast tab: hero copy, address, day chips, and the combined
 * chart + time picker (drag anywhere on the curve to change the hour).
 */
@Composable
fun ForecastScreen(
    state: ForecastUiState,
    onSearchClick: () -> Unit,
    onLocate: () -> Unit,
    onSelectDay: (Int) -> Unit,
    onSelectTime: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UvTheme
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 80.dp),
    ) {
        TopChrome(
            uv = state.selectedUv,
            skinType = state.skinType,
            spf = state.spf,
            placeName = state.placeName,
            onSearchClick = onSearchClick,
            onLocate = onLocate,
        )

        val day = state.selectedDay
        if (day != null) {
            Spacer(Modifier.height(16.dp))
            DayChipsRow(state.days, state.selectedDayIndex, onSelectDay)

            Spacer(Modifier.height(18.dp))
            TimeRow(state.selectedTimeMinutes)

            Spacer(Modifier.height(10.dp))
            UvChartCard(
                day = day,
                selectedTimeMinutes = state.selectedTimeMinutes,
                onTimeChange = onSelectTime,
            )

            Spacer(Modifier.height(10.dp))
            Text(
                "Drag anywhere on the chart to change time",
                color = colors.textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("Sunrise ${minutesToHhMm(day.sunriseMinutes)}", color = colors.textSecondary, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text("Sunset ${minutesToHhMm(day.sunsetMinutes)}", color = colors.textSecondary, fontSize = 10.sp)
            }
        }
    }
}
