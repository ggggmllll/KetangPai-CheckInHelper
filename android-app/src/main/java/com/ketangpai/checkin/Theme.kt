package com.ketangpai.checkin

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Palette = lightColorScheme(
    primary = Color(0xFF146B63),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F2EC),
    onPrimaryContainer = Color(0xFF073D37),
    secondary = Color(0xFFE99B45),
    background = Color(0xFFF5F7F6),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EFED),
    error = Color(0xFFB3261E),
)

@Composable
fun CheckInTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Palette, content = content)
}
