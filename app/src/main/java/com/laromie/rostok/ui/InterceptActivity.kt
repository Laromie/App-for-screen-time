package com.laromie.rostok.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laromie.rostok.data.AppCatalog
import com.laromie.rostok.data.Prefs
import com.laromie.rostok.data.Prep
import com.laromie.rostok.data.PrepItem
import com.laromie.rostok.data.PrepRepository
import com.laromie.rostok.data.Rewards
import com.laromie.rostok.data.Strictness
import com.laromie.rostok.data.Study
import com.laromie.rostok.data.TimeUtils
import com.laromie.rostok.data.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Напоминание о ближайшем экзамене для экранов паузы и блокировки. */
private data class PrepHint(
    val item: PrepItem,
    val daysLeft: Int,
    val doneMinutes: Int,
    val strictness: Strictness,
    val studyTime: Boolean,
)

/** Экран, который встаёт между пользователем и отвлекающим приложением. */
class InterceptActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return finish()
        val mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_PAUSE

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        val prefs = Prefs(this)
        val usage = UsageRepository(this)
        val rewards = Rewards(prefs, usage)
        val prepRepo = PrepRepository(this)
        val study = Study(prefs, rewards, prepRepo)
        val label = AppCatalog(this).label(pkg)

        val prep = prepRepo.load()
        val hint = prep?.nearest()?.let { item ->
            PrepHint(
                item = item,
                daysLeft = Prep.daysUntil(item.date),
                doneMinutes = study.minutesFor(item.id),
                strictness = prep.strictness,
                studyTime = prep.isStudyTime(),
            )
        }

        val startStudy: (PrepItem) -> Unit = { item ->
            study.start(item, STUDY_FROM_PAUSE_MINUTES)
            startActivity(MainActivity.prepIntent(this))
            finish()
        }
        val emergency: () -> Unit = {
            study.emergencyUnlock(pkg, label)
            finish()
        }

        setContent {
            RostokTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (mode) {
                        MODE_LIMIT -> LimitScreen(
                            label = label,
                            limitMinutes = prefs.limitMinutes(pkg) + prefs.bonusMinutesToday(pkg),
                            points = prefs.points,
                            onClose = ::goHome,
                            onBuy = {
                                if (rewards.buyExtraTime(pkg, label)) {
                                    prefs.setUnlockUntil(
                                        pkg,
                                        System.currentTimeMillis() + Rewards.EXTRA_TIME_MINUTES * TimeUtils.MINUTE,
                                    )
                                    finish()
                                }
                            },
                        )

                        MODE_FOCUS -> FocusBlockScreen(
                            label = label,
                            focusUntil = prefs.focusUntil,
                            subject = prepRepo.activeItemId?.let { id -> prep?.items?.firstOrNull { it.id == id }?.name },
                            onClose = ::goHome,
                        )

                        MODE_STUDY -> BlockLayout(
                            emoji = "📚",
                            title = "Сейчас учебное время",
                            text = hint?.let { "${it.item.name} ${Prep.whenText(it.daysLeft)}. $label подождёт до конца учебных часов." }
                                ?: "$label подождёт до конца учебных часов.",
                            onClose = ::goHome,
                        ) {
                            hint?.let { h ->
                                FilledTonalButton(onClick = { startStudy(h.item) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("📖 Заниматься: ${h.item.name}")
                                }
                            }
                            EmergencyExit(onConfirm = emergency)
                        }

                        MODE_SLEEP -> BlockLayout(
                            emoji = "🌙",
                            title = "Пора спать",
                            text = hint?.let { "${it.item.name} ${Prep.whenText(it.daysLeft)}. Выспаться сейчас полезнее, чем листать ленту." }
                                ?: "Завтра важный день. Выспаться сейчас полезнее, чем листать ленту.",
                            onClose = ::goHome,
                        ) {
                            EmergencyExit(onConfirm = emergency)
                        }

                        else -> {
                            val serious = hint != null && hint.studyTime && hint.strictness == Strictness.SERIOUS
                            PauseScreen(
                                label = label,
                                pauseSeconds = if (serious) maxOf(prefs.pauseSeconds, SERIOUS_PAUSE_SECONDS) else prefs.pauseSeconds,
                                openOptions = if (serious) listOf(SERIOUS_UNLOCK_MINUTES) else listOf(5, 10, 15, 20),
                                hint = hint,
                                loadStats = {
                                    withContext(Dispatchers.IO) {
                                        usage.today().let { it.opensFor(pkg) to it.timeFor(pkg) }
                                    }
                                },
                                onStudy = startStudy,
                                onResist = {
                                    rewards.onResist(label)
                                    goHome()
                                },
                                onOpen = { minutes ->
                                    prefs.setUnlockUntil(pkg, System.currentTimeMillis() + minutes * TimeUtils.MINUTE)
                                    finish()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun goHome() {
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    companion object {
        const val MODE_PAUSE = "pause"
        const val MODE_LIMIT = "limit"
        const val MODE_FOCUS = "focus"
        const val MODE_STUDY = "study"
        const val MODE_SLEEP = "sleep"
        private const val EXTRA_PACKAGE = "pkg"
        private const val EXTRA_MODE = "mode"
        private const val STUDY_FROM_PAUSE_MINUTES = 25
        private const val SERIOUS_PAUSE_SECONDS = 30
        private const val SERIOUS_UNLOCK_MINUTES = 2

        fun intent(context: Context, pkg: String, mode: String): Intent =
            Intent(context, InterceptActivity::class.java)
                .putExtra(EXTRA_PACKAGE, pkg)
                .putExtra(EXTRA_MODE, mode)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

@Composable
private fun PauseScreen(
    label: String,
    pauseSeconds: Int,
    openOptions: List<Int>,
    hint: PrepHint?,
    loadStats: suspend () -> Pair<Int, Long>,
    onStudy: (PrepItem) -> Unit,
    onResist: () -> Unit,
    onOpen: (minutes: Int) -> Unit,
) {
    var secondsLeft by remember { mutableIntStateOf(pauseSeconds) }
    var stats by remember { mutableStateOf<Pair<Int, Long>?>(null) }
    LaunchedEffect(Unit) { stats = loadStats() }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000)
            secondsLeft--
        }
    }

    // Вдох 4 секунды — выдох 4 секунды.
    val breath = rememberInfiniteTransition(label = "breath")
    val scale by breath.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale",
    )

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(180.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(180.dp)
                    .scale(scale)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            )
            Text(
                if (secondsLeft > 0) "$secondsLeft" else "🌱",
                fontSize = 48.sp,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            when {
                secondsLeft > 0 -> "Сделай медленный вдох…"
                hint != null -> "Открыть $label или позаниматься?"
                else -> "Ты правда хочешь открыть $label?"
            },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        stats?.let { (opens, time) ->
            Text(
                "Сегодня: открыто $opens раз, ${TimeUtils.formatDuration(time)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        hint?.let {
            Spacer(Modifier.height(16.dp))
            PrepHintCard(it)
        }
        Spacer(Modifier.weight(1f))

        if (hint != null) {
            Button(onClick = { onStudy(hint.item) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("📖 Заниматься 25 мин: ${hint.item.name}", maxLines = 1)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onResist, modifier = Modifier.fillMaxWidth()) {
                Text("Просто не открывать  +${Rewards.RESIST_POINTS} 🌱")
            }
        } else {
            Button(onClick = onResist, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Не буду открывать  +${Rewards.RESIST_POINTS} 🌱", fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (secondsLeft == 0) {
            Text(
                "Открыть на:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                openOptions.forEach { m ->
                    TextButton(onClick = { onOpen(m) }) { Text("$m мин") }
                }
            }
        } else {
            // Место под кнопки, чтобы интерфейс не прыгал.
            Spacer(Modifier.height(68.dp))
        }
    }
}

@Composable
private fun PrepHintCard(hint: PrepHint) {
    val target = hint.item.targetMinutes.coerceAtLeast(1)
    SectionCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            "${hint.item.name} — ${Prep.whenText(hint.daysLeft)}",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (hint.doneMinutes.toFloat() / target).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            drawStopIndicator = {},
        )
        val left = (target - hint.doneMinutes).coerceAtLeast(0)
        Text(
            "Подготовка: ${TimeUtils.formatMinutes(hint.doneMinutes)} из ${TimeUtils.formatMinutes(target)}" +
                if (left > 0) " · осталось ${TimeUtils.formatMinutes(left)}" else " · цель выполнена 🎉",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun LimitScreen(
    label: String,
    limitMinutes: Int,
    points: Int,
    onClose: () -> Unit,
    onBuy: () -> Unit,
) {
    BlockLayout(
        emoji = "⏳",
        title = "Лимит на $label исчерпан",
        text = "Сегодня ты уже провёл здесь ${TimeUtils.formatMinutes(limitMinutes)}. " +
            "Завтра лимит обновится — а сейчас можно заняться чем-то другим.",
        onClose = onClose,
    ) {
        if (points >= Rewards.EXTRA_TIME_COST) {
            TextButton(onClick = onBuy) {
                Text("Ещё ${Rewards.EXTRA_TIME_MINUTES} мин за ${Rewards.EXTRA_TIME_COST} 🌱 (у тебя $points)")
            }
        } else {
            Text(
                "Нужно ${Rewards.EXTRA_TIME_COST} 🌱, чтобы купить ещё ${Rewards.EXTRA_TIME_MINUTES} мин (у тебя $points)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FocusBlockScreen(label: String, focusUntil: Long, subject: String?, onClose: () -> Unit) {
    var left by remember { mutableStateOf(focusUntil - System.currentTimeMillis()) }
    LaunchedEffect(focusUntil) {
        while (left > 0) {
            delay(1000)
            left = focusUntil - System.currentTimeMillis()
        }
    }
    val min = (left.coerceAtLeast(0) / 60_000)
    val sec = (left.coerceAtLeast(0) / 1000) % 60
    BlockLayout(
        emoji = "🎯",
        title = if (subject != null) "Идёт занятие: $subject" else "Сейчас режим фокуса",
        text = "$label подождёт. До конца ${"%d:%02d".format(min, sec)}.",
        onClose = onClose,
    ) {}
}

/** Аварийный выход: нужно напечатать фразу и подождать — чтобы не сдаваться на автомате. */
@Composable
private fun EmergencyExit(onConfirm: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }) {
        Text("Мне правда нужно", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (!open) return

    var text by remember { mutableStateOf("") }
    var wait by remember { mutableIntStateOf(Study.EMERGENCY_WAIT_SECONDS) }
    LaunchedEffect(Unit) {
        while (wait > 0) {
            delay(1000)
            wait--
        }
    }
    val phraseOk = text.trim().lowercase() == Study.EMERGENCY_PHRASE
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Аварийный выход") },
        text = {
            Column {
                Text(
                    "Откроется на ${Study.EMERGENCY_MINUTES} мин и спишет ${Study.EMERGENCY_COST} 🌱. " +
                        "Чтобы продолжить, напиши: «${Study.EMERGENCY_PHRASE}»",
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = phraseOk && wait == 0) {
                Text(if (wait > 0) "Подожди $wait с" else "Открыть")
            }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Я передумал") } },
    )
}

@Composable
private fun BlockLayout(
    emoji: String,
    title: String,
    text: String,
    onClose: () -> Unit,
    extra: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text(emoji, fontSize = 72.sp)
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Закрыть", fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        extra()
    }
}
