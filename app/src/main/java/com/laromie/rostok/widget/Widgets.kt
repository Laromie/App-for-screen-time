package com.laromie.rostok.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import com.laromie.rostok.R
import com.laromie.rostok.data.AppCatalog
import com.laromie.rostok.data.Prefs
import com.laromie.rostok.data.Prep
import com.laromie.rostok.data.PrepRepository
import com.laromie.rostok.data.Rewards
import com.laromie.rostok.data.TimeUtils
import com.laromie.rostok.data.UsageRepository
import com.laromie.rostok.ui.MainActivity
import java.util.concurrent.Executors

/** Общий провайдер: любое системное обновление перерисовывает все виджеты разом. */
abstract class GlassWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Widgets.updateAll(context) { pending.finish() }
    }
}

class ScreenTimeWidget : GlassWidget()
class TopAppsWidget : GlassWidget()
class GoalsWidget : GlassWidget()

/** Сборка содержимого виджетов. Считаем в фоне: статистика за день может занять сотни миллисекунд. */
object Widgets {

    private val executor = Executors.newSingleThreadExecutor()

    fun updateAll(context: Context, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        executor.execute {
            try {
                update(app)
            } catch (e: Exception) {
                // Виджет — второстепенная вещь: ошибка не должна ронять приложение.
            } finally {
                onDone?.invoke()
            }
        }
    }

    private fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        fun ids(cls: Class<*>) = manager.getAppWidgetIds(ComponentName(context, cls))
        val screenIds = ids(ScreenTimeWidget::class.java)
        val topIds = ids(TopAppsWidget::class.java)
        val goalIds = ids(GoalsWidget::class.java)
        if (screenIds.isEmpty() && topIds.isEmpty() && goalIds.isEmpty()) return

        val prefs = Prefs(context)
        val usage = UsageRepository(context)
        val hasAccess = usage.hasPermission()
        val today = if (hasAccess) usage.today() else null

