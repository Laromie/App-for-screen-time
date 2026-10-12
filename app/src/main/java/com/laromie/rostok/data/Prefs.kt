package com.laromie.rostok.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/** Все настройки и игровой прогресс. Хранятся локально в SharedPreferences. */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("rostok", Context.MODE_PRIVATE)

    // ---------- Отвлекающие приложения и лимиты ----------

    var trackedApps: Set<String>
        get() = sp.getStringSet(KEY_TRACKED, emptySet())!!.toSet()
        set(value) = sp.edit { putStringSet(KEY_TRACKED, value) }

    fun isTracked(pkg: String) = pkg in trackedApps

    fun setTracked(pkg: String, tracked: Boolean) {
        trackedApps = if (tracked) trackedApps + pkg else trackedApps - pkg
    }

    /** Дневной лимит приложения в минутах, 0 — без лимита (только пауза). */
    fun limitMinutes(pkg: String): Int = sp.getInt("limit_$pkg", 0)

    fun setLimitMinutes(pkg: String, minutes: Int) = sp.edit { putInt("limit_$pkg", minutes) }

    /** Бонусные минуты, купленные за очки на сегодня. */
    fun bonusMinutesToday(pkg: String): Int {
        val raw = sp.getString("bonus_$pkg", null) ?: return 0
        val (day, minutes) = raw.split(":").let { it[0].toLong() to it[1].toInt() }
        return if (day == TimeUtils.startOfToday()) minutes else 0
    }

    fun addBonusMinutesToday(pkg: String, minutes: Int) {
        val total = bonusMinutesToday(pkg) + minutes
        sp.edit { putString("bonus_$pkg", "${TimeUtils.startOfToday()}:$total") }
    }

    /** Время (epoch ms), до которого приложение разрешено после осознанной паузы. */
    fun unlockUntil(pkg: String): Long = sp.getLong("unlock_$pkg", 0L)

    fun setUnlockUntil(pkg: String, until: Long) = sp.edit { putLong("unlock_$pkg", until) }

    var dailyGoalMinutes: Int
        get() = sp.getInt("daily_goal", 120)
        set(value) = sp.edit { putInt("daily_goal", value) }

    var pauseSeconds: Int
        get() = sp.getInt("pause_seconds", 10)
        set(value) = sp.edit { putInt("pause_seconds", value) }

    var pauseEnabled: Boolean
        get() = sp.getBoolean("pause_enabled", true)
        set(value) = sp.edit { putBoolean("pause_enabled", value) }

    // ---------- Режим фокуса ----------

    var focusUntil: Long
        get() = sp.getLong("focus_until", 0L)
        set(value) = sp.edit { putLong("focus_until", value) }

    var focusStartedAt: Long
        get() = sp.getLong("focus_started", 0L)
        set(value) = sp.edit { putLong("focus_started", value) }

    fun isFocusActive(now: Long = System.currentTimeMillis()) = focusUntil > now

    // ---------- Награды ----------

    var points: Int
        get() = sp.getInt("points", 0)
        set(value) = sp.edit { putInt("points", value.coerceAtLeast(0)) }

    /** Всего заработано за всё время — определяет стадию растения, траты его не уменьшают. */
    var totalEarned: Int
        get() = sp.getInt("total_earned", 0)
        set(value) = sp.edit { putInt("total_earned", value) }

    var streak: Int
        get() = sp.getInt("streak", 0)
        set(value) = sp.edit { putInt("streak", value) }

    var bestStreak: Int
        get() = sp.getInt("best_streak", 0)
        set(value) = sp.edit { putInt("best_streak", value) }

    var goodDays: Int
        get() = sp.getInt("good_days", 0)
        set(value) = sp.edit { putInt("good_days", value) }

    /** Начало последнего дня, по которому уже подведены итоги. 0 — ещё не начинали. */
    var lastEvaluatedDay: Long
        get() = sp.getLong("last_evaluated_day", 0L)
        set(value) = sp.edit { putLong("last_evaluated_day", value) }

    var totalResists: Int
        get() = sp.getInt("total_resists", 0)
        set(value) = sp.edit { putInt("total_resists", value) }

    var focusSessionsDone: Int
        get() = sp.getInt("focus_done", 0)
        set(value) = sp.edit { putInt("focus_done", value) }

    var bestDayMinutes: Int
        get() = sp.getInt("best_day_minutes", Int.MAX_VALUE)
        set(value) = sp.edit { putInt("best_day_minutes", value) }

    /** Сколько отказов от открытия сегодня уже принесли очки (защита от «накрутки»). */
    fun rewardedResistsToday(): Int {
        val day = sp.getLong("resist_day", 0L)
        return if (day == TimeUtils.startOfToday()) sp.getInt("resist_count", 0) else 0
    }

    fun incRewardedResistsToday() {
        val count = rewardedResistsToday() + 1
        sp.edit {
            putLong("resist_day", TimeUtils.startOfToday())
            putInt("resist_count", count)
        }
    }

    var achievements: Set<String>
        get() = sp.getStringSet("achievements", emptySet())!!.toSet()
        set(value) = sp.edit { putStringSet("achievements", value) }

    /** Последние события начисления/списания очков: "ts|delta|reason" построчно, новые сверху. */
    var historyRaw: String
        get() = sp.getString("history", "")!!
        set(value) = sp.edit { putString("history", value) }

    var initialized: Boolean
        get() = sp.getBoolean("initialized", false)
        set(value) = sp.edit { putBoolean("initialized", value) }

    companion object {
        private const val KEY_TRACKED = "tracked_apps"
    }
}
