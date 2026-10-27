package com.sms.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The website's teal and navy.
private val Teal = Color(0xFF167D80)
private val Navy = Color(0xFF17324D)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7EEEE),
    onPrimaryContainer = Color(0xFF0B3B3C),
    secondary = Navy,
    onSecondary = Color.White,
    background = Color(0xFFF4F7F8),
    surface = Color(0xFFF4F7F8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FCFD1),
    onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF11696D),
    onPrimaryContainer = Color(0xFFD7EEEE),
    secondary = Color(0xFFA9C7E3),
    onSecondary = Color(0xFF0E2236),
)

@Composable
fun SmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
