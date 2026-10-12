package com.laromie.rostok.data

import java.util.Calendar

object TimeUtils {
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE
    const val DAY = 24 * HOUR

    fun startOfDay(time: Long): Long = Calendar.getInstance().run {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    fun startOfToday(): Long = startOfDay(System.currentTimeMillis())

    /** Начало следующего дня (корректно при переходе на летнее/зимнее время). */
    fun nextDay(dayStart: Long): Long = Calendar.getInstance().run {
        timeInMillis = dayStart
        add(Calendar.DAY_OF_YEAR, 1)
        timeInMillis
    }

    fun previousDay(dayStart: Long): Long = Calendar.getInstance().run {
        timeInMillis = dayStart
        add(Calendar.DAY_OF_YEAR, -1)
        timeInMillis
    }

    /** "1 ч 05 мин", "12 мин", "0 мин". */
    fun formatDuration(ms: Long): String {
        val totalMin = ms / MINUTE
        val h = totalMin / 60
        val m = totalMin % 60
        return when {
            h > 0 -> "$h ч ${"%02d".format(m)} мин"
            else -> "$m мин"
        }
    }

    fun formatMinutes(minutes: Int): String = formatDuration(minutes * MINUTE)

    private val dayNames = arrayOf("Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб")

    fun shortDayName(dayStart: Long): String = Calendar.getInstance().run {
        timeInMillis = dayStart
        dayNames[get(Calendar.DAY_OF_WEEK) - 1]
    }
}
