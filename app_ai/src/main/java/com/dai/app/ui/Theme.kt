package com.dai.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF0B6E6E), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDEA), onPrimaryContainer = Color(0xFF002020),
    background = Color(0xFFF4F7F8), surface = Color(0xFFF4F7F8),
    surfaceVariant = Color(0xFFE3EAEC), onSurfaceVariant = Color(0xFF3F4949)
)
private val Dark = darkColorScheme(
    primary = Color(0xFF5FD0C4), onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF0F4F4A), onPrimaryContainer = Color(0xFFCDEDEA),
    background = Color(0xFF0D1519), surface = Color(0xFF0D1519),
    surfaceVariant = Color(0xFF1B2A30), onSurfaceVariant = Color(0xFFB5C4C8)
)

@Composable
fun DaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
