package com.example.uvapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.uvapp.ui.UVAppRoot
import com.example.uvapp.viewmodel.Tab

/**
 * The Compose root owns the location permission request and loads current data.
 * singleTop (manifest) makes a widget tap reuse this instance via onNewIntent,
 * so the running exposure session is never duplicated.
 */
class MainActivity : ComponentActivity() {
    private var requestedTab by mutableStateOf<Tab?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a fresh launch honours the extra; a recreated activity keeps its current tab.
        if (savedInstanceState == null) requestedTab = readRequestedTab(intent)
        setContent {
            UVAppRoot(
                requestedTab = requestedTab,
                onRequestedTabHandled = { requestedTab = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        requestedTab = readRequestedTab(intent)
    }

    private fun readRequestedTab(intent: Intent): Tab? {
        val name = intent.getStringExtra(EXTRA_TAB)
        if (name == null) return null
        return Tab.entries.firstOrNull { it.name == name }
    }

    companion object {
        /** Intent extra naming the [Tab] to open, e.g. from the weekly widget. */
        const val EXTRA_TAB = "com.example.uvapp.EXTRA_TAB"
    }
}
