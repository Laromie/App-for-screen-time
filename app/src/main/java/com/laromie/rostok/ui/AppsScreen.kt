package com.laromie.rostok.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.laromie.rostok.data.InstalledApp
import com.laromie.rostok.data.TimeUtils
import kotlin.math.roundToInt

@Composable
fun AppsScreen(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<InstalledApp?>(null) }

    if (!state.loaded) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val todayTime = state.today?.apps?.associate { it.packageName to it.timeMs } ?: emptyMap()
    val (tracked, others) = state.apps
        .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
        .partition { it.packageName in state.tracked }
    val sortedOthers = others.sortedByDescending { todayTime[it.packageName] ?: 0L }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Text("Отвлекающие приложения", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Перед их открытием будет пауза с дыханием. Лимит — сколько минут в день можно провести в " +
                    "приложении, потом оно блокируется до завтра.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Поиск") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            )
        }
        if (tracked.isNotEmpty()) {
            item { SectionTitle("Под контролем (${tracked.size})") }
            items(tracked, key = { "t_" + it.packageName }) { app ->
                AppRow(
                    app = app,
                    tracked = true,
                    limit = state.limits[app.packageName] ?: 0,
                    todayMs = todayTime[app.packageName] ?: 0L,
                    onToggle = { vm.setTracked(app.packageName, it) },
                    onEditLimit = { editing = app },
                )
            }
        }
        item { SectionTitle("Остальные") }
        items(sortedOthers, key = { "o_" + it.packageName }) { app ->
            AppRow(
                app = app,
                tracked = false,
                limit = 0,
                todayMs = todayTime[app.packageName] ?: 0L,
                onToggle = { vm.setTracked(app.packageName, it) },
                onEditLimit = {},
            )
        }
    }

    editing?.let { app ->
        LimitDialog(
            app = app,
            initial = state.limits[app.packageName] ?: 0,
            onDismiss = { editing = null },
            onSave = {
                vm.setLimit(app.packageName, it)
                editing = null
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun AppRow(
    app: InstalledApp,
    tracked: Boolean,
    limit: Int,
    todayMs: Long,
    onToggle: (Boolean) -> Unit,
    onEditLimit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = tracked, onClick = onEditLimit)
            .padding(vertical = 6.dp),
    ) {
        AppIcon(app.icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 1)
            Text(
                "Сегодня ${TimeUtils.formatDuration(todayMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (tracked) {
            AssistChip(
                onClick = onEditLimit,
                label = { Text(if (limit > 0) TimeUtils.formatMinutes(limit) else "без лимита") },
            )
            Spacer(Modifier.width(8.dp))
        }
        Switch(checked = tracked, onCheckedChange = onToggle)
    }
}

@Composable
private fun LimitDialog(
    app: InstalledApp,
    initial: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    // 0 — без лимита, дальше шагами по 5 минут до 3 часов.
    var value by remember { mutableFloatStateOf(initial / 5f) }
    val minutes = value.roundToInt() * 5
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Лимит: ${app.label}") },
        text = {
            Column {
                Text(
                    if (minutes == 0) "Без лимита — только пауза перед открытием"
                    else "${TimeUtils.formatMinutes(minutes)} в день",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..36f, steps = 35)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { m ->
                        AssistChip(onClick = { value = m / 5f }, label = { Text("$m мин") })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(minutes) }) { Text("Сохранить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
