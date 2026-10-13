package com.laromie.rostok.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.laromie.rostok.data.TimeUtils
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: UiState,
    vm: MainViewModel,
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { FocusCard(state, vm) }
        item { AppearanceCard(state, vm) }
        item { GoalCard(state, vm) }
        item { PauseCard(state, vm) }
        item { NotificationsCard(state, vm) }
        item {
            SectionCard {
                Text("Разрешения", style = MaterialTheme.typography.titleMedium)
                PermissionRow("Доступ к статистике", state.hasUsageAccess, onOpenUsageAccess)
                PermissionRow("Служба паузы и лимитов", state.hasAccessibility, onOpenAccessibility)
                Text(
                    "Все данные хранятся только на телефоне. Приложение ничего не отправляет в интернет.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FocusCard(state: UiState, vm: MainViewModel) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val active = state.focusUntil > now
    LaunchedEffect(state.focusUntil) {
        while (state.focusUntil > System.currentTimeMillis()) {
            delay(1000)
            now = System.currentTimeMillis()
        }
        now = System.currentTimeMillis()
        if (state.focusUntil != 0L) vm.onFocusFinished()
    }

    SectionCard(
        containerColor = if (active) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text("🎯 Режим фокуса", style = MaterialTheme.typography.titleMedium)
        if (active) {
            val left = state.focusUntil - now
            Text(
                "Все отвлекающие приложения заблокированы ещё ${"%d:%02d".format(left / 60_000, left / 1000 % 60)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = vm::cancelFocus) { Text("Прервать (без награды)") }
        } else {
            Text(
                "Заблокировать все отвлекающие приложения на время учёбы, работы или отдыха. " +
                    "За завершённый фокус — очки.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 50, 90).forEach { m ->
                    FilledTonalButton(onClick = { vm.startFocus(m) }) { Text("$m мин") }
                }
            }
        }
    }
}

@Composable
private fun GoalCard(state: UiState, vm: MainViewModel) {
    // Шаг 15 минут, от 30 минут до 6 часов.
    var value by remember(state.goalMinutes) { mutableFloatStateOf(state.goalMinutes / 15f) }
    val minutes = value.roundToInt() * 15
    SectionCard {
        Text("Дневная цель", style = MaterialTheme.typography.titleMedium)
        Text(
            "Не больше ${TimeUtils.formatMinutes(minutes)} экрана в день",
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { vm.setGoal(minutes) },
            valueRange = 2f..24f,
            steps = 21,
        )
        Text(
            "Совет: начни с чуть меньшего, чем твоё среднее за неделю, и снижай постепенно.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        ToggleRow(
            title = "Только отвлекающие приложения",
            hint = "Карты, звонки, музыка и учёба не тратят цель — считаются только приложения из вкладки «Приложения»",
            checked = state.goalTrackedOnly,
            onChange = vm::setGoalTrackedOnly,
        )
    }
}

@Composable
private fun NotificationsCard(state: UiState, vm: MainViewModel) {
    SectionCard {
        Text("🔔 Напоминания", style = MaterialTheme.typography.titleMedium)
        ToggleRow(
            title = "Предупреждать о лимите",
            hint = "За 5 и за 1 минуту до конца лимита приложения",
            checked = state.limitWarnings,
            onChange = vm::setLimitWarnings,
        )
        ToggleRow(
            title = "Вечерний итог дня",
            hint = "Сколько провёл в телефоне, сравнение со вчера и серия. По воскресеньям — итоги недели",
            checked = state.eveningSummary,
            onChange = vm::setEveningSummary,
        )
        if (state.eveningSummary) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Время итога", modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.setEveningHour((state.eveningHour + 23) % 24) }) { Text("−") }
                Text("%d:00".format(state.eveningHour), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { vm.setEveningHour((state.eveningHour + 1) % 24) }) { Text("+") }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PauseCard(state: UiState, vm: MainViewModel) {
    var value by remember(state.pauseSeconds) { mutableFloatStateOf(state.pauseSeconds.toFloat()) }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Пауза перед открытием", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Дыхательная пауза перед отвлекающими приложениями",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.pauseEnabled, onCheckedChange = vm::setPauseEnabled)
        }
        if (state.pauseEnabled) {
            Text("${value.roundToInt()} сек", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = value,
                onValueChange = { value = it },
                onValueChangeFinished = { vm.setPauseSeconds(value.roundToInt()) },
                valueRange = 3f..30f,
                steps = 26,
            )
        }
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text((if (granted) "✅ " else "⚠️ ") + title, modifier = Modifier.weight(1f))
        if (!granted) Button(onClick = onClick) { Text("Включить") }
    }
}
