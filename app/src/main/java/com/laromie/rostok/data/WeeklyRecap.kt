package com.laromie.rostok.data

/** Итоги последних 7 завершённых дней. */
data class WeeklyRecap(
    val firstDay: Long,
    val lastDay: Long,
    /** Минуты экрана по дням, от старых к новым. */
    val dailyMinutes: List<Pair<Long, Int>>,
    val totalMinutes: Int,
    val avgMinutes: Int,
    /** Среднее за предыдущую неделю, если есть данные. */
    val prevAvgMinutes: Int?,
    val bestDay: Pair<Long, Int>?,
    val daysWithinGoal: Int,
    val daysWithData: Int,
    val pickups: Int,
    /** Топ-3 приложения: пакет → минуты. */
    val topApps: List<Pair<String, Int>>,
    val studyMinutes: Int,
    val streak: Int,
) {
    /** Разница среднего за день с прошлой неделей (минус — стало меньше). */
    val avgDiff: Int? get() = prevAvgMinutes?.let { avgMinutes - it }

    /** Сколько полнометражных фильмов (~2 часа) можно было посмотреть за это время. */
    val movies: Int get() = totalMinutes / 120

    companion object {
        /**
         * @param days последние 7 завершённых дней, от старых к новым
         * @param history минуты экрана по прошлым дням (из сохранённой истории)
         * @param goalUsage сколько минут дня идёт в зачёт цели
         */
        fun build(
            days: List<DayUsage>,
            history: Map<Long, Int>,
            goalUsage: (DayUsage) -> Long,
            goalMinutes: Int,
            studyMinutes: Int,
            streak: Int,
        ): WeeklyRecap {
            val daily = days.map { it.dayStart to (it.totalMs / TimeUtils.MINUTE).toInt() }
            val withData = days.filter { it.totalMs > 0 }
            val total = daily.sumOf { it.second }
            val avg = if (withData.isEmpty()) 0 else total / withData.size

            val firstDay = days.firstOrNull()?.dayStart ?: 0L
            // Прошлая неделя — 7 дней перед первым днём этой; нужно хотя бы 3 дня данных.
            val prevStart = firstDay - 7 * TimeUtils.DAY - TimeUtils.HOUR // запас на переход часов
            val prev = history.filter { (day, min) -> day in prevStart until firstDay && min > 0 }.values
            val prevAvg = if (prev.size >= 3) prev.sum() / prev.size else null

            val top = days.flatMap { it.apps }
                .groupBy { it.packageName }
                .mapValues { (_, list) -> (list.sumOf { it.timeMs } / TimeUtils.MINUTE).toInt() }
                .entries.sortedByDescending { it.value }
                .take(3)
                .map { it.key to it.value }

            return WeeklyRecap(
                firstDay = firstDay,
                lastDay = days.lastOrNull()?.dayStart ?: 0L,
                dailyMinutes = daily,
                totalMinutes = total,
                avgMinutes = avg,
                prevAvgMinutes = prevAvg,
                bestDay = withData.minByOrNull { it.totalMs }?.let { it.dayStart to (it.totalMs / TimeUtils.MINUTE).toInt() },
                daysWithinGoal = withData.count { goalUsage(it) / TimeUtils.MINUTE <= goalMinutes },
                daysWithData = withData.size,
                pickups = days.sumOf { it.pickups },
                topApps = top,
                studyMinutes = studyMinutes,
                streak = streak,
            )
        }
    }
}
