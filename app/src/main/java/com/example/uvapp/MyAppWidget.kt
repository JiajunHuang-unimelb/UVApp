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
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.GlanceTheme


class MyAppWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Load any required data here
        provideContent {
            // Define your UI using Glance composables
            MyContent()
        }
    }

    @Composable
    private fun MyContent() {

        Row(
            modifier = GlanceModifier.fillMaxSize().background(Color.Black),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {


            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "UV INDEX NOW",
                    modifier = GlanceModifier.padding(12.dp),
                    style = TextStyle(color = ColorProvider(Color.White))
                )
                Text(text = "--",
                    modifier = GlanceModifier.padding(12.dp),
                    style = TextStyle(color = ColorProvider(Color.White))
                )

            }
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally
            ){
                Row(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(
                        text = "More",
                        onClick = actionStartActivity<MainActivity>()
                    )
                }
            }
        }

    }
}