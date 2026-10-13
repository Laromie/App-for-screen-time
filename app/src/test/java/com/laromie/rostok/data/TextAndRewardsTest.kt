package com.laromie.rostok.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAndRewardsTest {

    @Test
    fun `formats durations`() {
        assertEquals("0 мин", TimeUtils.formatDuration(0))
        assertEquals("45 мин", TimeUtils.formatDuration(45 * TimeUtils.MINUTE))
        assertEquals("2 ч 05 мин", TimeUtils.formatDuration(125 * TimeUtils.MINUTE))
        assertEquals("1ч 05м", SummaryText.compact(65))
        assertEquals("9м", SummaryText.compact(9))
    }

    @Test
    fun `day points grow with saved time and streak`() {
        assertEquals(Rewards.DAY_BASE + 6 + 2, Rewards.dayPoints(minutes = 60, goal = 120, streak = 1))
        // Бонус за серию ограничен 10 днями
        assertEquals(Rewards.dayPoints(60, 120, 10), Rewards.dayPoints(60, 120, 30))
        // Ровно по цели — без бонуса за экономию
        assertEquals(Rewards.DAY_BASE + 2, Rewards.dayPoints(120, 120, 1))
    }

    @Test
    fun `evening summary compares with yesterday and promises the streak`() {
        val (title, text) = SummaryText.evening(
            totalMinutes = 130, yesterdayMinutes = 170, goalUsedMinutes = 100,
            goalMinutes = 120, streak = 3, studyMinutes = 90,
        )
        assertEquals("Итог дня: 2ч 10м в телефоне", title)
        assertTrue(text, text.startsWith("На 40м меньше, чем вчера"))
        assertTrue(text, "серия станет 4" in text)
        assertTrue(text, "учёба 1ч 30м" in text)
    }

    @Test
    fun `evening summary warns when goal is exceeded`() {
        val (_, text) = SummaryText.evening(
            totalMinutes = 200, yesterdayMinutes = null, goalUsedMinutes = 150,
            goalMinutes = 120, streak = 5, studyMinutes = 0,
        )
        assertEquals("Цель превышена на 30м — серия 5 дн. под угрозой", text)
    }
}
