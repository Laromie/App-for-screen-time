package com.laromie.rostok.ui

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.laromie.rostok.data.TimeUtils
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun AppIcon(drawable: Drawable?, size: Dp = 40.dp) {
    val bitmap = remember(drawable) {
        drawable?.let { runCatching { it.toBitmap(96, 96).asImageBitmap() }.getOrNull() }
    }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size))
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/** Карточка с просьбой выдать разрешение. */
@Composable
fun PermissionCard(title: String, text: String, button: String, onClick: () -> Unit) {
    SectionCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp))
        Button(onClick = onClick) { Text(button) }
    }
}

/**
 * Быстрый выбор длительности: готовые варианты и «Своё…» с ползунком от 5 минут до 4 часов.
 * [lastCustom] — прошлое своё значение, с него ползунок начинает.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DurationButtons(
    presets: List<Int>,
    lastCustom: Int,
    onPick: (minutes: Int, custom: Boolean) -> Unit,
) {
    var dialog by remember { mutableStateOf(false) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { m ->
            FilledTonalButton(
                onClick = { onPick(m, false) },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) { Text("$m мин", maxLines = 1, softWrap = false) }
        }
        OutlinedButton(
            onClick = { dialog = true },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) { Text("⏱ Своё…", maxLines = 1, softWrap = false) }
    }
    if (dialog) {
        DurationDialog(
            initial = lastCustom,
            onConfirm = {
                dialog = false
                onPick(it, true)
            },
            onDismiss = { dialog = false },
        )
    }
}

@Composable
private fun DurationDialog(initial: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    // Шаг 5 минут: от 5 минут до 4 часов.
    var steps by remember { mutableFloatStateOf((initial / 5).coerceIn(1, 48).toFloat()) }
    val minutes = steps.roundToInt() * 5
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сколько времени?") },
        text = {
            Column {
                Text(TimeUtils.formatMinutes(minutes), style = MaterialTheme.typography.headlineMedium)
                Slider(value = steps, onValueChange = { steps = it }, valueRange = 1f..48f, steps = 46)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("5 мин", style = MaterialTheme.typography.labelSmall)
                    Text("4 ч", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(minutes) }) { Text("Начать") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
