package com.laromie.rostok.ui

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.laromie.rostok.data.AppCatalog
import com.laromie.rostok.data.Prefs
import com.laromie.rostok.data.PrepRepository
import com.laromie.rostok.data.Rewards
import com.laromie.rostok.data.TimeUtils
import com.laromie.rostok.data.UsageRepository
import com.laromie.rostok.data.WeeklyRecap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Итоги недели: три карточки в формате сторис, любой можно поделиться. */
class RecapActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RostokTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var cards by remember { mutableStateOf<List<Bitmap>?>(null) }
                    LaunchedEffect(Unit) { cards = withContext(Dispatchers.Default) { buildCards() } }
                    val list = cards
                    if (list == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        RecapPager(list, onShare = ::share, onClose = ::finish)
                    }
                }
            }
        }
    }

    private fun buildCards(): List<Bitmap> {
        val prefs = Prefs(this)
        val usage = UsageRepository(this)
        val catalog = AppCatalog(this)
        // Последние 7 завершённых дней: со вчера на 6 дней назад.
        var day = TimeUtils.previousDay(TimeUtils.startOfToday())
        val starts = ArrayList<Long>()
        repeat(7) {
            starts.add(day)
            day = TimeUtils.previousDay(day)
        }
        val days = starts.reversed().map { usage.day(it) }
        val first = days.first().dayStart
        val study = PrepRepository(this).sessions().filter { it.start >= first && it.start < TimeUtils.startOfToday() }
            .sumOf { it.minutes }
        val recap = WeeklyRecap.build(
            days = days,
            history = prefs.dayHistory(),
            goalUsage = prefs::goalUsageMs,
            goalMinutes = prefs.dailyGoalMinutes,
            studyMinutes = study,
            streak = prefs.streak,
        )
        return RecapCards(
            recap = recap,
            accent = Appearance.settings.accent.color,
            characterEmoji = Rewards(prefs, usage).plantStage().emoji,
            appLabel = catalog::label,
            appIcon = catalog::icon,
        ).all()
    }

    private fun share(bitmap: Bitmap) {
        val dir = File(cacheDir, "recap").apply { mkdirs() }
        val file = File(dir, "rostok-week.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Поделиться итогами"))
    }
}

@Composable
private fun RecapPager(cards: List<Bitmap>, onShare: (Bitmap) -> Unit, onClose: () -> Unit) {
    val pager = rememberPagerState { cards.size }
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Итоги недели", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = remember(page) { cards[page].asImageBitmap() },
                    contentDescription = "Карточка ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .aspectRatio(9f / 16f)
                        .clip(MaterialTheme.shapes.large),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(cards.size) { i ->
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == pager.currentPage) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Закрыть") }
            Button(onClick = { onShare(cards[pager.currentPage]) }, modifier = Modifier.weight(1f)) {
                Text("Поделиться")
            }
        }
    }
}
