package com.example.uvapp


import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Alignment
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.glance.GlanceModifier
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.text.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.Button
import androidx.glance.ButtonDefaults
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.GlanceTheme


import android.os.SystemClock
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.example.uvapp.data.repository.UvRepositoryFactory
import com.example.uvapp.domain.model.UvForecastReading
import com.example.uvapp.domain.location.CurrentLocationProvider
import com.example.uvapp.domain.location.LocationResult
import com.example.uvapp.domain.repository.UvRepository as ForecastUvRepository
import com.example.uvapp.domain.model.LocationFix
import com.example.uvapp.platform.location.FusedCurrentLocationProvider
import kotlin.math.abs

import androidx.compose.ui.platform.LocalDensity
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import com.example.uvapp.domain.model.UvBand
import com.example.uvapp.ui.theme.BandPalettes

import com.example.uvapp.ui.theme.UvTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.update


class MyAppWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact;
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition;
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Load any required data here


        provideContent {
            // Define your UI using Glance composables


            MyContent()
        }
    }

    @Composable
    private fun MyContent() {

        val data = currentState<Preferences>()
        val uv = data[doublePreferencesKey("uv")] ?: -2.0
        val band = data[stringPreferencesKey("band")] ?: "NBand"

        val size = LocalSize.current;

        val largeFont = (40).sp;
        val midFont = (18).sp;
        val smallFont = (16).sp;

        val currentSize = "Size $size";

        var uvColor = Color.Gray;

        if (band == "Low"){
            uvColor = Color(0xFF8FE3A0);

        } else if (band == "Moderate"){
            uvColor = Color(0xFFFFD966)
        } else if (band == "High"){
            uvColor = Color(0xFFFFB066)
        } else if (band == "Very High"){
            uvColor = Color(0xFFFF8A80)
        } else if (band == "Extreme"){
            uvColor = Color(0xFFD9A6F2)
        }else{
            uvColor = Color.White;
        }


        Row(
            modifier = GlanceModifier.fillMaxSize().background(Color.Black),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {


            Column(
                modifier = GlanceModifier.defaultWeight()
                    .padding(12.dp).clickable(actionRunCallback<RefreshAction>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                //testing (comment out when done)
                Text(
                    text = currentSize,
                    modifier = GlanceModifier.padding(0.dp),
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = smallFont,
                        color = ColorProvider(UvTheme.textSecondary),
                    )
                )

                Text(
                    text = "UV INDEX NOW",
                    modifier = GlanceModifier.padding(0.dp),
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = smallFont,
                        color = ColorProvider(UvTheme.textSecondary),
                    )
                )
                Text(text = uv.toString() ?: "--",
                    modifier = GlanceModifier.padding(0.dp),
                    style = TextStyle(color = ColorProvider(uvColor),
                        fontSize = largeFont,
                        fontWeight = FontWeight.Bold,
                ))
                Text(text = band,
                    modifier = GlanceModifier.padding(5.dp),
                    style = TextStyle(color = ColorProvider(uvColor),
                        fontSize = midFont,
                        fontWeight = FontWeight.Bold)
                )



                Row () {
                    Button(
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = ColorProvider(Color.DarkGray),
                            contentColor = ColorProvider(Color.White)
                        ),
                        text = "More",
                        onClick = actionStartActivity<MainActivity>()
                    )
                }
            }
        }

    }



}