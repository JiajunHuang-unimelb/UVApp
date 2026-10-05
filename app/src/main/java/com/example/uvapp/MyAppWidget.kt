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
import android.content.Intent
import android.net.Uri
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

import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.state.updateAppWidgetState
import com.example.uvapp.domain.model.UvBand
import com.example.uvapp.ui.theme.BandPalettes

import com.example.uvapp.ui.theme.UvTheme
import com.example.uvapp.ui.components.DataSourceLinks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.take


class MyAppWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact;
    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition;
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Load any required data here

        fetchData(context,id)

        provideContent {
            // Define your UI using Glance composables


            MyContent()
        }
    }

    @Composable
    private fun MyContent() {
        val context = androidx.glance.LocalContext.current
        val data = currentState<Preferences>()
        val uv = data[doublePreferencesKey("uv")] ?: -1.0
        //instead of val band = data[stringPreferencesKey("band")] ?: "NBand"
        //now just generate band from uv instead of storing it
        val band = UvBand.fromIndex(uv)

        val size = LocalSize.current;


        var largeFont = (40).sp;
        var midFont = (18).sp;
        var smallFont = (16).sp;

        if (size.width<160.dp){
            largeFont = (30).sp;
            midFont = (16).sp;
            smallFont = (12).sp;
        } else if (size.width<140.dp){
            largeFont = (25).sp;
            midFont = (14).sp;
            smallFont = (10).sp;
        }


        //val currentSize = "Size $size";



        var uvColor = Color.Gray;
        uvColor = BandPalettes.dark(band).text


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
                /*testing (comment out when done)
                Text(
                    text = currentSize,
                    modifier = GlanceModifier.padding(0.dp),
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = smallFont,
                        color = ColorProvider(UvTheme.textSecondary),
                    )
                )

                 */

                Text(
                    text = "UV INDEX NOW",
                    modifier = GlanceModifier.padding(2.dp),
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
                Text(text = band.label,
                    modifier = GlanceModifier.padding(5.dp),
                    style = TextStyle(color = ColorProvider(uvColor),
                        fontSize = midFont,
                        fontWeight = FontWeight.Bold)
                )

                Text(
                    text = context.getString(R.string.uv_data_attribution),
                    modifier = GlanceModifier.padding(vertical = 4.dp).clickable(
                        androidx.glance.appwidget.action.actionStartActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(DataSourceLinks.OPEN_METEO)),
                        ),
                    ),
                    style = TextStyle(color = ColorProvider(Color.LightGray), fontSize = 10.sp),
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





    companion object {

        //so the code is reused between initializing the widget and refreshing it
        suspend fun fetchData(context: Context, glanceId: GlanceId){
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
                            band = UvBand.fromIndex(uv).label;
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


            println("third $uv $band")

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


}
