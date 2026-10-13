package com.laromie.rostok.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laromie.rostok.data.AppCatalog
import com.laromie.rostok.data.ItemKind
import com.laromie.rostok.data.Prep
import com.laromie.rostok.data.TimeUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ru = Locale.forLanguageTag("ru")
private val monthFormat = SimpleDateFormat("LLLL yyyy", ru)
private val dayFormat = SimpleDateFormat("EEEE, d MMMM", ru)

/**
 * Календарь-«тепловая карта»: каждый день окрашен по тому, уложился ли он в цель.
 * Зелёный — в цели (чем меньше времени, тем насыщеннее), красный — сверх цели.
 * Точка снизу — был учебный день, 🎓 — экзамен.
 */
@Composable
fun CalendarCard(state: UiState, catalog: AppCatalog) {
    val today = TimeUtils.startOfToday()
    var monthStart by rememberSaveable { mutableLongStateOf(firstOfMonth(today)) }
    var selected by remember { mutableStateOf<Long?>(null) }
    // Последний выбранный день — чтобы при закрытии деталей они не исчезали до конца анимации.
    var shownDay by remember { mutableStateOf<Long?>(null) }
    if (selected != null) shownDay = selected

    val examDays = state.prep?.items.orEmpty().filter { it.kind != ItemKind.DEADLINE }
        .groupBy { it.date }
    val deadlineDays = state.prep?.items.orEmpty().filter { it.kind == ItemKind.DEADLINE }
        .groupBy { it.date }

    SectionCard(modifier = Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Календарь", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { monthStart = shiftMonth(monthStart, -1); selected = null }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Предыдущий месяц")
            }
            Text(
                monthFormat.format(Date(monthStart)).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelLarge,
            )
            IconButton(
                onClick = { monthStart = shiftMonth(monthStart, 1); selected = null },
                enabled = monthStart < firstOfMonth(today),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Следующий месяц")
            }
        }

        // Месяц «перелистывается» в сторону нажатой стрелки.
        AnimatedContent(
            targetState = monthStart,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it * dir / 3 } + fadeOut())
            },
            label = "month",
        ) { month ->
            MonthGrid(
                monthStart = month,
                today = today,
                selected = selected,
                state = state,
                examDays = examDays.keys,
                onSelect = { day -> selected = if (selected == day) null else day },
            )
        }

        MonthStats(monthStart, state)
        Legend()

        AnimatedVisibility(
            visible = selected != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            shownDay?.let { day ->
                DayDetails(
                    day = day,
                    state = state,
                    catalog = catalog,
                    exams = examDays[day].orEmpty().map { it.name } + deadlineDays[day].orEmpty().map { "Дедлайн: ${it.name}" },
                )
            }
        }
    }
}

@Composable
private fun MonthGrid(
    monthStart: Long,
    today: Long,
    selected: Long?,
    state: UiState,
    examDays: Set<Long>,
    onSelect: (Long) -> Unit,
) {
    val cal = Calendar.getInstance().apply { timeInMillis = monthStart }
    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leading = Prep.dayIndex(monthStart) // 0 — понедельник
    val cells = leading + daysInMonth
    val rows = (cells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Prep.DAY_SHORT.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        var day = monthStart
        repeat(rows) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { c ->
                    val index = r * 7 + c
                    if (index < leading || index >= cells) {
                        Spacer(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val thisDay = day
                        DayCell(
                            dayOfMonth = index - leading + 1,
                            goalUsed = state.dayGoalMinutes[thisDay],
                            goal = state.goalMinutes,
                            studied = (state.studyByDay[thisDay] ?: 0) > 0,
                            exam = thisDay in examDays,
                            isToday = thisDay == today,
                            isFuture = thisDay > today,
                            isSelected = thisDay == selected,
                            onClick = { onSelect(thisDay) },
                            modifier = Modifier.weight(1f),
                        )
                        day = TimeUtils.nextDay(day)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    dayOfMonth: Int,
    goalUsed: Int?,
    goal: Int,
    studied: Boolean,
    exam: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val color = dayColor(goalUsed, goal)
    val outline = when {
        isSelected -> MaterialTheme.colorScheme.onSurface
        isToday -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }
    Box(
        modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.small)
            .background(color)
            .border(2.dp, outline, MaterialTheme.shapes.small)
            .clickable(enabled = !isFuture, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (exam) "🎓" else "$dayOfMonth",
            fontSize = if (exam) 14.sp else 12.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = if (isFuture) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.onSurface,
        )
        if (studied) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface)
            )
        }
    }
}

