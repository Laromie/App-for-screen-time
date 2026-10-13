package com.laromie.rostok.ui

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
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
        item { TodaySummary(state, catalog) }
        item { PrepTeaser(state, onOpenPrep) }
        if (state.week.isNotEmpty()) {
            item { WeekChart(state) }
        }
        item { CalendarCard(state, catalog) }
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

/** Сводка дня: крупное время, полоса по приложениям с отметкой цели и три плитки. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodaySummary(state: UiState, catalog: AppCatalog) {
    val totalMs = state.today?.totalMs ?: 0L
    // В зачёт цели — всё время или только отвлекающие приложения.
    val usedMs = state.goalUsedMs
    val goalMs = state.goalMinutes * TimeUtils.MINUTE
    val left = goalMs - usedMs
    val over = left < 0

    // Сегменты полосы: топ-3 приложения из тех, что идут в зачёт цели, и «остальное».
    val counted = state.today?.apps.orEmpty()
        .filter { !state.goalTrackedOnly || it.packageName in state.tracked }
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface, 0.45f),
    )
    val restColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val segments = counted.take(3).mapIndexed { i, app ->
        BarSegment(catalog.label(app.packageName), app.timeMs, colors[i])
    } + listOfNotNull(
        (usedMs - counted.take(3).sumOf { it.timeMs }).takeIf { it >= TimeUtils.MINUTE }
            ?.let { BarSegment("Остальное", it, restColor) }
    )

    SectionCard {
        Text(
            if (state.goalTrackedOnly) "Отвлекающие приложения сегодня" else "Сегодня в телефоне",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                TimeUtils.formatDuration(usedMs),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            StatusChip(
                text = if (over) "+${TimeUtils.formatDuration(-left)}" else "ещё ${TimeUtils.formatDuration(left)}",
                container = if (over) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
            )
        }
        if (state.goalTrackedOnly) {
            Text(
                "Всего в телефоне ${TimeUtils.formatDuration(totalMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        GoalBar(segments = segments, usedMs = usedMs, goalMs = goalMs)
        Spacer(Modifier.height(4.dp))
        GoalCaption(usedMs = usedMs, goalMs = goalMs, goalMinutes = state.goalMinutes)
        if (segments.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                segments.forEach { seg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(seg.color))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${seg.label} ${TimeUtils.formatDuration(seg.ms)}",
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniTile("🔓", "${state.today?.pickups ?: 0}", "разблокировок", Modifier.weight(1f))
            MiniTile("🔥", "${state.streak}", "дней серия", Modifier.weight(1f))
            MiniTile("🌱", "${state.points}", "очков", Modifier.weight(1f))
        }
    }
}

private data class BarSegment(val label: String, val ms: Long, val color: Color)

/**
 * Горизонтальная полоса: сегменты по приложениям, шкала — до цели (или до факта, если цель превышена).
 * Вертикальная черта — где проходит цель. Полоса плавно «заполняется» при появлении.
 */
@Composable
private fun GoalBar(segments: List<BarSegment>, usedMs: Long, goalMs: Long) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(usedMs) { progress.animateTo(1f, tween(durationMillis = 800, easing = FastOutSlowInEasing)) }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val marker = MaterialTheme.colorScheme.onSurface
    val scale = maxOf(usedMs, goalMs).coerceAtLeast(1L).toFloat()
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(9.dp))
    ) {
        drawRect(track)
        var x = 0f
        val p = progress.value
        segments.forEach { seg ->
            val w = size.width * seg.ms / scale * p
            drawRect(seg.color, topLeft = Offset(x, 0f), size = Size(w, size.height))
            x += w
        }
        // Отметка цели, если факт её перерос.
        if (usedMs > goalMs) {
            val gx = size.width * goalMs / scale
            drawLine(marker, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 2.5.dp.toPx())
        }
    }
}

@Composable
private fun GoalCaption(usedMs: Long, goalMs: Long, goalMinutes: Int) {
    val scale = maxOf(usedMs, goalMs).coerceAtLeast(1L).toFloat()
    val frac = (goalMs / scale).coerceIn(0f, 1f)
    Box(Modifier.fillMaxWidth()) {
        Text(
            "цель ${TimeUtils.formatMinutes(goalMinutes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            // Подпись стоит под отметкой цели (или у правого края, если цель не превышена).
            modifier = Modifier.align(BiasAlignment(horizontalBias = frac * 2 - 1, verticalBias = 0f)),
        )
    }
}

@Composable
private fun StatusChip(text: String, container: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
private fun MiniTile(emoji: String, value: String, label: String, modifier: Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text("$emoji $value", style = MaterialTheme.typography.titleMedium, maxLines = 1)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
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
