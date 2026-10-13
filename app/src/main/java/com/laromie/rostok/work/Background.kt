package com.laromie.rostok.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.laromie.rostok.R
import com.laromie.rostok.data.Prefs
import com.laromie.rostok.data.PrepRepository
import com.laromie.rostok.data.Rewards
import com.laromie.rostok.data.Study
import com.laromie.rostok.data.SummaryText
import com.laromie.rostok.data.TimeUtils
import com.laromie.rostok.data.UsageRepository
import com.laromie.rostok.ui.MainActivity
import com.laromie.rostok.ui.RecapActivity
import com.laromie.rostok.widget.Widgets
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Планирование фоновых задач. Вызывается при каждом запуске приложения — повторно ничего не дублирует. */
object Background {
    private const val DAILY_CHECK = "daily_check"
    private const val EVENING = "evening_summary"

    fun schedule(context: Context, eveningChanged: Boolean = false) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            DAILY_CHECK,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyCheckWorker>(6, TimeUnit.HOURS).build(),
        )
        val prefs = Prefs(context)
        val request = PeriodicWorkRequestBuilder<EveningWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(delayUntilHour(prefs.eveningHour), TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(
            EVENING,
            if (eveningChanged) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun delayUntilHour(hour: Int): Long {
        val now = System.currentTimeMillis()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis - now
    }
}

/** Подводит итоги прошедших дней и обновляет виджеты, даже если приложение не открывали. */
class DailyCheckWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = Prefs(applicationContext)
        val usage = UsageRepository(applicationContext)
        val rewards = Rewards(prefs, usage)
        rewards.evaluatePastDays()
        Study(prefs, rewards, PrepRepository(applicationContext)).checkCompletion()
        Widgets.updateAll(applicationContext)
        return Result.success()
    }
}

/** Вечерний итог дня, а по воскресеньям — приглашение посмотреть итоги недели. */
class EveningWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        if (!prefs.eveningSummary) return Result.success()
        val today = TimeUtils.startOfToday()
        // Периодические задачи могут «плавать» по времени — не больше одного итога в день.
        if (prefs.lastSummaryDay == today) return Result.success()

        val usage = UsageRepository(ctx)
        if (!usage.hasPermission()) return Result.success()
        val day = usage.today()
        val yesterday = usage.day(TimeUtils.previousDay(today))
        val prep = PrepRepository(ctx)
        val study = prep.sessions().filter { it.start >= today }.sumOf { it.minutes }

        val (title, text) = SummaryText.evening(
            totalMinutes = (day.totalMs / TimeUtils.MINUTE).toInt(),
            yesterdayMinutes = (yesterday.totalMs / TimeUtils.MINUTE).toInt().takeIf { it > 0 },
            goalUsedMinutes = (prefs.goalUsageMs(day) / TimeUtils.MINUTE).toInt(),
            goalMinutes = prefs.dailyGoalMinutes,
            streak = prefs.streak,
            studyMinutes = study,
        )
        val sunday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        if (sunday) {
            Notifications.show(
                ctx, Notifications.ID_EVENING, "📊 Итоги недели готовы",
                "$title. Посмотри, как прошла неделя, и поделись карточкой.",
                Intent(ctx, RecapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } else {
            Notifications.show(
                ctx, Notifications.ID_EVENING, title, text,
                MainActivity.tabIntent(ctx, MainActivity.TAB_TODAY),
            )
        }
        prefs.lastSummaryDay = today
        return Result.success()
    }
}

object Notifications {
    private const val CHANNEL_SUMMARY = "summary"
    const val ID_EVENING = 1

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(context: Context, id: Int, title: String, text: String, intent: Intent) {
        if (!canPost(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SUMMARY, "Итоги дня и недели", NotificationManager.IMPORTANCE_DEFAULT)
        )
        val pending = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SUMMARY)
            .setSmallIcon(R.drawable.ic_stat_sprout)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Разрешение отозвали между проверкой и показом — просто пропускаем.
        }
    }
}
