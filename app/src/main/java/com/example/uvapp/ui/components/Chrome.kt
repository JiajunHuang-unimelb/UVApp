package com.example.uvapp.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.model.UvBand
import com.example.uvapp.ui.icons.UvIcons
import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.viewmodel.Tab

/**
 * Shared top chrome for Home and Forecast: hero (UV number + SKIN/SPF card),
 * then the address bar + locate button — identical positions and sizes on
 * both pages because both screens call this exact block with the same paddings.
 */
@Composable
fun TopChrome(
    uv: Double,
    skinType: SkinType,
    spf: Int,
    placeName: String,
    onSearchClick: () -> Unit,
    onLocate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        HeroRow(uv, UvBand.fromIndex(uv), skinType, spf)
        Spacer(Modifier.height(12.dp))
        AddressBar(
            placeName = placeName,
            onSearchClick = onSearchClick,
            onLocate = onLocate,
        )
    }
}

/** Shared chrome: indeterminate top loading bar (visible during any load). */
@Composable
fun TopLoadingBar(visible: Boolean, modifier: Modifier = Modifier) {
    val colors = UvTheme
    val transition = rememberInfiniteTransition(label = "loading")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "loadingAlpha",
    )
    if (visible) {
        Box(modifier.fillMaxWidth().height(3.dp).background(colors.accent.copy(alpha = alpha)))
    }
}

/** Floating refresh button, top right (40 dp circle per high-fi). */
@Composable
fun RefreshButton(isLoading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = UvTheme
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.surface)
            .border(1.dp, colors.outline, CircleShape)
            .clickable(enabled = !isLoading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            UvIcons.Refresh,
            contentDescription = "Refresh",
            tint = if (isLoading) colors.textSecondary else colors.onBackground,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * Bottom navigation with the three tabs, active pill and home indicator,
 * matching the high-fi geometry (item centers at 22.3% / 50% / 77.7% of width).
 */
@Composable
fun BottomNav(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val colors = UvTheme
    BoxWithConstraints(modifier.fillMaxWidth().height(64.dp).background(colors.surfaceVariant)) {
        Hairline(Modifier.align(Alignment.TopCenter).fillMaxWidth())
        val w = maxWidth
        NavItem(centerX = w * 0.2233f, icon = UvIcons.Home, label = "Home", selected = selected == Tab.HOME) { onSelect(Tab.HOME) }
        NavItem(centerX = w * 0.5f, icon = UvIcons.Forecast, label = "Forecast", selected = selected == Tab.FORECAST) { onSelect(Tab.FORECAST) }
        NavItem(centerX = w * 0.7767f, icon = UvIcons.Settings, label = "Settings", selected = selected == Tab.SETTINGS) { onSelect(Tab.SETTINGS) }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp)
                .width(60.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.outline),
        )
    }
}

@Composable
private fun BoxWithConstraintsScope.NavItem(
    centerX: Dp,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = UvTheme
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .offset(x = centerX - 32.dp)
            .width(64.dp)
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(top = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(64.dp, 30.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(if (selected) colors.activePill else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) colors.accent else colors.textSecondary,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (selected) colors.accent else colors.textSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
