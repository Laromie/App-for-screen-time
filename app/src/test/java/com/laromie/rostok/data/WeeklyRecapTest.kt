package com.laromie.rostok.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class WeeklyRecapTest {

    @Before
    fun setUp() = TestTime.init()

    private val min = TimeUtils.MINUTE

    private fun day(date: Long, vararg apps: Pair<String, Int>) = DayUsage(
        dayStart = date,
        totalMs = apps.sumOf { it.second } * min,
        pickups = 10,
        apps = apps.map { AppUsage(it.first, it.second * min, 1) },
    )

    private val days = (5..11).map { d ->
        day(TestTime.at(2026, 10, d), "insta" to 60, "yt" to d * 5, "maps" to 10)
    }

    @Test
    fun `totals averages and top apps`() {
        val recap = WeeklyRecap.build(days, emptyMap(), { it.totalMs }, 120, studyMinutes = 300, streak = 2)
        val total = days.sumOf { it.totalMs / min }.toInt()
        assertEquals(total, recap.totalMinutes)
        assertEquals(total / 7, recap.avgMinutes)
        assertEquals(listOf("insta", "yt", "maps"), recap.topApps.map { it.first })
        assertEquals(70, recap.pickups)
        assertEquals(TestTime.at(2026, 10, 5), recap.bestDay?.first)
        assertNull(recap.prevAvgMinutes)
    }

    @Test
    fun `days within goal use the goal usage function`() {
        // Цель 90 минут: всего в каждом дне больше (95–125), но «только insta» (60) укладывается.
        val all = WeeklyRecap.build(days, emptyMap(), { it.totalMs }, 90, 0, 0)
        val trackedOnly = WeeklyRecap.build(days, emptyMap(), { it.timeForApps(setOf("insta")) }, 90, 0, 0)
        assertEquals(0, all.daysWithinGoal)
        assertEquals(7, trackedOnly.daysWithinGoal)
    }

    @Test
    fun `compares with the previous week from history`() {
        val history = (28..30).associate { TestTime.at(2026, 9, it) to 200 } +
            (1..4).associate { TestTime.at(2026, 10, it) to 200 }
        val recap = WeeklyRecap.build(days, history, { it.totalMs }, 120, 0, 0)
        assertEquals(200, recap.prevAvgMinutes)
        assertEquals(recap.avgMinutes - 200, recap.avgDiff)
    }
}
