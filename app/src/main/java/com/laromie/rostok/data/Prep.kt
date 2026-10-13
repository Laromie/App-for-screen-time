package com.laromie.rostok.data

import java.util.Calendar
import java.util.UUID
import kotlin.math.roundToInt

/** Готовые наборы рамок для быстрого старта. Любую рамку потом можно поменять. */
enum class PrepTemplate(val emoji: String, val title: String, val description: String) {
    EGE("🎓", "ЕГЭ", "11 класс"),
    OGE("📘", "ОГЭ", "9 класс"),
    SESSION("📚", "Сессия", "Экзамены и зачёты в вузе"),
    COURSEWORK("📝", "Курсовая / диплом", "Один большой дедлайн"),
    CUSTOM_EXAM("🌍", "Свой экзамен", "IELTS, права, олимпиада…"),
    BLANK("✏️", "С нуля", "Всё настрою сам"),
}

enum class ItemKind(val title: String) {
    EXAM("Экзамен"),
    CREDIT("Зачёт"),
    DEADLINE("Дедлайн"),
}

/** Насколько строго ограничивать отвлекающие приложения в учебные часы. */
enum class Strictness(val emoji: String, val title: String, val description: String) {
    SOFT("🟢", "Мягко", "Пауза с напоминанием об экзамене перед каждой соцсетью"),
    SERIOUS("🟡", "Серьёзно", "Пауза 30 секунд, открыть можно только на 2 минуты"),
    HARD("🔴", "Жёстко", "Соцсети заблокированы. Выход — только через аварийную кнопку"),
}

/** Один экзамен, зачёт или дедлайн внутри подготовки. */
data class PrepItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val kind: ItemKind = ItemKind.EXAM,
    /** Начало дня экзамена (epoch ms). */
    val date: Long,
    val targetMinutes: Int,
    val passed: Boolean = false,
)