/** Цвет дня: зелёный в пределах цели (насыщеннее — чем меньше времени), красный — сверх цели. */
@Composable
private fun dayColor(used: Int?, goal: Int): Color {
    val empty = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    if (used == null) return empty
    val good = Color(0xFF3FA66B)
    val bad = MaterialTheme.colorScheme.error
    return if (used <= goal) {
        val strength = 1f - used.toFloat() / goal.coerceAtLeast(1) // 0 у самой цели, 1 — почти без экрана
        lerp(empty, good, 0.35f + 0.65f * strength)
    } else {
        val over = ((used - goal).toFloat() / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
        lerp(empty, bad, 0.35f + 0.65f * over)
    }
}

@Composable
private fun MonthStats(monthStart: Long, state: UiState) {
    val monthEnd = shiftMonth(monthStart, 1)
    val days = state.dayGoalMinutes.filterKeys { it in monthStart until monthEnd }
    if (days.isEmpty()) {
        Text(
            "Нет данных за этот месяц — они копятся с момента установки.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        return
    }
    val within = days.count { it.value <= state.goalMinutes }
    val totals = state.dayMinutes.filterKeys { it in monthStart until monthEnd }.values
    val avg = if (totals.isEmpty()) 0 else totals.sum() / totals.size
    val study = state.studyByDay.filterKeys { it in monthStart until monthEnd }.values.sum()
    Text(
        "В цели $within из ${days.size} дн. · в среднем ${TimeUtils.formatMinutes(avg)}" +
            if (study > 0) " · учёба ${TimeUtils.formatMinutes(study)}" else "",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Legend() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 6.dp),
    ) {
        LegendItem(dayColor(0, 100), "в цели")
        LegendItem(dayColor(200, 100), "сверх")
        LegendItem(dayColor(null, 100), "нет данных")
        Text("• учёба", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(MaterialTheme.shapes.extraSmall).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayDetails(day: Long, state: UiState, catalog: AppCatalog, exams: List<String>) {
    val minutes = state.dayMinutes[day]
    val used = state.dayGoalMinutes[day]
    val study = state.studyByDay[day] ?: 0
    // Подробности по приложениям есть только за последнюю неделю — дальше система их не хранит.
    val apps = state.week.firstOrNull { it.dayStart == day }?.apps.orEmpty().take(3)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp)
    ) {
        Text(dayFormat.format(Date(day)).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        if (minutes == null) {
            Text("Нет данных за этот день", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("📱 ${TimeUtils.formatMinutes(minutes)} в телефоне", style = MaterialTheme.typography.bodyMedium)
            if (used != null) {
                val diff = used - state.goalMinutes
                Text(
                    if (diff <= 0) "✅ В пределах цели, запас ${TimeUtils.formatMinutes(-diff)}"
                    else "⚠️ Сверх цели на ${TimeUtils.formatMinutes(diff)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (study > 0) Text("📖 Учёба ${TimeUtils.formatMinutes(study)}", style = MaterialTheme.typography.bodyMedium)
        exams.forEach { Text("🎓 $it", style = MaterialTheme.typography.bodyMedium) }
        if (apps.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            apps.forEach { app ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    AppIcon(catalog.icon(app.packageName), size = 20.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(catalog.label(app.packageName), modifier = Modifier.weight(1f), maxLines = 1,
                        style = MaterialTheme.typography.bodySmall)
                    Text(TimeUtils.formatDuration(app.timeMs), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun firstOfMonth(time: Long): Long = Calendar.getInstance().run {
    timeInMillis = time
    set(Calendar.DAY_OF_MONTH, 1)
    TimeUtils.startOfDay(timeInMillis)
}

private fun shiftMonth(monthStart: Long, delta: Int): Long = Calendar.getInstance().run {
    timeInMillis = monthStart
    add(Calendar.MONTH, delta)
    TimeUtils.startOfDay(timeInMillis)
}
