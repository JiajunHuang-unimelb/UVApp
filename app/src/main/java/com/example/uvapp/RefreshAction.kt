package com.example.uvapp

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback

import com.example.uvapp.MyAppWidget

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Perform your data fetch or state update here
        MyAppWidget.fetchData(context, glanceId)
    }
}
