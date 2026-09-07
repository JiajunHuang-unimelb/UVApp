package com.example.uvapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.uvapp.domain.advisor.BurnCalculator
import com.example.uvapp.domain.model.LightContext
import com.example.uvapp.domain.model.SkinType
import com.example.uvapp.domain.model.UvBand
import com.example.uvapp.ui.icons.UvIcons
import com.example.uvapp.ui.theme.BandPalettes
import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.viewmodel.MainViewModel
import com.example.uvapp.viewmodel.Tab
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

import androidx.lifecycle.viewmodel.compose.viewModel

fun formatUv(uv: Double): String = String.format(Locale.US, "%.1f", uv)

/** "38 200" — space-grouped thousands; shared by cards and the dev panel. */
fun formatThousands(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(" ").reversed()

/** Card with rounded (M3 Medium) corners and an optional border. No shadow. */
@Composable
fun SunCard(
    modifier: Modifier = Modifier,
    containerColor: Color,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    // The box sizes to its content (matchParentSize-only children would
    // collapse it to zero). The clip is OUTERMOST so the background and
    // border are also rounded (not just the content).
    Box(
        modifier
            .clip(shape)
            .then(if (borderColor != null) Modifier.border(borderWidth, borderColor, shape) else Modifier)
            .drawBehind {
                drawRect(containerColor, size = size)
            },
        content = content,
    )
}

/** 1 dp horizontal divider line (used by cards, dialogs and the nav bar). */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = UvTheme.outline) {
    Box(modifier.height(1.dp).background(color))
}

