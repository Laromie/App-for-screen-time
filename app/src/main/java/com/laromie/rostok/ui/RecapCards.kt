package com.laromie.rostok.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withTranslation
import com.laromie.rostok.data.SummaryText
import com.laromie.rostok.data.TimeUtils
import com.laromie.rostok.data.WeeklyRecap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Карточки итогов недели в формате сторис (1080×1920). Рисуются обычным Canvas,
 * поэтому одна и та же картинка показывается в приложении и уходит в «Поделиться».
 */
class RecapCards(
    private val recap: WeeklyRecap,
    private val accent: Color,
    private val characterEmoji: String,
    private val appLabel: (String) -> String,
    private val appIcon: (String) -> Drawable?,
) {
    private val w = 1080
    private val h = 1920
    private val pad = 96f
    private val dateFormat = SimpleDateFormat("d MMM", Locale.forLanguageTag("ru"))

    fun all(): List<Bitmap> = listOf(screenCard(), appsCard(), resultsCard())

    // ---------- 1. Экранное время ----------

    private fun screenCard(): Bitmap = card("Экран за неделю") { c, top ->
        var y = top
        y = text(c, SummaryText.compact(recap.totalMinutes), y, 150f, bold = true)
        y = text(c, "в среднем ${SummaryText.compact(recap.avgMinutes)} в день", y + 10, 52f, alpha = 220)

        val diff = recap.avgDiff
        if (diff != null) {
            val line = when {
                diff <= -5 -> "На ${SummaryText.compact(-diff)} в день меньше, чем неделю назад 👏"
                diff >= 5 -> "На ${SummaryText.compact(diff)} в день больше, чем неделю назад"
                else -> "Примерно как неделю назад"
            }
            y = pill(c, line, y + 60)
        }

        // Столбики по дням
        val chartTop = y + 120
        val chartH = 520f
        val max = (recap.dailyMinutes.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
        val slot = (w - 2 * pad) / recap.dailyMinutes.size.coerceAtLeast(1)
        val bar = Paint(Paint.ANTI_ALIAS_FLAG)
        val label = textPaint(38f, alpha = 210)
        recap.dailyMinutes.forEachIndexed { i, (day, min) ->
            val bh = chartH * min / max
            val left = pad + i * slot + slot * 0.2f
            val right = pad + (i + 1) * slot - slot * 0.2f
            bar.color = android.graphics.Color.argb(if (day == recap.bestDay?.first) 255 else 150, 255, 255, 255)
            c.drawRoundRect(RectF(left, chartTop + chartH - bh, right, chartTop + chartH), 18f, 18f, bar)
            label.textAlign = Paint.Align.CENTER
            c.drawText(TimeUtils.shortDayName(day), (left + right) / 2, chartTop + chartH + 60, label)
        }
        recap.bestDay?.let { (day, min) ->
            text(c, "Лучший день — ${TimeUtils.shortDayName(day)}: ${SummaryText.compact(min)}", chartTop + chartH + 130, 44f, alpha = 230)
        }
    }

    // ---------- 2. Куда ушло время ----------

    private fun appsCard(): Bitmap = card("Куда ушло время") { c, top ->
        var y = top + 20
        if (recap.topApps.isEmpty()) {
            text(c, "Почти ничего не потратил — так держать 🌿", y, 60f, bold = true)
            return@card
        }
        recap.topApps.forEachIndexed { i, (pkg, min) ->
            val rowTop = y
            appIcon(pkg)?.let { d ->
                runCatching { d.toBitmap(160, 160) }.getOrNull()?.let { c.drawBitmap(it, pad, rowTop, null) }
            }
            text(c, "${i + 1}. ${appLabel(pkg)}", rowTop + 10, 56f, bold = true, x = pad + 200)
            text(c, SummaryText.compact(min), rowTop + 90, 48f, alpha = 220, x = pad + 200)
            y = rowTop + 230
        }
        y += 60
        val movies = recap.movies
        val fact = when {
            movies >= 2 -> "За неделю в телефоне — это $movies полнометражных фильма 🎬"
            recap.totalMinutes >= 45 -> "За неделю в телефоне — это ${recap.totalMinutes / 45} серий сериала 📺"
            else -> "Меньше часа за неделю — впечатляет 🔥"
        }
        pill(c, fact, y)
    }

    // ---------- 3. Результаты ----------

    private fun resultsCard(): Bitmap = card("Мои результаты") { c, top ->
        var y = top
        y = text(c, characterEmoji, y, 220f)
        y = stat(c, "🎯", "${recap.daysWithinGoal} из ${recap.daysWithData.coerceAtLeast(1)}", "дней в пределах цели", y + 40)
        y = stat(c, "🔥", "${recap.streak}", "дней подряд — текущая серия", y + 30)
        if (recap.studyMinutes > 0) {
            y = stat(c, "📖", SummaryText.compact(recap.studyMinutes), "на учёбу за неделю", y + 30)
        }
        stat(c, "🔓", "${recap.pickups}", "разблокировок телефона", y + 30)
    }

    // ---------- Общий каркас ----------

    private fun card(title: String, body: (Canvas, Float) -> Unit): Bitmap {
        val bmp = createBitmap(w, h)
        val c = Canvas(bmp)
        val top = lerp(accent, Color.White, 0.15f).toArgb()
        val bottom = lerp(accent, Color.Black, 0.55f).toArgb()
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        })
        // Мягкое «стеклянное» пятно для глубины.
        c.drawCircle(w * 0.85f, h * 0.12f, 420f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x1FFFFFFF })

        val range = "${dateFormat.format(Date(recap.firstDay))} – ${dateFormat.format(Date(recap.lastDay))}"
        var y = text(c, "🌱 Росток · итоги недели", 140f, 40f, alpha = 220)
        y = text(c, range, y + 6, 40f, alpha = 180)
        y = text(c, title, y + 60, 84f, bold = true)
        body(c, y + 60)
        text(c, "меньше скролла — больше скилла", h - 160f, 38f, alpha = 170)
        return bmp
    }

    private fun stat(c: Canvas, emoji: String, value: String, caption: String, y: Float): Float {
        text(c, emoji, y, 80f)
        text(c, value, y - 6, 80f, bold = true, x = pad + 140)
        return text(c, caption, y + 96, 42f, alpha = 210, x = pad + 140)
    }

    /** Текст в полупрозрачной «пилюле». */
    private fun pill(c: Canvas, line: String, y: Float): Float {
        val paint = textPaint(46f, alpha = 255)
        val layout = layout(line, paint, (w - 2 * pad - 80).toInt())
        val rect = RectF(pad, y, w - pad, y + layout.height + 64f)
        c.drawRoundRect(rect, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x2EFFFFFF })
        c.drawRoundRect(rect, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0x66FFFFFF
        })
        c.withTranslation(pad + 40, y + 32) { layout.draw(this) }
        return rect.bottom
    }

    /** Рисует (с переносом строк) и возвращает нижнюю границу текста. */
    private fun text(
        c: Canvas,
        value: String,
        y: Float,
        size: Float,
        bold: Boolean = false,
        alpha: Int = 255,
        x: Float = pad,
    ): Float {
        val layout = layout(value, textPaint(size, alpha, bold), (w - x - pad).toInt())
        c.withTranslation(x, y) { layout.draw(this) }
        return y + layout.height
    }

    private fun textPaint(size: Float, alpha: Int = 255, bold: Boolean = false) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            color = android.graphics.Color.argb(alpha, 255, 255, 255)
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
}
