package com.laromie.rostok.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.edit

enum class ThemeMode(val title: String) {
    SYSTEM("Как в системе"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
}

enum class Accent(val title: String, val color: Color) {
    GREEN("Зелёный", Color(0xFF2E7D4F)),
    MINT("Мята", Color(0xFF00897B)),
    BLUE("Синий", Color(0xFF1E63D6)),
    PURPLE("Фиолетовый", Color(0xFF6A3FC8)),
    PINK("Розовый", Color(0xFFC2185B)),
    RED("Красный", Color(0xFFD32F2F)),
    ORANGE("Оранжевый", Color(0xFFE8650A)),
    AMBER("Янтарь", Color(0xFFB58500)),
    GRAPHITE("Графит", Color(0xFF455A64)),
}

enum class CornerStyle(val title: String) {
    SHARP("Острые"),
    NORMAL("Обычные"),
    ROUND("Круглые"),
}

enum class TextScale(val title: String, val factor: Float) {
    SMALL("Мельче", 0.9f),
    NORMAL("Обычный", 1f),
    LARGE("Крупнее", 1.15f),
}

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.GREEN,
    /** Цвета из обоев (Android 12+). Если включено, акцент не используется. */
    val wallpaperColors: Boolean = false,
    /** Чисто чёрный фон в тёмной теме — красиво и экономит батарею на AMOLED-экранах. */
    val amoled: Boolean = false,
    val corners: CornerStyle = CornerStyle.NORMAL,
    val textScale: TextScale = TextScale.NORMAL,
)

/** Настройки оформления: общее состояние для всех экранов, сохраняется в SharedPreferences. */
object Appearance {

    private var loaded = false
    var settings by mutableStateOf(AppearanceSettings())
        private set

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        val sp = prefs(context)
        val d = AppearanceSettings()
        settings = AppearanceSettings(
            themeMode = enumOr(sp.getString("theme", null), d.themeMode),
            accent = enumOr(sp.getString("accent", null), d.accent),
            wallpaperColors = sp.getBoolean("wallpaper", d.wallpaperColors),
            amoled = sp.getBoolean("amoled", d.amoled),
            corners = enumOr(sp.getString("corners", null), d.corners),
            textScale = enumOr(sp.getString("text_scale", null), d.textScale),
        )
    }

    fun update(context: Context, change: (AppearanceSettings) -> AppearanceSettings) {
        ensureLoaded(context)
        val new = change(settings)
        settings = new
        prefs(context).edit {
            putString("theme", new.themeMode.name)
            putString("accent", new.accent.name)
            putBoolean("wallpaper", new.wallpaperColors)
            putBoolean("amoled", new.amoled)
            putString("corners", new.corners.name)
            putString("text_scale", new.textScale.name)
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { n -> enumValues<T>().firstOrNull { it.name == n } } ?: default
}
