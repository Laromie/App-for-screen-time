package com.laromie.rostok.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/** Светлая схема из одного акцентного цвета: контейнеры и фоны — его смеси с белым. */
private fun lightScheme(accent: Color): ColorScheme {
    val white = Color.White
    return lightColorScheme(
        primary = accent,
        onPrimary = white,
        primaryContainer = lerp(white, accent, 0.22f),
        onPrimaryContainer = lerp(accent, Color.Black, 0.6f),
        secondary = lerp(accent, Color(0xFF555555), 0.5f),
        secondaryContainer = lerp(white, accent, 0.13f),
        onSecondaryContainer = lerp(accent, Color.Black, 0.65f),
        tertiary = Color(0xFFB4572B),
        tertiaryContainer = Color(0xFFFFDBCB),
        onTertiaryContainer = Color(0xFF380D00),
        error = Color(0xFFBA1A1A),
        errorContainer = Color(0xFFFFDAD6),
        background = lerp(white, accent, 0.03f),
        surface = lerp(white, accent, 0.03f),
        surfaceVariant = lerp(Color(0xFFE2E2E2), accent, 0.08f),
        surfaceContainer = lerp(Color(0xFFF0F0F0), accent, 0.06f),
        surfaceContainerHigh = lerp(Color(0xFFEAEAEA), accent, 0.07f),
    )
}

private fun darkScheme(accent: Color, amoled: Boolean): ColorScheme {
    val base = if (amoled) Color.Black else Color(0xFF111413)
    val light = lerp(accent, Color.White, 0.45f)
    return darkColorScheme(
        primary = light,
        onPrimary = lerp(accent, Color.Black, 0.7f),
        primaryContainer = lerp(Color.Black, accent, 0.45f),
        onPrimaryContainer = lerp(accent, Color.White, 0.8f),
        secondary = lerp(light, Color(0xFFBBBBBB), 0.5f),
        secondaryContainer = lerp(Color(0xFF1E1E1E), accent, 0.25f),
        onSecondaryContainer = lerp(accent, Color.White, 0.85f),
        tertiary = Color(0xFFFFB693),
        tertiaryContainer = Color(0xFF7A3413),
        onTertiaryContainer = Color(0xFFFFDBCB),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF93000A),
        background = base,
        surface = base,
        surfaceVariant = lerp(Color(0xFF3A3A3A), accent, 0.12f),
        surfaceContainer = if (amoled) Color(0xFF101010) else lerp(Color(0xFF1B1D1C), accent, 0.06f),
        surfaceContainerHigh = if (amoled) Color(0xFF1A1A1A) else lerp(Color(0xFF252726), accent, 0.07f),
    )
}

private fun shapesFor(style: CornerStyle): Shapes = when (style) {
    CornerStyle.SHARP -> Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(3.dp),
        medium = RoundedCornerShape(4.dp),
        large = RoundedCornerShape(6.dp),
        extraLarge = RoundedCornerShape(8.dp),
    )
    CornerStyle.NORMAL -> Shapes()
    CornerStyle.ROUND -> Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(16.dp),
        medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )
}

@Composable
fun RostokTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    Appearance.ensureLoaded(context)
    val s = Appearance.settings

    val dark = when (s.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = when {
        s.wallpaperColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) {
            dynamicDarkColorScheme(context).let {
                if (s.amoled) it.copy(background = Color.Black, surface = Color.Black) else it
            }
        } else {
            dynamicLightColorScheme(context)
        }
        dark -> darkScheme(s.accent.color, s.amoled)
        else -> lightScheme(s.accent.color)
    }

    // Иконки строки состояния должны быть видны на фоне выбранной темы, а не системной.
    LaunchedEffect(dark) {
        val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        (context as? ComponentActivity)?.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    // Размер текста масштабируем через fontScale — так меняется весь текст, включая эмодзи.
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * s.textScale.factor),
    ) {
        MaterialTheme(
            colorScheme = colors,
            shapes = shapesFor(s.corners),
            content = content,
        )
    }
}