/** Hero UV card: band tint, label, big number with white halo, band label. */
@Composable
fun UvHeroCard(uv: Double, band: UvBand, modifier: Modifier = Modifier, uvAvailable: Boolean = true, uvLabel: String = "UV INDEX NOW") {
    val colors = UvTheme
    val palette = if (colors.isDark) BandPalettes.dark(band) else BandPalettes.light(band)
    SunCard(modifier, containerColor = palette.tint, shape = RoundedCornerShape(12.dp)) {
        Column(
            Modifier.fillMaxSize().padding(top = 16.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                uvLabel,
                color = palette.text.copy(alpha = if (colors.isDark) 0.75f else 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                if (!colors.isDark) {
                    Text(
                        if (uvAvailable) formatUv(uv) else "--",
                        color = Color.White,
                        fontSize = 62.sp,
                        fontWeight = FontWeight.ExtraBold,
                        style = TextStyle(drawStyle = Stroke(width = 7f, join = StrokeJoin.Round)),
                    )
                }
                Text(
                    if (uvAvailable) formatUv(uv) else "--",
                    color = palette.text,
                    fontSize = 62.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 64.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (uvAvailable) band.label else "Awaiting UV data",
                color = palette.text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Skin / SPF summary card. */
@Composable
fun SkinSpfCard(skinType: SkinType, spf: Int, modifier: Modifier = Modifier, viewModel: MainViewModel = viewModel()) {
    val colors = UvTheme
    SunCard(modifier.clickable{viewModel.onTabSelected(Tab.SETTINGS)}, containerColor = colors.surface, borderColor = colors.outline, shape = RoundedCornerShape(12.dp)) {
        Column(
            Modifier.fillMaxSize().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "SKIN / SPF",
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(skinType.label, color = colors.onBackground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(skinType.description, color = colors.textSecondary, fontSize = 13.sp)
            }
            Hairline(Modifier.width(76.dp))
            Text(
                "SPF $spf",
                color = colors.accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Hero row: UV card (smaller frame) + skin card (wider, same height = aligned). */
@Composable
fun HeroRow(uv: Double, band: UvBand, skinType: SkinType, spf: Int, modifier: Modifier = Modifier, uvAvailable: Boolean = true, uvLabel: String = "UV INDEX NOW") {
    Row(modifier.fillMaxWidth().height(132.dp), verticalAlignment = Alignment.Top) {
        UvHeroCard(uv, band, Modifier.width(224.dp).fillMaxHeight(), uvAvailable, uvLabel)
        Spacer(Modifier.width(8.dp))
        SkinSpfCard(skinType, spf, Modifier.weight(1f).fillMaxHeight())
    }
}

/** Address pill + locate button. */
@Composable
fun AddressBar(placeName: String, onSearchClick: () -> Unit, onLocate: () -> Unit, modifier: Modifier = Modifier) {
    val colors = UvTheme
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .weight(1f)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(colors.surface)
                .border(1.dp, colors.outline, RoundedCornerShape(24.dp))
                .clickable(onClick = onSearchClick),
        ) {
            Icon(
                UvIcons.Search,
                contentDescription = "Search address",
                tint = colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 19.dp).size(19.dp),
            )
            Text(
                placeName,
                color = colors.onBackground,
                fontSize = 15.sp,
                maxLines = 1,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 40.dp),
            )
            Icon(
                UvIcons.ChevronDown,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 19.dp).size(17.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(colors.surfaceVariant)
                .border(1.dp, colors.outline, CircleShape)
                .clickable(onClick = onLocate),
            contentAlignment = Alignment.Center,
        ) {
            Icon(UvIcons.Locate, contentDescription = "Use current location", tint = colors.accent, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * Context card: all three light states listed as colour-dot chips (current one
 * highlighted) plus a lux bar showing which lux range maps to which state and
 * the current sensor reading.
 */
@Composable
fun ContextCard(context: LightContext, lux: Int, onLuxChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = UvTheme
    SunCard(
        modifier.fillMaxWidth(),
        containerColor = colors.surface,
        borderColor = colors.outline,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text("Light exposure - manual simulation", color = colors.textSecondary, fontSize = 12.sp)
            Text("Drag to adjust brightness (lux) and timer estimate", color = colors.textSecondary, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LightContext.entries.forEach { ctx ->
                    val active = ctx == context
                    val tint = when (ctx) {
                        LightContext.INDOOR -> Color(0xFFEFEAE4)
                        LightContext.SHADE -> Color(0xFFFFF3D6)
                        LightContext.DIRECT_SUN -> Color(0xFFFFE9D6)
                    }
                    Row(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) tint else colors.surface)
                            .border(
                                if (active) 1.5.dp else 1.dp,
                                if (active) colors.accent else colors.outline,
                                RoundedCornerShape(16.dp),
                            )
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(contextDot(ctx)))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            ctx.label,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) colors.accent else colors.onBackground,
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LuxBar(lux, onLuxChange)
        }
    }
}

// Lux-scale semantics shared by the context chips and the bar below them:
// one grey for Indoors, one amber for In shade (Direct sun = theme accent).
private val indoorGrey = Color(0xFFC9C2B8)
private val shadeAmber = Color(0xFFFB8C00)

@Composable
private fun contextDot(ctx: LightContext): Color = when (ctx) {
    LightContext.INDOOR -> indoorGrey
    LightContext.SHADE -> shadeAmber
    LightContext.DIRECT_SUN -> UvTheme.accent
}

/** Lux scale (log, 1..100k): grey = Indoors, amber = In shade, accent = Direct sun. Slidable for testing. */
@Composable
private fun LuxBar(lux: Int, onLuxChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = UvTheme
    val textMeasurer = rememberTextMeasurer()
    val indoorEnd = (log10(LightContext.INDOOR_MAX_LUX.toDouble()) / 5.0).toFloat()
    val shadeEnd = (log10(LightContext.SHADE_MAX_LUX.toDouble()) / 5.0).toFloat()
    val markerFrac = (log10(lux.toDouble()) / 5.0).toFloat().coerceIn(0f, 1f)

    /** Maps a pointer x (px) to a lux value on the log scale (1..100k). */
    fun luxFromX(xPx: Float, widthPx: Int): Int {
        val fraction = (xPx / widthPx).coerceIn(0f, 1f)
        return (10.0.pow(fraction * 5.0)).toInt().coerceIn(1, 100_000)
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(30.dp)
                .pointerInput(Unit) {
                    detectTapGestures { offset -> onLuxChange(luxFromX(offset.x, size.width)) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onLuxChange(luxFromX(change.position.x, size.width))
                    }
                },
        ) {
            val w = size.width
            val barY = 14.dp.toPx()
            val barH = 8.dp.toPx()
            drawRect(indoorGrey, topLeft = Offset(0f, barY), size = Size(w * indoorEnd, barH))
            drawRect(shadeAmber, topLeft = Offset(w * indoorEnd, barY), size = Size(w * (shadeEnd - indoorEnd), barH))
            drawRect(colors.accent, topLeft = Offset(w * shadeEnd, barY), size = Size(w * (1f - shadeEnd), barH))
            drawLine(Color.White, Offset(w * indoorEnd, barY), Offset(w * indoorEnd, barY + barH), strokeWidth = 1.5.dp.toPx())
            drawLine(Color.White, Offset(w * shadeEnd, barY), Offset(w * shadeEnd, barY + barH), strokeWidth = 1.5.dp.toPx())
            // marker: triangle above the bar + stem
            val mx = w * markerFrac
            val tri = Path().apply {
                moveTo(mx - 6.dp.toPx(), barY - 7.dp.toPx())
                lineTo(mx + 6.dp.toPx(), barY - 7.dp.toPx())
                lineTo(mx, barY)
                close()
            }
            drawPath(tri, colors.accent)
            drawLine(colors.accent, Offset(mx, barY), Offset(mx, barY + barH), strokeWidth = 1.5.dp.toPx())
            // value text, clamped inside the bar
            val valStyle = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.accent)
            val vl = textMeasurer.measure(AnnotatedString(formatThousands(lux) + " lux"), style = valStyle)
            val gap = 6.dp.toPx()
            val vx = if (mx + gap + vl.size.width <= w) mx + gap else mx - gap - vl.size.width
            drawText(vl, topLeft = Offset(vx, 0f))
        }
        // state ranges under the bar
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(indoorEnd), contentAlignment = Alignment.CenterStart) {
                Text(LightContext.INDOOR.label, color = colors.textSecondary, fontSize = 9.sp)
            }
            Box(Modifier.weight(shadeEnd - indoorEnd), contentAlignment = Alignment.Center) {
                Text(LightContext.SHADE.label, color = colors.textSecondary, fontSize = 9.sp)
            }
            Box(Modifier.weight(1f - shadeEnd), contentAlignment = Alignment.CenterEnd) {
                Text(LightContext.DIRECT_SUN.label, color = colors.textSecondary, fontSize = 9.sp)
            }
        }
    }
}

/** Safe Timer card with progress ring and countdown. */
@Composable
fun SafeTimerCard(
    remainingSeconds: Long,
    totalBurnSeconds: Long,
    isWarning: Boolean,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = UvTheme
    val finite = totalBurnSeconds in 1 until Long.MAX_VALUE
    val borderColor = if (isWarning) colors.error else colors.outline
    val borderWidth = if (isWarning) 1.4.dp else 1.2.dp
    SunCard(
        modifier.fillMaxWidth().height(268.dp),
        containerColor = colors.surface,
        borderColor = borderColor,
        borderWidth = borderWidth,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(12.dp))
            Text("SAFE TIMER", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                val ringSize = 188.dp
                Canvas(Modifier.size(ringSize)) {
                    val stroke = 12.dp.toPx()
                    val radius = size.minDimension / 2 - stroke / 2
                    val center = Offset(size.width / 2, size.height / 2)
                    drawCircle(color = colors.outline, radius = radius, center = center, style = Stroke(width = stroke))
                    val fraction = if (finite) (remainingSeconds.toFloat() / totalBurnSeconds.toFloat()).coerceIn(0f, 1f) else 0f
                    if (fraction > 0f) {
                        drawArc(
                            color = if (isWarning) colors.error else colors.accent,
                            startAngle = -90f,
                            sweepAngle = 360f * fraction,
                            useCenter = false,
                            topLeft = Offset(center.x - radius, center.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(width = stroke, cap = StrokeCap.Butt),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (finite) BurnCalculator.formatRemaining(remainingSeconds) else "--:--",
                        color = if (isWarning) colors.error else colors.onBackground,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (finite) {
                Box(
                    Modifier
                        .width(148.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (isWarning) colors.warningResetPill else colors.surfaceVariant)
                        .clickable(onClick = onReset),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Reset Timer",
                        color = if (isWarning) colors.error else colors.accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(10.dp))
            } else {
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** Inline error banner (load failed). */
@Composable
fun ErrorBanner(message: String, modifier: Modifier = Modifier) {
    val colors = UvTheme
    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.errorBanner)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(UvIcons.Warning, contentDescription = null, tint = colors.error, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            message,
            color = colors.onBackground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
        )
    }
}

/** "Showing cached data" freshness note. */
@Composable
fun CachedIndicator(modifier: Modifier = Modifier) {
    Text(
        "Showing cached data",
        color = UvTheme.textSecondary,
        fontSize = 11.5.sp,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}
