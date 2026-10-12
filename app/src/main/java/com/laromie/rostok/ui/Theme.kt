package com.laromie.rostok.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E7D4F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8F2D4),
    onPrimaryContainer = Color(0xFF00210F),
    secondary = Color(0xFF4F6354),
    secondaryContainer = Color(0xFFD2E8D5),
    tertiary = Color(0xFFB4572B),
    tertiaryContainer = Color(0xFFFFDBCB),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    background = Color(0xFFF6FBF4),
    surface = Color(0xFFF6FBF4),
    surfaceVariant = Color(0xFFDCE5DB),
    surfaceContainer = Color(0xFFEAF0E8),
    surfaceContainerHigh = Color(0xFFE4EAE3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD8A2),
    onPrimary = Color(0xFF00391D),
    primaryContainer = Color(0xFF0F5231),
    onPrimaryContainer = Color(0xFFC8F2D4),
    secondary = Color(0xFFB6CCB9),
    secondaryContainer = Color(0xFF384B3D),
    tertiary = Color(0xFFFFB693),
    tertiaryContainer = Color(0xFF7A3413),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    background = Color(0xFF0F1511),
    surface = Color(0xFF0F1511),
    surfaceVariant = Color(0xFF404941),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252B27),
)

@Composable
fun RostokTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
