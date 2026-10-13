package com.laromie.rostok.data

import com.laromie.rostok.data.UsageCalculator.Event
import com.laromie.rostok.data.UsageCalculator.Type
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageCalculatorTest {

    private val min = TimeUtils.MINUTE
    private val start = 0L
    private val end = 24 * 60 * min

    @Test
    fun `counts time between foreground and background`() {
        val day = UsageCalculator.compute(
            listOf(
                Event(Type.FOREGROUND, 10 * min, "insta"),
                Event(Type.BACKGROUND, 25 * min, "insta"),
            ),
            start, end,
        )
        assertEquals(15 * min, day.timeFor("insta"))
        assertEquals(1, day.opensFor("insta"))
        assertEquals(15 * min, day.totalMs)
    }

    @Test
    fun `switching apps closes the previous one`() {
        val day = UsageCalculator.compute(
            listOf(
                Event(Type.FOREGROUND, 0, "a"),
                Event(Type.FOREGROUND, 5 * min, "b"),
                Event(Type.BACKGROUND, 8 * min, "b"),
            ),
            start, end,
        )
        assertEquals(5 * min, day.timeFor("a"))
        assertEquals(3 * min, day.timeFor("b"))
    }

    @Test
    fun `app opened before the interval is counted from the interval start`() {
        val day = UsageCalculator.compute(
            listOf(Event(Type.BACKGROUND, 7 * min, "tiktok")),
            start, end,
        )
        assertEquals(7 * min, day.timeFor("tiktok"))
    }

    @Test
    fun `app still open at the end is counted until the end`() {
        val day = UsageCalculator.compute(
            listOf(Event(Type.FOREGROUND, end - 3 * min, "yt")),
            start, end,
        )
        assertEquals(3 * min, day.timeFor("yt"))
    }

    @Test
    fun `screen off stops the timer`() {
        val day = UsageCalculator.compute(
            listOf(
                Event(Type.FOREGROUND, 0, "yt"),
                Event(Type.SCREEN_OFF, 4 * min),
                Event(Type.BACKGROUND, 60 * min, "yt"),
            ),
            start, end,
        )
        assertEquals(4 * min, day.timeFor("yt"))
    }

    @Test
    fun `quick bounce inside one app is not a new open`() {
        val day = UsageCalculator.compute(
            listOf(
                Event(Type.FOREGROUND, 0, "vk"),
                Event(Type.BACKGROUND, min, "vk"),
                Event(Type.FOREGROUND, min + 500, "vk"),
                Event(Type.BACKGROUND, 2 * min, "vk"),
                // Реальное повторное открытие через минуту
                Event(Type.FOREGROUND, 3 * min, "vk"),
                Event(Type.BACKGROUND, 4 * min, "vk"),
            ),
            start, end,
        )
        assertEquals(2, day.opensFor("vk"))
    }

    @Test
    fun `unlocks are counted and ignored apps are dropped`() {
        val day = UsageCalculator.compute(
            listOf(
                Event(Type.UNLOCK, 0),
                Event(Type.FOREGROUND, 0, "launcher"),
                Event(Type.FOREGROUND, 2 * min, "insta"),
                Event(Type.BACKGROUND, 3 * min, "insta"),
                Event(Type.UNLOCK, 10 * min),
            ),
            start, end, ignored = setOf("launcher"),
        )
        assertEquals(2, day.pickups)
        assertEquals(0L, day.timeFor("launcher"))
        assertEquals(min, day.totalMs)
    }

    @Test
    fun `timeForApps sums only the given apps`() {
        val day = UsageCalculator.compute(
            listOf(
                Event(Type.FOREGROUND, 0, "maps"),
                Event(Type.FOREGROUND, 30 * min, "insta"),
                Event(Type.BACKGROUND, 40 * min, "insta"),
            ),
            start, end,
        )
        assertEquals(10 * min, day.timeForApps(setOf("insta")))
        assertEquals(40 * min, day.totalMs)
    }
}
