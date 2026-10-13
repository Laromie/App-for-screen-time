package com.laromie.rostok.data

/**
 * Чистый (без Android) подсчёт экранного времени по последовательности событий —
 * отделён от UsageRepository, чтобы его можно было проверять обычными тестами.
 */
object UsageCalculator {

    enum class Type { FOREGROUND, BACKGROUND, SCREEN_OFF, UNLOCK }

    data class Event(val type: Type, val time: Long, val pkg: String = "")

    /** Переход между экранами одного приложения быстрее этого — не новое открытие. */
    const val BOUNCE_MS = 2_000L

    /** Приложения, открытые меньше секунды, не показываем. */
    private const val MIN_VISIBLE_MS = 1_000L

    fun compute(events: List<Event>, start: Long, end: Long, ignored: Set<String> = emptySet()): DayUsage {
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

        for (e in events) {
            when (e.type) {
                Type.FOREGROUND -> {
                    val cur = current
                    if (cur != null && cur != e.pkg) add(cur, e.time - currentStart)
                    if (cur != e.pkg) {
                        val bounce = lastBgPkg == e.pkg && e.time - lastBgTime < BOUNCE_MS
                        if (!bounce) opens[e.pkg] = (opens[e.pkg] ?: 0) + 1
                        current = e.pkg
                        currentStart = e.time
                    }
                    seenAny = true
                }

                Type.BACKGROUND -> {
                    if (current == e.pkg) {
                        add(e.pkg, e.time - currentStart)
                        current = null
                    } else if (!seenAny) {
                        // Приложение было открыто ещё до начала интервала.
                        add(e.pkg, e.time - start)
                    }
                    lastBgPkg = e.pkg
                    lastBgTime = e.time
                    seenAny = true
                }

                Type.SCREEN_OFF -> {
                    current?.let { add(it, e.time - currentStart) }
                    current = null
                }

                Type.UNLOCK -> pickups++
            }
        }
        current?.let { add(it, end - currentStart) }

        val apps = time.keys
            .filter { it !in ignored }
            .map { AppUsage(it, time[it] ?: 0L, opens[it] ?: 0) }
            .filter { it.timeMs >= MIN_VISIBLE_MS }
            .sortedByDescending { it.timeMs }

        return DayUsage(start, apps.sumOf { it.timeMs }, pickups, apps)
    }
}
