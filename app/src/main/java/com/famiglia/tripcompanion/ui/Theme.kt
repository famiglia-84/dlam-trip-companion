package com.famiglia.tripcompanion.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF145C56), onPrimary = Color.White,
    primaryContainer = Color(0xFFD0EEE6), onPrimaryContainer = Color(0xFF063A35),
    secondary = Color(0xFF8E5F30), secondaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFFFAF9F5), surface = Color(0xFFFAF9F5),
    surfaceContainer = Color(0xFFF1F0EA),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF99D5C9), onPrimary = Color(0xFF033730),
    primaryContainer = Color(0xFF21574E), onPrimaryContainer = Color(0xFFBCEFE1),
    secondary = Color(0xFFEABF8F),
    background = Color(0xFF101B19), surface = Color(0xFF101B19),
    surfaceContainer = Color(0xFF1D2C28),
)

@Composable
fun TripTheme(mode: String = "System", content: @Composable () -> Unit) {
    val dark = when (mode) { "Dark" -> true; "Light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
