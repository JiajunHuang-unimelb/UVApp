package com.example.uvapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.uvapp.ui.UVAppRoot

/** Hosts the Compose app; production location, UV forecast, and place-name data are wired in [UVAppRoot]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UVAppRoot()
        }
    }
}