data class Prep(
    val title: String,
    val template: PrepTemplate,
    val items: List<PrepItem>,
    val strictness: Strictness = Strictness.SERIOUS,
    /** Учебные дни: бит 0 — понедельник … бит 6 — воскресенье. */
    val studyDays: Int = ALL_DAYS,
    /** Учебные часы в минутах от полуночи. */
    val studyStart: Int = 16 * 60,
    val studyEnd: Int = 20 * 60,
    /** Приложения, которые в учебные часы не ограничиваются (например, чат группы). */
    val exceptions: Set<String> = emptySet(),
    val sleepEnabled: Boolean = true,
    val bedtime: Int = 23 * 60,
    val wakeTime: Int = 7 * 60,
) {
    fun upcoming(today: Long = TimeUtils.startOfToday()): List<PrepItem> =
        items.filter { !it.passed && it.date >= today }.sortedBy { it.date }

    fun nearest(today: Long = TimeUtils.startOfToday()): PrepItem? = upcoming(today).firstOrNull()

    fun isStudyDay(time: Long): Boolean = studyDays and (1 shl dayIndex(time)) != 0

    fun isStudyTime(now: Long = System.currentTimeMillis()): Boolean {
        if (!isStudyDay(now)) return false
        val t = minuteOfDay(now)
        return if (studyStart <= studyEnd) t in studyStart until studyEnd
        else t >= studyStart || t < studyEnd // окно через полночь
    }

    /** Ночь перед экзаменом: после отбоя накануне и до подъёма в день экзамена. */
    fun isSleepTime(now: Long = System.currentTimeMillis()): Boolean {
        if (!sleepEnabled) return false
        val today = TimeUtils.startOfDay(now)
        val t = minuteOfDay(now)
        return (t >= bedtime && hasExamOn(TimeUtils.nextDay(today))) ||
            (t < wakeTime && hasExamOn(today))
    }

    private fun hasExamOn(dayStart: Long) =
        items.any { !it.passed && it.kind != ItemKind.DEADLINE && it.date == dayStart }

    val isFinished: Boolean get() = items.isNotEmpty() && items.all { it.passed }

    companion object {
        const val ALL_DAYS = 0b1111111
        const val WEEKDAYS = 0b0011111

        /** 0 — понедельник … 6 — воскресенье. */
        fun dayIndex(time: Long): Int = Calendar.getInstance().run {
            timeInMillis = time
            (get(Calendar.DAY_OF_WEEK) + 5) % 7
        }

        fun minuteOfDay(time: Long): Int = Calendar.getInstance().run {
            timeInMillis = time
            get(Calendar.HOUR_OF_DAY) * 60 + get(Calendar.MINUTE)
        }

        /** Сколько дней от сегодня до [dayStart]: 0 — сегодня, 1 — завтра. */
        fun daysUntil(dayStart: Long, today: Long = TimeUtils.startOfToday()): Int =
            ((dayStart - today).toDouble() / TimeUtils.DAY).roundToInt()

        /** "сегодня", "завтра", "через 5 дн.", "3 дн. назад". */
        fun whenText(days: Int): String = when {
            days == 0 -> "сегодня"
            days == 1 -> "завтра"
            days > 1 -> "через $days дн."
            else -> "${-days} дн. назад"
        }

        fun formatClock(minutes: Int): String = "%d:%02d".format(minutes / 60, minutes % 60)

        val DAY_SHORT = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

        /** Рамки по умолчанию для шаблона. Даты — только подсказка, их нужно проверить. */
        fun fromTemplate(template: PrepTemplate, today: Long = TimeUtils.startOfToday()): Prep {
            val june1 = nextDate(today, Calendar.JUNE, 1)
            return when (template) {
                PrepTemplate.EGE -> Prep(
                    title = "ЕГЭ",
                    template = template,
                    items = listOf(
                        PrepItem(name = "Русский язык", date = june1, targetMinutes = 60 * 60),
                        PrepItem(name = "Математика (профиль)", date = june1, targetMinutes = 80 * 60),
                    ),
                    strictness = Strictness.SERIOUS,
                    studyStart = 16 * 60, studyEnd = 20 * 60,
                )

                PrepTemplate.OGE -> Prep(
                    title = "ОГЭ",
                    template = template,
                    items = listOf(
                        PrepItem(name = "Русский язык", date = june1, targetMinutes = 40 * 60),
                        PrepItem(name = "Математика", date = june1, targetMinutes = 40 * 60),
                    ),
                    strictness = Strictness.SOFT,
                    studyStart = 16 * 60, studyEnd = 19 * 60,
                )

                PrepTemplate.SESSION -> {
                    val month = Calendar.getInstance().apply { timeInMillis = today }.get(Calendar.MONTH)
                    val winter = month >= Calendar.SEPTEMBER || month <= Calendar.JANUARY
                    Prep(
                        title = if (winter) "Зимняя сессия" else "Летняя сессия",
                        template = template,
                        items = listOf(
                            PrepItem(name = "Зачёт", kind = ItemKind.CREDIT, date = plusDays(today, 21), targetMinutes = 4 * 60),
                            PrepItem(name = "Экзамен", kind = ItemKind.EXAM, date = plusDays(today, 28), targetMinutes = 15 * 60),
                        ),
                        strictness = Strictness.SERIOUS,
                        studyStart = 11 * 60, studyEnd = 19 * 60,
                    )
                }

                PrepTemplate.COURSEWORK -> Prep(
                    title = "Курсовая",
                    template = template,
                    items = listOf(
                        PrepItem(name = "Сдача курсовой", kind = ItemKind.DEADLINE, date = plusDays(today, 30), targetMinutes = 30 * 60),
                    ),
                    strictness = Strictness.SERIOUS,
                    studyDays = WEEKDAYS,
                    studyStart = 12 * 60, studyEnd = 18 * 60,
                    sleepEnabled = false,
                )

                PrepTemplate.CUSTOM_EXAM -> Prep(
                    title = "Мой экзамен",
                    template = template,
                    items = listOf(PrepItem(name = "Экзамен", date = plusDays(today, 30), targetMinutes = 20 * 60)),
                    strictness = Strictness.SERIOUS,
                )

                PrepTemplate.BLANK -> Prep(
                    title = "Подготовка",
                    template = template,
                    items = listOf(PrepItem(name = "Экзамен", date = plusDays(today, 14), targetMinutes = 10 * 60)),
                    strictness = Strictness.SOFT,
                )
            }
        }

        fun plusDays(dayStart: Long, days: Int): Long = Calendar.getInstance().run {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, days)
            timeInMillis
        }

        /** Ближайшая будущая дата с указанными месяцем и днём. */
        private fun nextDate(today: Long, month: Int, day: Int): Long = Calendar.getInstance().run {
            timeInMillis = today
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            if (timeInMillis < today) add(Calendar.YEAR, 1)
            TimeUtils.startOfDay(timeInMillis)
        }
    }
}

/** Одно завершённое (или прерванное) занятие. */
data class StudySession(val itemId: String, val start: Long, val minutes: Int)
