package com.example.uvapp.platform.alerts

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.uvapp.MainActivity
import com.example.uvapp.R
import com.example.uvapp.domain.model.IndoorSuggestion

class IndoorSuggestionNotifier(private val context: Context) {
    fun show(pending: IndoorSuggestion?) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (pending == null) { manager.cancel(2002); return }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        manager.createNotificationChannel(NotificationChannel("indoor_suggestions", "Indoor location suggestions", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val tap = PendingIntent.getActivity(context, 2002, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        try {
            manager.notify(2002, Notification.Builder(context, "indoor_suggestions")
                .setSmallIcon(R.drawable.ic_locate).setContentTitle("Were you indoors when you paused?")
                .setContentText("Tap to confirm and save this location.").setContentIntent(tap).setOnlyAlertOnce(true).setAutoCancel(true).build())
        } catch (_: SecurityException) { /* Pending suggestion remains available in the app. */ }
    }
}
