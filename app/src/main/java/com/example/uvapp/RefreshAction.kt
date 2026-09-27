package com.example.uvapp

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.uvapp.data.repository.UvRepositoryFactory
import com.example.uvapp.domain.location.LocationResult
import com.example.uvapp.domain.model.LocationFix
import com.example.uvapp.domain.model.UvForecastReading
import com.example.uvapp.platform.location.FusedCurrentLocationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.take
import kotlin.math.abs

import com.example.uvapp.domain.model.UvBand
import com.example.uvapp.MyAppWidget

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Perform your data fetch or state update here
        MyAppWidget.fetchData(context, glanceId)
    }




}
