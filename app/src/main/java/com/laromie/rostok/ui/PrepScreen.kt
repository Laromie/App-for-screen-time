package com.laromie.rostok.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laromie.rostok.data.InstalledApp
import com.laromie.rostok.data.ItemKind
import com.laromie.rostok.data.Prep
import com.laromie.rostok.data.PrepItem
import com.laromie.rostok.data.PrepTemplate
import com.laromie.rostok.data.Strictness
import com.laromie.rostok.data.TimeUtils
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat("d MMMM", Locale.forLanguageTag("ru"))

/** Что сейчас показывает вкладка «Учёба». Порядок задаёт направление анимации. */
private sealed class PrepPage(val order: Int) {
    data object Picker : PrepPage(0)
    data class Dashboard(val prep: Prep) : PrepPage(1)
    data class Editor(val draft: Prep, val isNew: Boolean) : PrepPage(2)
}

@Composable
fun PrepScreen(state: UiState, vm: MainViewModel, modifier: Modifier = Modifier) {
    // Черновик, который сейчас редактируется (новая подготовка или изменение текущей).
    var draft by remember { mutableStateOf<Prep?>(null) }

    val saved = state.prep
    val page: PrepPage = draft?.let { PrepPage.Editor(it, isNew = saved == null) }
        ?: saved?.let { PrepPage.Dashboard(it) }
        ?: PrepPage.Picker

    // Вглубь (к настройке) — экран въезжает справа, назад — слева.
    AnimatedContent(
        targetState = page,
        contentKey = { it::class },
        transitionSpec = {
            val dir = if (targetState.order > initialState.order) 1 else -1
            (slideInHorizontally(tween(300)) { it * dir / 3 } + fadeIn(tween(300))) togetherWith
                (slideOutHorizontally(tween(250)) { -it * dir / 4 } + fadeOut(tween(200)))
        },
        modifier = modifier,
        label = "prepPage",
    ) { p ->
        when (p) {
            is PrepPage.Editor -> PrepEditor(
                initial = p.draft,
                trackedApps = state.apps.filter { it.packageName in state.tracked },
                isNew = p.isNew,
                onSave = {
                    vm.savePrep(it)
                    draft = null
                },
                onCancel = { draft = null },
                modifier = Modifier.fillMaxSize(),
            )

            PrepPage.Picker -> TemplatePicker(
                onPick = { draft = Prep.fromTemplate(it) },
                modifier = Modifier.fillMaxSize(),
            )

            is PrepPage.Dashboard -> PrepDashboard(
                state = state,
                prep = p.prep,
                vm = vm,
                onEdit = { draft = p.prep },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Выбор шаблона
// ---------------------------------------------------------------------------

@Composable
private fun TemplatePicker(onPick: (PrepTemplate) -> Unit, modifier: Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Режим подготовки", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Меньше скролла — больше баллов. Выбери, к чему готовишься: обратный отсчёт, учёт занятий " +
                    "и рамки для соцсетей настроятся сами. Любую рамку потом можно поменять.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        items(PrepTemplate.entries) { t ->
            SectionCard(modifier = Modifier.clickable { onPick(t) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.emoji, fontSize = 32.sp)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(t.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            t.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Дашборд
// ---------------------------------------------------------------------------

@Composable
private fun PrepDashboard(
    state: UiState,
    prep: Prep,
    vm: MainViewModel,
    onEdit: () -> Unit,
    modifier: Modifier,
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val today = TimeUtils.startOfToday()
    val next = prep.nearest(today)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${prep.template.emoji} ${prep.title}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Изменить рамки") }
            }
        }

        val activeItem = state.activeItemId?.let { id -> prep.items.firstOrNull { it.id == id } }
        if (activeItem != null && state.focusUntil > System.currentTimeMillis()) {
            item { ActiveSessionCard(activeItem, state.focusUntil, vm) }
        }

        when {
            prep.isFinished -> item {
                SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                    Text("🎉 Всё сдано!", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Ты подготовился и справился. Можно удалить эту подготовку и начать новую.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            next != null && activeItem == null -> item { NextExamCard(next, state.prepMinutes[next.id] ?: 0, vm) }

            next == null -> item {
                SectionCard {
                    Text("Нет предстоящих дат", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Все даты прошли. Отметь сданное или поменяй даты в рамках.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Сегодня", TimeUtils.formatMinutes(state.studyTodayMinutes), Modifier.weight(1f))
                StatCard("За 7 дней", TimeUtils.formatMinutes(state.studyWeekMinutes), Modifier.weight(1f))
            }
        }

        item { RulesCard(prep) }

        item { Text("Что сдаю", style = MaterialTheme.typography.titleMedium) }
        items(prep.items.sortedWith(compareBy<PrepItem> { it.passed }.thenBy { it.date }), key = { it.id }) { item ->
            ItemRow(
                item = item,
                doneMinutes = state.prepMinutes[item.id] ?: 0,
                canStudy = activeItem == null && !item.passed,
                onStudy = { vm.startStudy(item, 25) },
                onPassed = { vm.setPassed(item.id, it) },
            )
        }

        item {
            TextButton(onClick = { confirmDelete = true }) {
                Text("Удалить подготовку", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить «${prep.title}»?") },
            text = { Text("Удалятся даты, рамки и журнал занятий. Очки и достижения останутся.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePrep()
                    confirmDelete = false
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NextExamCard(item: PrepItem, doneMinutes: Int, vm: MainViewModel) {
    val days = Prep.daysUntil(item.date)
    SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text("Ближайшее: ${item.kind.title.lowercase()}", style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (days == 0) "Сегодня" else "$days",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (days > 0) {
                Text(
                    " ${daysWord(days)}",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
        Text("${item.name} · ${dateFormat.format(Date(item.date))}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        StudyProgress(doneMinutes, item.targetMinutes)
        Spacer(Modifier.height(12.dp))
        Text("Заниматься сейчас:", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(25, 45, 60, 90).forEach { m ->
                FilledTonalButton(onClick = { vm.startStudy(item, m) }) { Text("$m мин") }
            }
        }
        Text(
            "На время занятия соцсети блокируются, время запишется на «${item.name}».",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ActiveSessionCard(item: PrepItem, until: Long, vm: MainViewModel) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(until) {
        while (now < until) {
            delay(1000)
            now = System.currentTimeMillis()
        }
        vm.onFocusFinished()
    }
    val left = (until - now).coerceAtLeast(0)
    SectionCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
        Text("📖 Идёт занятие", style = MaterialTheme.typography.labelLarge)
        Text(item.name, style = MaterialTheme.typography.titleLarge)
        Text(
            "%d:%02d".format(left / 60_000, left / 1000 % 60),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Light,
        )
        Text("Соцсети заблокированы до конца занятия.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = vm::stopStudy) { Text("Закончить раньше") }
        Text(
            "Время засчитается, но бонус за полное занятие — нет.",
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun StudyProgress(done: Int, target: Int) {
    val safeTarget = target.coerceAtLeast(1)
    LinearProgressIndicator(
        progress = { (done.toFloat() / safeTarget).coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth(),
        drawStopIndicator = {},
    )
    val left = (safeTarget - done).coerceAtLeast(0)
    Text(
        "${TimeUtils.formatMinutes(done)} из ${TimeUtils.formatMinutes(safeTarget)}" +
            if (left > 0) " · осталось ${TimeUtils.formatMinutes(left)}" else " · цель выполнена 🎉",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    SectionCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun RulesCard(prep: Prep) {
    val studyNow = prep.isStudyTime()
    SectionCard {
        Text("Мои рамки", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text("${prep.strictness.emoji} ${prep.strictness.title} — ${prep.strictness.description}")
        Text("🕓 ${scheduleText(prep)}" + if (studyNow) "  · сейчас учебное время" else "")
        if (prep.sleepEnabled) {
            Text("🌙 Перед экзаменом отбой в ${Prep.formatClock(prep.bedtime)}, до ${Prep.formatClock(prep.wakeTime)}")
        }
        if (prep.exceptions.isNotEmpty()) {
            Text("✅ Исключений: ${prep.exceptions.size} — их не ограничиваю")
        }
    }
}

@Composable
private fun ItemRow(
    item: PrepItem,
    doneMinutes: Int,
    canStudy: Boolean,
    onStudy: () -> Unit,
    onPassed: (Boolean) -> Unit,
) {
    val days = Prep.daysUntil(item.date)
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    (if (item.passed) "✅ " else "") + item.name,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${item.kind.title} · ${dateFormat.format(Date(item.date))}" +
                        if (!item.passed) " · ${Prep.whenText(days)}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (canStudy) {
                IconButton(onClick = onStudy) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Заниматься 25 минут")
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Checkbox(checked = item.passed, onCheckedChange = onPassed)
                Text("сдал", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(4.dp))
        StudyProgress(doneMinutes, item.targetMinutes)
    }
}

// ---------------------------------------------------------------------------
// Редактор рамок: 3 обязательных шага + расширенные настройки
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrepEditor(
    initial: Prep,
    trackedApps: List<InstalledApp>,
    isNew: Boolean,
    onSave: (Prep) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    var prep by remember { mutableStateOf(initial) }
    var advanced by remember { mutableStateOf(!isNew) }
    var pickDateFor by remember { mutableStateOf<String?>(null) }

    fun updateItem(id: String, change: (PrepItem) -> PrepItem) {
        prep = prep.copy(items = prep.items.map { if (it.id == id) change(it) else it })
    }

    val valid = prep.title.isNotBlank() && prep.items.isNotEmpty() && prep.items.all { it.name.isNotBlank() }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                if (isNew) "${prep.template.emoji} Новая подготовка" else "Рамки подготовки",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Шаг 1 — что сдаёшь
        item { StepTitle(1, "Что сдаёшь") }
        item {
            OutlinedTextField(
                value = prep.title,
                onValueChange = { prep = prep.copy(title = it) },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Шаг 2 — когда
        item {
            StepTitle(2, "Когда и сколько готовиться")
            if (prep.template == PrepTemplate.EGE || prep.template == PrepTemplate.OGE) {
                Text(
                    "Даты стоят примерные — сверь с официальным расписанием и поправь.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(prep.items, key = { it.id }) { item ->
            SectionCard(Modifier.animateItem()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = item.name,
                        onValueChange = { v -> updateItem(item.id) { it.copy(name = v) } },
                        label = { Text("Предмет / что сдаю") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    if (prep.items.size > 1) {
                        IconButton(onClick = { prep = prep.copy(items = prep.items - item) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                        }
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ItemKind.entries.forEach { kind ->
                        FilterChip(
                            selected = item.kind == kind,
                            onClick = { updateItem(item.id) { it.copy(kind = kind) } },
                            label = { Text(kind.title) },
                        )
                    }
                }
                OutlinedButton(onClick = { pickDateFor = item.id }) {
                    Text("📅 ${dateFormat.format(Date(item.date))} · ${Prep.whenText(Prep.daysUntil(item.date))}")
                }
                Stepper(
                    label = "Цель подготовки",
                    value = "${item.targetMinutes / 60} ч",
                    onMinus = { updateItem(item.id) { it.copy(targetMinutes = (it.targetMinutes - 60).coerceAtLeast(60)) } },
                    onPlus = { updateItem(item.id) { it.copy(targetMinutes = it.targetMinutes + 60) } },
                )
            }
        }
        item {
            TextButton(onClick = {
                val last = prep.items.maxOfOrNull { it.date } ?: TimeUtils.startOfToday()
                prep = prep.copy(
                    items = prep.items + PrepItem(
                        name = "",
                        kind = prep.items.lastOrNull()?.kind ?: ItemKind.EXAM,
                        date = Prep.plusDays(last, 3),
                        targetMinutes = prep.items.lastOrNull()?.targetMinutes ?: 10 * 60,
                    )
                )
            }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Добавить предмет")
            }
        }

        // Шаг 3 — строгость
        item { StepTitle(3, "Насколько строго в учебные часы") }
        items(Strictness.entries) { s ->
            val selected = prep.strictness == s
            val color by animateColorAsState(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                label = "strictness",
            )
            SectionCard(
                modifier = Modifier.clickable { prep = prep.copy(strictness = s) },
                containerColor = color,
            ) {
                Text("${s.emoji} ${s.title}", style = MaterialTheme.typography.titleMedium)
                Text(s.description, style = MaterialTheme.typography.bodySmall)
            }
        }

        // Расширенные настройки
        item {
            TextButton(onClick = { advanced = !advanced }) {
                Text(if (advanced) "Скрыть расширенные настройки ▲" else "Расширенные настройки ▼")
            }
        }
        if (advanced) {
            item(key = "adv1") {
                SectionCard(Modifier.animateItem()) {
                    Text("🕓 Учебные часы", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "В это время действуют рамки строгости. Вне учебных часов — обычная пауза и лимиты.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Prep.DAY_SHORT.forEachIndexed { i, d ->
                            val on = prep.studyDays and (1 shl i) != 0
                            FilterChip(
                                selected = on,
                                onClick = { prep = prep.copy(studyDays = prep.studyDays xor (1 shl i)) },
                                label = { Text(d) },
                            )
                        }
                    }
                    Stepper(
                        label = "Начало",
                        value = Prep.formatClock(prep.studyStart),
                        onMinus = { prep = prep.copy(studyStart = shiftClock(prep.studyStart, -30)) },
                        onPlus = { prep = prep.copy(studyStart = shiftClock(prep.studyStart, 30)) },
                    )
                    Stepper(
                        label = "Конец",
                        value = Prep.formatClock(prep.studyEnd),
                        onMinus = { prep = prep.copy(studyEnd = shiftClock(prep.studyEnd, -30)) },
                        onPlus = { prep = prep.copy(studyEnd = shiftClock(prep.studyEnd, 30)) },
                    )
                }
            }
            item(key = "adv2") {
                SectionCard(Modifier.animateItem()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("🌙 Отбой перед экзаменом", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Ночью перед экзаменом соцсети блокируются, чтобы выспаться",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = prep.sleepEnabled,
                            onCheckedChange = { prep = prep.copy(sleepEnabled = it) },
                        )
                    }
                    if (prep.sleepEnabled) {
                        Stepper(
                            label = "Отбой",
                            value = Prep.formatClock(prep.bedtime),
                            onMinus = { prep = prep.copy(bedtime = shiftClock(prep.bedtime, -30)) },
                            onPlus = { prep = prep.copy(bedtime = shiftClock(prep.bedtime, 30)) },
                        )
                        Stepper(
                            label = "Подъём",
                            value = Prep.formatClock(prep.wakeTime),
                            onMinus = { prep = prep.copy(wakeTime = shiftClock(prep.wakeTime, -30)) },
                            onPlus = { prep = prep.copy(wakeTime = shiftClock(prep.wakeTime, 30)) },
                        )
                    }
                }
            }
            item(key = "adv3") {
                SectionCard(Modifier.animateItem()) {
                    Text("✅ Не ограничивать в учебные часы", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Например, мессенджер с чатом группы. Список берётся из вкладки «Приложения».",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (trackedApps.isEmpty()) {
                        Text("Отвлекающих приложений пока нет.", style = MaterialTheme.typography.bodyMedium)
                    }
                    trackedApps.forEach { app ->
                        val exempt = app.packageName in prep.exceptions
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIcon(app.icon, size = 32.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(app.label, modifier = Modifier.weight(1f))
                            Checkbox(
                                checked = exempt,
                                onCheckedChange = {
                                    prep = prep.copy(
                                        exceptions = if (it) prep.exceptions + app.packageName
                                        else prep.exceptions - app.packageName
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        item(key = "actions") {
            Row(Modifier.animateItem(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Отмена") }
                Button(onClick = { onSave(prep) }, enabled = valid, modifier = Modifier.weight(1f)) {
                    Text(if (isNew) "Начать подготовку" else "Сохранить")
                }
            }
            if (!valid) {
                Text(
                    "Заполни название и все предметы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    pickDateFor?.let { id ->
        val item = prep.items.firstOrNull { it.id == id }
        if (item == null) {
            pickDateFor = null
        } else {
            ExamDatePicker(
                initial = item.date,
                onPick = { date ->
                    updateItem(id) { it.copy(date = date) }
                    pickDateFor = null
                },
                onDismiss = { pickDateFor = null },
            )
        }
    }
}

@Composable
private fun StepTitle(n: Int, text: String) {
    Text(
        "Шаг $n · $text",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = onMinus) { Text("−", fontSize = 20.sp) }
        Text(value, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onPlus) { Text("+", fontSize = 20.sp) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExamDatePicker(initial: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    // DatePicker работает с полуночью по UTC, а мы храним полночь по местному времени.
    val state = rememberDatePickerState(initialSelectedDateMillis = localDayToUtc(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(utcToLocalDay(it)) } ?: onDismiss()
            }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    ) {
        DatePicker(state = state)
    }
}

private fun localDayToUtc(localDay: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localDay }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).run {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        timeInMillis
    }
}

private fun utcToLocalDay(utc: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utc }
    return Calendar.getInstance().run {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH))
        timeInMillis
    }
}

private fun shiftClock(minutes: Int, delta: Int): Int = Math.floorMod(minutes + delta, 24 * 60)

private fun scheduleText(prep: Prep): String {
    val days = when (prep.studyDays) {
        Prep.ALL_DAYS -> "каждый день"
        Prep.WEEKDAYS -> "Пн–Пт"
        0 -> "дни не выбраны"
        else -> Prep.DAY_SHORT.filterIndexed { i, _ -> prep.studyDays and (1 shl i) != 0 }.joinToString(", ")
    }
    return "$days, ${Prep.formatClock(prep.studyStart)}–${Prep.formatClock(prep.studyEnd)}"
}

/** "день", "дня", "дней". */
private fun daysWord(n: Int): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> "дней"
        mod10 == 1 -> "день"
        mod10 in 2..4 -> "дня"
        else -> "дней"
    }
}
