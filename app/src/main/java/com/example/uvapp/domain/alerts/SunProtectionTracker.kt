package com.example.uvapp.domain.alerts

import com.example.uvapp.domain.model.UvBand

/** Why a sun protection notification is shown. */
enum class SunProtectionAlertKind {
    /** UV entered a band that needs sunscreen (session start or a band rise). */
    BAND,

    /** Two hours of exposure since the user last applied sunscreen. */
    REAPPLY,

    /** The reapply reminder came early because of sustained walking (US-18). */
    ACTIVITY,
}

data class SunProtectionAlert(
    val kind: SunProtectionAlertKind,
    val band: UvBand,
    val uvIndex: Double,
    val recommendedSpf: Int,
    val userSpf: Int,
) {
    val userSpfTooLow: Boolean get() = userSpf < recommendedSpf
}

/** Recommended sunscreen for a band, or null when the band needs none (Low). */
fun UvBand.recommendedSpf(): Int? =
    when (this) {
        UvBand.LOW -> null
        UvBand.MODERATE -> 30
        UvBand.HIGH, UvBand.VERY_HIGH, UvBand.EXTREME -> 50
    }

/**
 * Decides when to show UV-band sunscreen advice (US-17) and when to remind the user to reapply,
 * bringing the reminder forward after sustained walking (US-18).
 *
 * Time only counts while the session is running: a paused session freezes the reapply timer and
 * holds any alert until it resumes. Times are on the exposure clock, so dev 60x speeds them up too.
 */
class SunProtectionTracker(
    private val reapplyIntervalMillis: Long = REAPPLY_INTERVAL_MILLIS,
    private val activityThresholdMillis: Long = ACTIVITY_THRESHOLD_MILLIS,
    private val activityAdvanceMillis: Long = ACTIVITY_ADVANCE_MILLIS,
) {
    private var highestBandAlerted: UvBand? = null
    private var lastUpdateMillis: Long? = null
    private var sinceAppliedMillis: Long? = null
    private var walkingMillis = 0L
    private var reminderSent = false

    /** Exposure time left until the reapply reminder, or null before the first "I've applied". */
    val reapplyRemainingMillis: Long?
        get() = sinceAppliedMillis?.let { (dueMillis() - it).coerceAtLeast(0L) }

    /** Starts (or restarts) the reapply timer. */
    fun markApplied() {
        sinceAppliedMillis = 0L
        walkingMillis = 0L
        reminderSent = false
    }

    /** Clears all state; called when the session ends or reminders are turned off. */
    fun reset() {
        highestBandAlerted = null
        lastUpdateMillis = null
        sinceAppliedMillis = null
        walkingMillis = 0L
        reminderSent = false
    }

    /** Advances the timers and returns the alert to show now, if any. */
    fun update(
        nowMillis: Long,
        sessionActive: Boolean,
        running: Boolean,
        uvIndex: Double,
        walking: Boolean,
        userSpf: Int,
    ): SunProtectionAlert? {
        if (!sessionActive) {
            reset()
            return null
        }
        val elapsed = lastUpdateMillis?.let { (nowMillis - it).coerceAtLeast(0L) } ?: 0L
        lastUpdateMillis = nowMillis
        if (!running) return null

        sinceAppliedMillis = sinceAppliedMillis?.plus(elapsed)
        if (walking) walkingMillis += elapsed

        val band = UvBand.fromIndex(uvIndex)
        val recommended = band.recommendedSpf()
        val alerted = highestBandAlerted
        if (recommended != null && (alerted == null || band > alerted)) {
            highestBandAlerted = band
            return SunProtectionAlert(SunProtectionAlertKind.BAND, band, uvIndex, recommended, userSpf)
        }

        val since = sinceAppliedMillis ?: return null
        if (reminderSent || since < dueMillis()) return null
        reminderSent = true
        val kind = if (since < reapplyIntervalMillis) SunProtectionAlertKind.ACTIVITY else SunProtectionAlertKind.REAPPLY
        return SunProtectionAlert(kind, band, uvIndex, recommended ?: 0, userSpf)
    }

    private fun dueMillis(): Long =
        if (walkingMillis >= activityThresholdMillis) reapplyIntervalMillis - activityAdvanceMillis else reapplyIntervalMillis

    companion object {
        const val REAPPLY_INTERVAL_MILLIS = 2 * 60 * 60_000L
        const val ACTIVITY_THRESHOLD_MILLIS = 20 * 60_000L
        const val ACTIVITY_ADVANCE_MILLIS = 30 * 60_000L
    }
}

/** Notification title for this alert. */
fun SunProtectionAlert.title(): String =
    when (kind) {
        SunProtectionAlertKind.BAND -> "UV ${band.label} (${formatUvIndex(uvIndex)}) - apply SPF $recommendedSpf+"
        SunProtectionAlertKind.REAPPLY -> "Time to reapply sunscreen"
        SunProtectionAlertKind.ACTIVITY -> "Time to reassess your sunscreen"
    }

/** Notification body for this alert. */
fun SunProtectionAlert.body(): String =
    when (kind) {
        SunProtectionAlertKind.BAND -> {
            val advice =
                if (band == UvBand.MODERATE) "Sun protection is recommended when UV is 3 or above." else "Add a hat and seek shade."
            when {
                !userSpfTooLow -> advice
                userSpf == 0 -> "$advice No SPF is set in Settings."
                else -> "$advice Your SPF $userSpf is below this."
            }
        }
        SunProtectionAlertKind.REAPPLY -> "2 hours since you applied. UV is ${band.label} (${formatUvIndex(uvIndex)})."
        SunProtectionAlertKind.ACTIVITY -> "You've been active for a while. Consider reapplying."
    }

private fun formatUvIndex(uv: Double): String = String.format(java.util.Locale.US, "%.1f", uv)
