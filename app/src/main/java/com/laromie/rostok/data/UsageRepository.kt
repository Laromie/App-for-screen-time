package com.laromie.rostok.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process

data class AppUsage(
    val packageName: String,
    val timeMs: Long,
    val opens: Int,
)

data class DayUsage(
    val dayStart: Long,
    val totalMs: Long,
    val pickups: Int,
    val apps: List<AppUsage>,
) {
    fun timeFor(pkg: String): Long = apps.firstOrNull { it.packageName == pkg }?.timeMs ?: 0L
    fun opensFor(pkg: String): Int = apps.firstOrNull { it.packageName == pkg }?.opens ?: 0
}

/**
 * Считает экранное время по сырым событиям UsageEvents (как Digital Wellbeing):
 * время между выходом приложения на передний план и уходом с него.
 */
class UsageRepository(context: Context) {

    private val appContext = context.applicationContext
    private val usm = appContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    /** Пакеты, которые не считаем экранным временем: лаунчеры, системный UI, само приложение. */
    private val ignored: Set<String> by lazy {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val launchers = appContext.packageManager
            .queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
        launchers.toSet() + setOf(appContext.packageName, "com.android.systemui", "android")
    }

    fun hasPermission(): Boolean {
        val appOps = appContext.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), appContext.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun today(): DayUsage = day(TimeUtils.startOfToday())

    fun day(dayStart: Long): DayUsage {
        val end = minOf(TimeUtils.nextDay(dayStart), System.currentTimeMillis())
        return range(dayStart, end)
    }

    /** Последние [count] дней, включая сегодня, от старых к новым. */
    fun lastDays(count: Int): List<DayUsage> {
        var day = TimeUtils.startOfToday()
        val starts = ArrayList<Long>(count)
        repeat(count) {
            starts.add(day)
            day = TimeUtils.previousDay(day)
        }
        return starts.reversed().map { day(it) }
    }

    fun range(start: Long, end: Long): DayUsage {
        if (end <= start || !hasPermission()) return DayUsage(start, 0, 0, emptyList())

        val time = HashMap<String, Long>()
        val opens = HashMap<String, Int>()
        var pickups = 0

        var current: String? = null
        var currentStart = start
        var seenAny = false
        var lastBgPkg: String? = null
        var lastBgTime = 0L

        fun add(pkg: String, ms: Long) {
            if (ms > 0) time[pkg] = (time[pkg] ?: 0L) + ms
        }

        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val pkg = e.packageName
            val ts = e.timeStamp
            when (e.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND -> { // == ACTIVITY_RESUMED
                    val cur = current
                    if (cur != null && cur != pkg) add(cur, ts - currentStart)
                    if (cur != pkg) {
                        // Переход между экранами одного приложения — не новое открытие.
                        val sameAppBounce = lastBgPkg == pkg && ts - lastBgTime < 2_000
                        if (!sameAppBounce) opens[pkg] = (opens[pkg] ?: 0) + 1
                        current = pkg
                        currentStart = ts
                    }
                    seenAny = true
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND -> { // == ACTIVITY_PAUSED
                    if (current == pkg) {
                        add(pkg, ts - currentStart)
                        current = null
                    } else if (!seenAny) {
                        // Приложение было открыто ещё до начала интервала.
                        add(pkg, ts - start)
                    }
                    lastBgPkg = pkg
                    lastBgTime = ts
                    seenAny = true
                }

                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    current?.let { add(it, ts - currentStart) }
                    current = null
                }

                UsageEvents.Event.KEYGUARD_HIDDEN -> pickups++
            }
        }
        current?.let { add(it, end - currentStart) }

        val apps = time.keys
            .filter { it !in ignored }
            .map { AppUsage(it, time[it] ?: 0L, opens[it] ?: 0) }
            .filter { it.timeMs >= 1_000 }
            .sortedByDescending { it.timeMs }

        return DayUsage(start, apps.sumOf { it.timeMs }, pickups, apps)
    }
}
