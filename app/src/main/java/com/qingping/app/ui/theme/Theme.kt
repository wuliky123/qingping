package com.qingping.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val QingpingGreen = Color(0xFF2E7D5B)
private val QingpingGreenDark = Color(0xFF1F5C42)
private val QingpingBackground = Color(0xFFF4F7F5)

private val LightColors = lightColorScheme(
    primary = QingpingGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9EDE2),
    onPrimaryContainer = QingpingGreenDark,
    secondary = Color(0xFF5B7A6A),
    background = QingpingBackground,
    surface = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    error = Color(0xFFB3261E)
)

@Composable
fun QingpingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
