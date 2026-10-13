package com.laromie.rostok.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class PrepTest {

    @Before
    fun setUp() = TestTime.init()

    // 13 октября 2026 — вторник
    private val tuesday = TestTime.at(2026, 10, 13)

    private fun prep(vararg items: PrepItem, days: Int = Prep.ALL_DAYS, start: Int = 16 * 60, end: Int = 20 * 60) =
        Prep(
            title = "Тест",
            template = PrepTemplate.BLANK,
            items = items.toList(),
            studyDays = days,
            studyStart = start,
            studyEnd = end,
        )

    private fun item(date: Long, passed: Boolean = false, kind: ItemKind = ItemKind.EXAM) =
        PrepItem(name = "Матан", kind = kind, date = date, targetMinutes = 600, passed = passed)

    @Test
    fun `study time respects window and weekdays`() {
        val p = prep(item(tuesday), days = Prep.WEEKDAYS)
        assertTrue(p.isStudyTime(TestTime.at(2026, 10, 13, 17)))
        assertFalse(p.isStudyTime(TestTime.at(2026, 10, 13, 20)))
        assertFalse(p.isStudyTime(TestTime.at(2026, 10, 13, 15, 59)))
        // 17 октября — суббота, её нет среди учебных дней
        assertFalse(p.isStudyTime(TestTime.at(2026, 10, 17, 17)))
    }

    @Test
    fun `study window can cross midnight`() {
        val p = prep(item(tuesday), start = 22 * 60, end = 2 * 60)
        assertTrue(p.isStudyTime(TestTime.at(2026, 10, 13, 23)))
        assertTrue(p.isStudyTime(TestTime.at(2026, 10, 13, 1)))
        assertFalse(p.isStudyTime(TestTime.at(2026, 10, 13, 12)))
    }

    @Test
    fun `sleep mode works the night before and the morning of an exam`() {
        val exam = TestTime.at(2026, 10, 14)
        val p = prep(item(exam)) // отбой 23:00, подъём 7:00 по умолчанию
        assertTrue(p.isSleepTime(TestTime.at(2026, 10, 13, 23, 30)))
        assertTrue(p.isSleepTime(TestTime.at(2026, 10, 14, 6)))
        assertFalse(p.isSleepTime(TestTime.at(2026, 10, 13, 22)))
        assertFalse(p.isSleepTime(TestTime.at(2026, 10, 14, 8)))
    }

    @Test
    fun `no sleep mode before a deadline or a passed exam`() {
        val tomorrow = TestTime.at(2026, 10, 14)
        val late = TestTime.at(2026, 10, 13, 23, 30)
        assertFalse(prep(item(tomorrow, kind = ItemKind.DEADLINE)).isSleepTime(late))
        assertFalse(prep(item(tomorrow, passed = true)).isSleepTime(late))
    }

    @Test
    fun `nearest skips passed and past items`() {
        val past = item(TestTime.at(2026, 10, 10))
        val passed = item(TestTime.at(2026, 10, 15), passed = true)
        val next = item(TestTime.at(2026, 10, 20))
        val later = item(TestTime.at(2026, 11, 1))
        val p = prep(later, past, passed, next)
        assertEquals(next.id, p.nearest(tuesday)?.id)
    }

    @Test
    fun `days until and when text`() {
        assertEquals(0, Prep.daysUntil(tuesday, tuesday))
        assertEquals(1, Prep.daysUntil(TestTime.at(2026, 10, 14), tuesday))
        assertEquals(30, Prep.daysUntil(TestTime.at(2026, 11, 12), tuesday))
        assertEquals("сегодня", Prep.whenText(0))
        assertEquals("завтра", Prep.whenText(1))
        assertEquals("через 6 дн.", Prep.whenText(6))
    }

    @Test
    fun `EGE template suggests next June 1st`() {
        val p = Prep.fromTemplate(PrepTemplate.EGE, tuesday)
        val date = Calendar.getInstance().apply { timeInMillis = p.items.first().date }
        assertEquals(2027, date.get(Calendar.YEAR))
        assertEquals(Calendar.JUNE, date.get(Calendar.MONTH))
        assertEquals(1, date.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `finished only when every item is passed`() {
        assertFalse(prep(item(tuesday), item(tuesday, passed = true)).isFinished)
        assertTrue(prep(item(tuesday, passed = true)).isFinished)
    }
}
