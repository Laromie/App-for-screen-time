package com.laromie.rostok.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.laromie.rostok.data.AppCatalog
import com.laromie.rostok.data.AppUsage
import com.laromie.rostok.data.Prep
import com.laromie.rostok.data.TimeUtils

@Composable
fun TodayScreen(
    state: UiState,
    catalog: AppCatalog,
    onOpenUsageAccess: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenPrep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Росток", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        if (state.loaded && !state.hasUsageAccess) {
            item {
                PermissionCard(
                    title = "Нужен доступ к статистике",
                    text = "Чтобы считать экранное время, разреши «Росток» в списке «Доступ к истории использования».",
                    button = "Открыть настройки",
                    onClick = onOpenUsageAccess,
                )
            }
        }
        if (state.loaded && !state.hasAccessibility) {
            item {
                PermissionCard(
                    title = "Включи паузу и лимиты",
                    text = "Чтобы показывать паузу перед соцсетями и блокировать их по лимиту, включи службу «Росток» " +
                        "в Спец. возможностях. Если пункт неактивен: Настройки → Приложения → Росток → ⋮ → " +
                        "«Разрешить ограниченные настройки».",
                    button = "Открыть спец. возможности",
                    onClick = onOpenAccessibility,
                )
            }
        }

        val today = state.today
        item { TodaySummary(state) }
        item { PrepTeaser(state, onOpenPrep) }
        if (state.week.isNotEmpty()) {
            item { WeekChart(state) }
        }
        item {
            val context = LocalContext.current
            FilledTonalButton(
                onClick = { context.startActivity(Intent(context, RecapActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("📊 Итоги недели — карточки для сторис") }
        }
        if (today != null && today.apps.isNotEmpty()) {
            item {
                Text(
                    "Больше всего времени",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            val maxTime = today.apps.first().timeMs
            items(today.apps.take(10), key = { it.packageName }) { app ->
                AppUsageRow(app, catalog, maxTime, state.tracked, state.limits)
            }
        }
    }
}

@Composable
private fun TodaySummary(state: UiState) {
    val totalMs = state.today?.totalMs ?: 0L
    // В кольце — то, что идёт в зачёт цели: всё время или только отвлекающие приложения.
    val usedMs = state.goalUsedMs
    val goalMs = state.goalMinutes * TimeUtils.MINUTE
    val left = goalMs - usedMs
    SectionCard {
        if (state.goalTrackedOnly) {
            Text(
                "Цель — только отвлекающие приложения · всего в телефоне ${TimeUtils.formatDuration(totalMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            GoalRing(fraction = usedMs.toFloat() / goalMs, modifier = Modifier.size(140.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        TimeUtils.formatDuration(usedMs),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "из ${TimeUtils.formatMinutes(state.goalMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat(
                    if (left >= 0) "Осталось до цели" else "Сверх цели",
                    TimeUtils.formatDuration(kotlin.math.abs(left)),
                )
                Stat("Разблокировок", "${state.today?.pickups ?: 0}")
                Stat("Серия", "🔥 ${state.streak} дн.")
                Stat("Очки", "🌱 ${state.points}")
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun WeekChart(state: UiState) {
    val days = state.week
    val goalMs = state.goalMinutes * TimeUtils.MINUTE
    val maxMs = maxOf(days.maxOf { it.totalMs }, goalMs).coerceAtLeast(1)
    val avg = days.dropLast(1).filter { it.totalMs > 0 }.map { it.totalMs }.average().takeIf { !it.isNaN() }

    val good = MaterialTheme.colorScheme.primary
    val bad = MaterialTheme.colorScheme.error
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant

    SectionCard {
        Text("Неделя", style = MaterialTheme.typography.titleMedium)
        if (avg != null) {
            Text(
                "В среднем ${TimeUtils.formatDuration(avg.toLong())} в день",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            val slot = size.width / days.size
            val barW = slot * 0.55f
            days.forEachIndexed { i, d ->
                val h = size.height * d.totalMs / maxMs
                val used = if (state.goalTrackedOnly) d.timeForApps(state.tracked) else d.totalMs
                drawRoundRect(
                    color = if (used <= goalMs) good else bad,
                    topLeft = Offset(i * slot + (slot - barW) / 2, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    alpha = if (i == days.lastIndex) 1f else 0.7f,
                )
            }
            val goalY = size.height - size.height * goalMs / maxMs
            drawLine(
                goalColor,
                Offset(0f, goalY),
                Offset(size.width, goalY),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            days.forEach {
                Text(
                    TimeUtils.shortDayName(it.dayStart),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun AppUsageRow(
    app: AppUsage,
    catalog: AppCatalog,
    maxTime: Long,
    tracked: Set<String>,
    limits: Map<String, Int>,
) {
    val limit = limits[app.packageName] ?: 0
    val isTracked = app.packageName in tracked
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        AppIcon(catalog.icon(app.packageName))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(catalog.label(app.packageName), modifier = Modifier.weight(1f), maxLines = 1)
                Text(TimeUtils.formatDuration(app.timeMs), style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(4.dp))
            val (progress, color) = if (isTracked && limit > 0) {
                val f = app.timeMs.toFloat() / (limit * TimeUtils.MINUTE)
                f to if (f >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
            } else {
                app.timeMs.toFloat() / maxTime to MaterialTheme.colorScheme.primary
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = color,
                drawStopIndicator = {},
            )
            Text(
                buildString {
                    append("Открыто ${app.opens} раз")
                    if (isTracked && limit > 0) append(" · лимит ${TimeUtils.formatMinutes(limit)}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Ближайший экзамен или приглашение включить режим подготовки. */
@Composable
private fun PrepTeaser(state: UiState, onOpen: () -> Unit) {
    val prep = state.prep
    val next = prep?.nearest()
    SectionCard(
        modifier = Modifier.clickable(onClick = onOpen),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        if (next == null) {
            Text("📚 Готовишься к ЕГЭ, ОГЭ или сессии?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Включи режим подготовки: обратный отсчёт, учёт занятий и свои рамки для соцсетей.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            val done = state.prepMinutes[next.id] ?: 0
            Text(
                "${next.name} — ${Prep.whenText(Prep.daysUntil(next.date))}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Подготовка ${TimeUtils.formatMinutes(done)} из ${TimeUtils.formatMinutes(next.targetMinutes)} · " +
                    "сегодня ${TimeUtils.formatMinutes(state.studyTodayMinutes)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
