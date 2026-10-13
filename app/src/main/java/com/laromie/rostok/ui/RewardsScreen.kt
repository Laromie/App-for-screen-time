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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laromie.rostok.data.Rewards
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RewardsScreen(state: UiState, modifier: Modifier = Modifier) {
    val dateFormat = SimpleDateFormat("d MMM, HH:mm", Locale.forLanguageTag("ru"))
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PlantCard(state) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallStat("🔥", "${state.streak}", "серия дней", Modifier.weight(1f))
                SmallStat("🏅", "${state.bestStreak}", "лучшая серия", Modifier.weight(1f))
                SmallStat("✋", "${state.totalResists}", "отказов", Modifier.weight(1f))
            }
        }
        item { HowToEarn(state.goalMinutes) }
        item { Text("Достижения", style = MaterialTheme.typography.titleMedium) }
        items(Rewards.ACHIEVEMENTS.chunked(3)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { a ->
                    val got = a.id in state.achievements
                    SectionCard(Modifier.weight(1f).alpha(if (got) 1f else 0.4f)) {
                        Text(a.emoji, fontSize = 28.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                        Text(
                            a.title,
                            style = MaterialTheme.typography.labelLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            a.description,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (state.history.isNotEmpty()) {
            item { Text("История", style = MaterialTheme.typography.titleMedium) }
            items(state.history) { h ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(h.reason, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            dateFormat.format(Date(h.time)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (h.delta != 0) {
                        Text(
                            (if (h.delta > 0) "+" else "") + h.delta,
                            color = if (h.delta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlantCard(state: UiState) {
    SectionCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text(state.stage.emoji, fontSize = 80.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            state.stage.name,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Text(
            "🌱 ${state.points} очков · всего заработано ${state.totalEarned}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        val next = state.nextStage
        if (next != null) {
            Spacer(Modifier.height(12.dp))
            val prevMin = state.stage.minPoints
            LinearProgressIndicator(
                progress = { (state.totalEarned - prevMin).toFloat() / (next.minPoints - prevMin) },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )
            Text(
                "До стадии «${next.name}» ${next.emoji}: ещё ${next.minPoints - state.totalEarned}",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SmallStat(emoji: String, value: String, label: String, modifier: Modifier) {
    SectionCard(modifier) {
        Text("$emoji $value", style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HowToEarn(goalMinutes: Int) {
    SectionCard {
        Text("Как зарабатывать очки 🌱", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        listOf(
            "☀️ День в пределах цели (${goalMinutes / 60} ч ${goalMinutes % 60} мин): +${Rewards.DAY_BASE}, " +
                "+1 за каждые 10 сэкономленных минут и бонус за серию",
            "✋ Передумал открывать соцсеть на паузе: +${Rewards.RESIST_POINTS} " +
                "(до ${Rewards.MAX_REWARDED_RESISTS_PER_DAY} раз в день)",
            "🎯 Завершённый фокус: +1 за каждые 2 минуты",
            "🏅 Каждое достижение: +${Rewards.ACHIEVEMENT_POINTS}",
            "⏳ Очки можно потратить: +${Rewards.EXTRA_TIME_MINUTES} мин к лимиту за ${Rewards.EXTRA_TIME_COST}",
        ).forEach {
            Row(Modifier.padding(vertical = 2.dp)) {
                Spacer(Modifier.width(2.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
