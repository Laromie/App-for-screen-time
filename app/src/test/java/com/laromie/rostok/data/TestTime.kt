package com.laromie.rostok.data

import java.util.Calendar
import java.util.TimeZone

/** Фиксированный часовой пояс и удобное построение дат для тестов. */
object TestTime {
    fun init() = TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))

    fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        Calendar.getInstance().run {
            clear()
            set(year, month - 1, day, hour, minute)
            timeInMillis
        }
}