        if (screenIds.isNotEmpty()) {
            val views = screenTimeViews(context, prefs, usage, today?.totalMs, hasAccess)
            screenIds.forEach { manager.updateAppWidget(it, views) }
        }
        if (topIds.isNotEmpty()) {
            val views = topAppsViews(context, today?.apps?.take(3)?.map { it.packageName to it.timeMs }, hasAccess)
            topIds.forEach { manager.updateAppWidget(it, views) }
        }
        if (goalIds.isNotEmpty()) {
            val views = goalsViews(context, prefs, today?.totalMs ?: 0L)
            goalIds.forEach { manager.updateAppWidget(it, views) }
        }
    }

    // ---------- Экранное время ----------

    private fun screenTimeViews(
        context: Context,
        prefs: Prefs,
        usage: UsageRepository,
        totalMs: Long?,
        hasAccess: Boolean,
    ): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_screen_time)
        v.setOnClickPendingIntent(R.id.root, openApp(context, MainActivity.TAB_TODAY))
        if (!hasAccess || totalMs == null) {
            v.setTextViewText(R.id.value, "—")
            v.setTextViewText(R.id.sub, "Открой Росток и дай доступ")
            return v
        }
        val goalMs = prefs.dailyGoalMinutes * TimeUtils.MINUTE
        val left = goalMs - totalMs
        v.setTextViewText(R.id.value, compact(totalMs))
        v.setTextViewText(
            R.id.sub,
            if (left >= 0) "ещё ${compact(left)} до цели" else "сверх цели ${compact(-left)}",
        )
        v.setImageViewBitmap(R.id.ring, ring(totalMs.toFloat() / goalMs))
        v.setTextViewText(R.id.emoji, Rewards(prefs, usage).plantStage().emoji)
        return v
    }

    // ---------- Топ приложений ----------

    private val rows = listOf(
        intArrayOf(R.id.row1, R.id.icon1, R.id.name1, R.id.time1),
        intArrayOf(R.id.row2, R.id.icon2, R.id.name2, R.id.time2),
        intArrayOf(R.id.row3, R.id.icon3, R.id.name3, R.id.time3),
    )

    private fun topAppsViews(context: Context, apps: List<Pair<String, Long>>?, hasAccess: Boolean): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_top_apps)
        v.setOnClickPendingIntent(R.id.root, openApp(context, MainActivity.TAB_TODAY))
        val list = apps.orEmpty()
        if (!hasAccess || list.isEmpty()) {
            v.setTextViewText(R.id.empty, if (hasAccess) "Сегодня ещё ничего не открывал 🌿" else "Открой Росток и дай доступ")
            v.setViewVisibility(R.id.empty, View.VISIBLE)
        }
        val catalog = AppCatalog(context)
        rows.forEachIndexed { i, (row, icon, name, time) ->
            val app = list.getOrNull(i)
            if (app == null) {
                // INVISIBLE, а не GONE: строки сохраняют высоту, и виджет не «прыгает».
                v.setViewVisibility(row, View.INVISIBLE)
                return@forEachIndexed
            }
            v.setViewVisibility(row, View.VISIBLE)
            v.setTextViewText(name, catalog.label(app.first))
            v.setTextViewText(time, compact(app.second))
            catalog.icon(app.first)?.let { d ->
                runCatching { d.toBitmap(72, 72) }.getOrNull()?.let { v.setImageViewBitmap(icon, it) }
            }
        }
        return v
    }

    // ---------- Цели ----------

    private fun goalsViews(context: Context, prefs: Prefs, totalMs: Long): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_goals)
        val repo = PrepRepository(context)
        val next = repo.load()?.nearest()

        if (next != null) {
            v.setOnClickPendingIntent(R.id.root, openApp(context, MainActivity.TAB_PREP))
            val days = Prep.daysUntil(next.date)
            val done = repo.sessions().filter { it.itemId == next.id }.sumOf { it.minutes }
            val target = next.targetMinutes.coerceAtLeast(1)
            v.setTextViewText(R.id.title, "📚 ${next.kind.title}")
            v.setTextViewText(R.id.value, if (days == 0) "Сегодня" else "$days дн.")
            v.setTextViewText(R.id.name, next.name)
            v.setProgressBar(R.id.progress, 100, (done * 100 / target).coerceIn(0, 100), false)
            v.setTextViewText(R.id.caption, "${hours(done)} из ${hours(target)}")
        } else {
            // Нет подготовки — показываем дневную цель, серию и очки.
            v.setOnClickPendingIntent(R.id.root, openApp(context, MainActivity.TAB_TODAY))
            val goalMs = prefs.dailyGoalMinutes * TimeUtils.MINUTE
            v.setTextViewText(R.id.title, "🎯 Цель дня")
            v.setTextViewText(R.id.value, "🔥 ${prefs.streak}")
            v.setTextViewText(R.id.name, "${daysWord(prefs.streak)} подряд · 🌱 ${prefs.points}")
            v.setProgressBar(R.id.progress, 100, (totalMs * 100 / goalMs).toInt().coerceIn(0, 100), false)
            v.setTextViewText(R.id.caption, "${compact(totalMs)} из ${compact(goalMs)}")
        }
        return v
    }

    // ---------- Вспомогательное ----------

    private fun openApp(context: Context, tab: Int): PendingIntent = PendingIntent.getActivity(
        context,
        tab,
        MainActivity.tabIntent(context, tab),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Кольцо прогресса к дневной цели: белое, а сверх цели — мягко-красное. */
    private fun ring(fraction: Float): Bitmap {
        val size = 192
        val stroke = 18f
        val bmp = createBitmap(size, size)
        val canvas = Canvas(bmp)
        val rect = RectF(stroke / 2, stroke / 2, size - stroke / 2, size - stroke / 2)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
        }
        paint.color = 0x59FFFFFF
        canvas.drawArc(rect, 0f, 360f, false, paint)
        paint.color = if (fraction >= 1f) 0xFFFF8A80.toInt() else 0xF2FFFFFF.toInt()
        canvas.drawArc(rect, -90f, 360f * fraction.coerceIn(0.01f, 1f), false, paint)
        return bmp
    }

    /** "3ч 04м", "45м". */
    private fun compact(ms: Long): String {
        val min = ms / TimeUtils.MINUTE
        return if (min >= 60) "${min / 60}ч ${"%02d".format(min % 60)}м" else "${min}м"
    }

    private fun hours(minutes: Int): String =
        if (minutes >= 60) "${minutes / 60} ч" + (if (minutes % 60 > 0) " ${minutes % 60} мин" else "") else "$minutes мин"

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
}
