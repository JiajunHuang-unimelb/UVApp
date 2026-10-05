package com.example.uvapp.platform.alerts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import com.example.uvapp.MainActivity
import com.example.uvapp.R
import com.example.uvapp.domain.alerts.SunProtectionAlert
import com.example.uvapp.domain.alerts.body
import com.example.uvapp.domain.alerts.title
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Heads-up notification for UV-band sunscreen advice and reapply reminders (US-17 / US-18). */
class SunProtectionNotifier(private val context: Context) {
    fun show(alert: SunProtectionAlert) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        // Sound and vibration come from ExposureAlertGateway, so the channel itself stays silent.
        val channel = NotificationChannel(CHANNEL_ID, "Sun protection", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val applied = PendingIntent.getBroadcast(
            context,
            NOTIFICATION_ID,
            Intent(context, SunscreenAppliedReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val action = Notification.Action.Builder(Icon.createWithResource(context, R.drawable.ic_warning), "I've applied", applied).build()
        try {
            manager.notify(NOTIFICATION_ID, Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_warning).setContentTitle(alert.title()).setContentText(alert.body())
                .setStyle(Notification.BigTextStyle().bigText(alert.body()))
                .setCategory(Notification.CATEGORY_REMINDER).setContentIntent(tap).addAction(action).setAutoCancel(true).build())
        } catch (_: SecurityException) { /* The Home card still shows the reapply state. */ }
    }

    fun clear() {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "sun_protection"
        const val NOTIFICATION_ID = 2004
    }
}

/** Process-wide "I've applied" taps from the notification; MainViewModel collects them. */
object SunscreenAppliedEvents {
    private val mutable = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val events: SharedFlow<Unit> = mutable.asSharedFlow()

    fun emit() {
        mutable.tryEmit(Unit)
    }
}

/** Handles the notification's "I've applied" action without opening the app. */
class SunscreenAppliedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SunscreenAppliedEvents.emit()
        SunProtectionNotifier(context).clear()
    }
}
