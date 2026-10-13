package com.laromie.rostok.data

/** Тексты итогов. Чистые функции — проверяются тестами. */
object SummaryText {

    /** "2ч 10м", "45м". */
    fun compact(minutes: Int): String =
        if (minutes >= 60) "${minutes / 60}ч ${"%02d".format(minutes % 60)}м" else "${minutes}м"

    /**
     * Вечерний итог дня.
     * @param goalMinutes сколько минут идёт в зачёт цели (всё время или только отвлекающие)
     * @param streak серия завершённых дней до сегодняшнего
     */
    fun evening(
        totalMinutes: Int,
        yesterdayMinutes: Int?,
        goalUsedMinutes: Int,
        goalMinutes: Int,
        streak: Int,
        studyMinutes: Int,
    ): Pair<String, String> {
        val title = "Итог дня: ${compact(totalMinutes)} в телефоне"
        val parts = mutableListOf<String>()

        if (yesterdayMinutes != null && yesterdayMinutes > 0) {
            val diff = totalMinutes - yesterdayMinutes
            parts += when {
                diff <= -5 -> "на ${compact(-diff)} меньше, чем вчера 👏"
                diff >= 5 -> "на ${compact(diff)} больше, чем вчера"
                else -> "примерно как вчера"
            }
        }

        parts += if (goalUsedMinutes <= goalMinutes) {
            "цель ✅ — если так и закончишь, серия станет ${streak + 1} 🔥"
        } else {
            "цель превышена на ${compact(goalUsedMinutes - goalMinutes)}" +
                if (streak > 0) " — серия $streak дн. под угрозой" else ""
        }

        if (studyMinutes > 0) parts += "📖 учёба ${compact(studyMinutes)}"

        return title to parts.joinToString(" · ").replaceFirstChar { it.uppercase() }
    }
}
