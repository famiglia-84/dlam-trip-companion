package com.famiglia.tripcompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.famiglia.tripcompanion.ui.TravelApp
import com.famiglia.tripcompanion.ui.TravelViewModel

class MainActivity : ComponentActivity() {
    private val model: TravelViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by model.theme.collectAsStateWithLifecycle()
            val dark = theme == "Dark" || (theme == "System" && isSystemInDarkTheme())
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(0xFFE6E6E6.toInt(), 0xFF1B1B1B.toInt()) { dark },
                )
            }
            TravelApp(model)
        }
    }
}
