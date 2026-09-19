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

class MyAppWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Load any required data here
        provideContent {
            // Define your UI using Glance composables
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally
            ) {
                Text(text = "Hello, Glance!")
            }
        }
    }
}