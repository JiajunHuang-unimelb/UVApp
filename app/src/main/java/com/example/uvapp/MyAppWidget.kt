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


import com.example.uvapp.ui.theme.UvTheme


class MyAppWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Load any required data here
        provideContent {
            // Define your UI using Glance composables
            val uv = 0.0;
            val band = null ?: "Awaiting";
            val skinType = null ?: "Type Unknown";
            val skinTypeDesc = null ?: "Unknown"
            val spf = null ?: 0

            MyContent(uv, band, skinType, spf, skinTypeDesc)
        }
    }

    @Composable
    private fun MyContent(uv: Double, band : String, skinType: String, spf : Int, skinTypeDesc: String) {

        Row(
            modifier = GlanceModifier.fillMaxSize().background(Color.Black),
            verticalAlignment = Alignment.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {


            Column(
                modifier = GlanceModifier.defaultWeight().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "UV INDEX NOW",
                    modifier = GlanceModifier.padding(12.dp),
                    style = TextStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = ColorProvider(UvTheme.textSecondary),
                    )
                )
                Text(text = uv.toString() ?: "--",
                    modifier = GlanceModifier.padding(0.dp),
                    style = TextStyle(color = ColorProvider(Color.White),
                        fontSize = 62.sp,
                        fontWeight = FontWeight.Bold,
                ))
                Text(text = band,
                    modifier = GlanceModifier.padding(12.dp),
                    style = TextStyle(color = ColorProvider(Color.White),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold)
                )

            }
            Column(
                modifier = GlanceModifier.padding(5.dp).defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ){
                Text(text = "SKIN / SPF",
                    modifier = GlanceModifier.padding(12.dp),
                    style = TextStyle(
                        color = ColorProvider(UvTheme.textSecondary),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(text = skinType,
                    modifier = GlanceModifier.padding(5.dp),
                    style = TextStyle(
                        color = ColorProvider(UvTheme.textSecondary),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(text = skinTypeDesc,
                    modifier = GlanceModifier.padding(5.dp),
                    style = TextStyle(
                        color = ColorProvider(UvTheme.textSecondary),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(text = "SPF ${spf.toString()}",
                    modifier = GlanceModifier.padding(10.dp),
                    style = TextStyle(
                        color = ColorProvider(UvTheme.accent),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                )


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