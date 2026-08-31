package com.example.uvapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.uvapp.ui.UVAppRoot

/**
 * UVApp front-end (MVVM + Jetpack Compose).
 * This build ships the full UI with mock data only — the backend team plugs
 * the real APIs into `data.UvRepository` behind the same interface.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UVAppRoot()
        }
    }
}
