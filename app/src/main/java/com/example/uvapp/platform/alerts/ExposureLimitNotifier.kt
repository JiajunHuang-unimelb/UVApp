package com.example.uvapp.platform.alerts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.uvapp.MainActivity
import com.example.uvapp.R

/**
 * Heads-up notification when the burn countdown reaches zero. The in-app vibration can be
 * silenced by the system in the background, so this channel vibrates on its own.
 */
class ExposureLimitNotifier(private val context: Context) {
    fun show() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val channel = NotificationChannel(CHANNEL_ID, "Exposure limit", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts when your safe sun time has run out"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 120, 350)
        }
        manager.createNotificationChannel(channel)
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(context, NOTIFICATION_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            manager.notify(NOTIFICATION_ID, Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_warning).setContentTitle("Exposure limit reached")
                .setContentText("Your safe sun time is up. Move into the shade or cover up.")
                .setCategory(Notification.CATEGORY_ALARM).setContentIntent(tap).setAutoCancel(true).build())
        } catch (_: SecurityException) { /* The Home card still shows the countdown has ended. */ }
    }

    companion object {
        const val CHANNEL_ID = "exposure_limit"
        const val NOTIFICATION_ID = 2005
    }
}
