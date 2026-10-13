package com.laromie.rostok.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.laromie.rostok.data.RewardStyle

/** Настройки внешнего вида: тема, акцентный цвет, углы, размер текста и персонаж наград. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceCard(state: UiState, vm: MainViewModel) {
    val context = LocalContext.current
    val s = Appearance.settings
    fun update(change: (AppearanceSettings) -> AppearanceSettings) = Appearance.update(context, change)

    SectionCard {
        Text("🎨 Оформление", style = MaterialTheme.typography.titleMedium)

        Label("Тема")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = s.themeMode == mode,
                    onClick = { update { it.copy(themeMode = mode) } },
                    label = { Text(mode.title) },
                )
            }
        }
        SwitchRow(
            title = "Чёрный фон",
            hint = "Для тёмной темы. На AMOLED-экранах экономит батарею",
            checked = s.amoled,
            onChange = { v -> update { it.copy(amoled = v) } },
        )

        Label("Цвет")
        val wallpaperAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val accentActive = !(s.wallpaperColors && wallpaperAvailable)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.alpha(if (accentActive) 1f else 0.4f),
        ) {
            Accent.entries.forEach { accent ->
                ColorDot(
                    color = accent.color,
                    selected = accentActive && s.accent == accent,
                    onClick = { update { it.copy(accent = accent, wallpaperColors = false) } },
                )
            }
        }
        if (wallpaperAvailable) {
            SwitchRow(
                title = "Цвета из обоев",
                hint = "Приложение подстроится под цвета твоих обоев",
                checked = s.wallpaperColors,
                onChange = { v -> update { it.copy(wallpaperColors = v) } },
            )
        }

        Label("Углы")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CornerStyle.entries.forEach { c ->
                FilterChip(
                    selected = s.corners == c,
                    onClick = { update { it.copy(corners = c) } },
                    label = { Text(c.title) },
                )
            }
        }

        Label("Размер текста")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextScale.entries.forEach { t ->
                FilterChip(
                    selected = s.textScale == t,
                    onClick = { update { it.copy(textScale = t) } },
                    label = { Text(t.title) },
                )
            }
        }

        Label("Кого растить за очки")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RewardStyle.entries.forEach { r ->
                FilterChip(
                    selected = state.rewardStyle == r,
                    onClick = { vm.setRewardStyle(r) },
                    label = { Text("${r.emoji} ${r.title}") },
                )
            }
        }
        Text(
            "Сейчас: ${state.stage.emoji} ${state.stage.name}. Прогресс сохраняется при смене.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Label(text: String) {
    Spacer(Modifier.height(12.dp))
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SwitchRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White)
    }
}
