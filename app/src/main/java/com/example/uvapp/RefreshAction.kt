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

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Perform your data fetch or state update here
        var uv = -1.0;
        var band = "Not Started";
        var skinType = "Not Started";
        var skinTypeDesc = "Not Started"
        var spf = -1

        val locationProvider = FusedCurrentLocationProvider(context)
        val forecastRepository = UvRepositoryFactory.create(context)
        var locationFix: LocationFix? = null
        val nowMillis: () -> Long = System::currentTimeMillis

        val result =
            try {
                locationProvider.getCurrentLocation()
            } catch (error: CancellationException) {
                throw error
            }

        println("result: $result")

        when (result) {
            is LocationResult.Success -> {
                locationFix = result.fix
                println("helloworld")
                println("first $uv")
                forecastRepository
                    .observeForecast(locationFix.latitude, locationFix.longitude).take(1).collect{
                        forecast -> val currentReading = forecast.readings.nearestTo(nowMillis())
                        println("second " + currentReading?.uvIndex)
                        val oldUv= uv
                        uv = currentReading?.uvIndex ?: oldUv
                    }
                println("goodbye")
            }
            LocationResult.PermissionDenied -> finishLocationFailure(
                "Location permission is required. Tap the locate button to grant it.",
            )

            LocationResult.LocationDisabled -> finishLocationFailure(
                "Location is turned off. Enable it in system settings and try again.",
            )

            LocationResult.Timeout -> finishLocationFailure(
                "Location request timed out. Move near a window or try again.",
            )

            LocationResult.Unavailable -> finishLocationFailure(
                "Current location is unavailable. Try again or choose a place manually.",
            )

            else -> println("else")

        }


        println("third $uv")

        updateAppWidgetState(context, glanceId){
                prefs -> prefs[doublePreferencesKey("uv")] = uv
        }

        updateAppWidgetState(context, glanceId){
                prefs -> prefs[stringPreferencesKey("band")] = band
        }

        updateAppWidgetState(context, glanceId){
                prefs -> prefs[stringPreferencesKey("skinType")] = skinType
        }

        updateAppWidgetState(context, glanceId){
                prefs -> prefs[stringPreferencesKey("skinTypeDesc")] = skinTypeDesc
        }

        updateAppWidgetState(context, glanceId){
                prefs -> prefs[intPreferencesKey("spf")] = spf
        }

        // Refresh/update the specific widget instance
        println("hello")
        MyAppWidget().update(context, glanceId)
    }

    private fun finishLocationFailure(message: String) {
        println(message);
    }
    private fun List<UvForecastReading>.nearestTo(timestampMillis: Long): UvForecastReading? =
        minByOrNull { reading -> abs(reading.forecastTimeMillis - timestampMillis) }
}
