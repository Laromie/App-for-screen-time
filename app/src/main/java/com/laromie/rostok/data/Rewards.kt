package com.laromie.rostok.data

/** Стадия растения, которое растёт от заработанных очков. */
data class PlantStage(val minPoints: Int, val emoji: String, val name: String)

/** Кого растить за очки. Пороги очков у всех стилей одинаковые. */
enum class RewardStyle(val emoji: String, val title: String, private val stages: List<Pair<String, String>>) {
    PLANT(
        "🌱", "Растение", listOf(
            "🌰" to "Семечко", "🌱" to "Росток", "🌿" to "Побег", "🪴" to "Кустик",
            "🌳" to "Деревце", "🌲" to "Крепкое дерево", "🌸" to "Цветущее дерево", "🍎" to "Плодовый сад",
        )
    ),
    DRAGON(
        "🐉", "Дракон", listOf(
            "🥚" to "Загадочное яйцо", "🐣" to "Вылупился", "🦎" to "Ящерка", "🐊" to "Зубастик",
            "🐲" to "Дракончик", "🐉" to "Дракон", "🔥" to "Огненный дракон", "👑" to "Король драконов",
        )
    ),
    SPACE(
        "🚀", "Космос", listOf(
            "🪨" to "Камешек", "🛰️" to "Спутник", "🚀" to "Ракета", "🌙" to "Луна",
            "🌍" to "Планета", "🪐" to "Газовый гигант", "⭐" to "Звезда", "🌌" to "Галактика",
        )
    );

    fun stages(): List<PlantStage> = stages.mapIndexed { i, (emoji, name) ->
        PlantStage(Rewards.STAGE_POINTS[i], emoji, name)
    }
}

data class Achievement(
    val id: String,
    val emoji: String,
    val title: String,
    val description: String,
)

data class HistoryEntry(val time: Long, val delta: Int, val reason: String)

/**
 * Игровая механика: очки за дни в пределах цели, за отказ открыть соцсеть
 * и за завершённые фокус-сессии. Очки можно потратить на дополнительные минуты.
 */
class Rewards(private val prefs: Prefs, private val usage: UsageRepository) {

    /** Подводит итоги всех завершённых дней с момента последней проверки. */
    fun evaluatePastDays() {
        if (!usage.hasPermission()) return
        val today = TimeUtils.startOfToday()
        val yesterday = TimeUtils.previousDay(today)
        var last = prefs.lastEvaluatedDay
        if (last == 0L) {
            // Первый запуск: начинаем отсчёт с сегодняшнего дня.
            prefs.lastEvaluatedDay = yesterday
            return
        }
        // Не уходим дальше недели назад: старые события система может уже не хранить.
        val weekAgo = (1..7).fold(today) { d, _ -> TimeUtils.previousDay(d) }
        if (last < weekAgo) last = TimeUtils.previousDay(weekAgo)

        var day = TimeUtils.nextDay(last)
        while (day <= yesterday) {
            evaluateDay(day)
            prefs.lastEvaluatedDay = day
            day = TimeUtils.nextDay(day)
        }
    }

    private fun evaluateDay(dayStart: Long) {
        val result = usage.day(dayStart)
        val minutes = (result.totalMs / TimeUtils.MINUTE).toInt()
        val goal = prefs.dailyGoalMinutes
        val date = TimeUtils.shortDayName(dayStart)

        if (minutes < prefs.bestDayMinutes && result.totalMs > 0) prefs.bestDayMinutes = minutes

        if (minutes <= goal) {
            prefs.streak += 1
            prefs.goodDays += 1
            if (prefs.streak > prefs.bestStreak) prefs.bestStreak = prefs.streak
            // База + бонус за каждые сэкономленные 10 минут + бонус за серию.
            val saved = (goal - minutes) / 10
            val streakBonus = minOf(prefs.streak, 10) * 2
            earn(DAY_BASE + saved + streakBonus, "$date: ${TimeUtils.formatMinutes(minutes)} — цель выполнена")
        } else {
            if (prefs.streak > 0) {
                addHistory(0, "$date: цель превышена, серия ${prefs.streak} дн. прервана")
            }
            prefs.streak = 0
        }
        checkAchievements(lastDayMinutes = minutes)
    }

    /** Пользователь на экране паузы решил не открывать приложение. */
    fun onResist(appLabel: String): Int {
        prefs.totalResists += 1
        val gained = if (prefs.rewardedResistsToday() < MAX_REWARDED_RESISTS_PER_DAY) {
            prefs.incRewardedResistsToday()
            earn(RESIST_POINTS, "Не стал открывать $appLabel")
            RESIST_POINTS
        } else 0
        checkAchievements()
        return gained
    }

    fun startFocus(minutes: Int) {
        val now = System.currentTimeMillis()
        prefs.focusStartedAt = now
        prefs.focusUntil = now + minutes * TimeUtils.MINUTE
    }

    fun cancelFocus() {
        prefs.focusUntil = 0
        prefs.focusStartedAt = 0
        addHistory(0, "Фокус прерван досрочно")
    }

    /** Начисляет награду за фокус-сессию, если она закончилась сама. */
    fun checkFocusCompletion(subject: String? = null) {
        val started = prefs.focusStartedAt
        val until = prefs.focusUntil
        if (started == 0L || until > System.currentTimeMillis()) return
        val minutes = ((until - started) / TimeUtils.MINUTE).toInt()
        prefs.focusStartedAt = 0
        prefs.focusSessionsDone += 1
        val what = if (subject != null) "Занятие «$subject»" else "Фокус"
        earn(maxOf(5, minutes / 2), "$what ${TimeUtils.formatMinutes(minutes)} завершено")
        checkAchievements()
    }

    /** Списать очки (но не ниже нуля) и записать причину в историю. */
    fun penalty(points: Int, reason: String) {
        val taken = minOf(points, prefs.points)
        prefs.points -= taken
        addHistory(-taken, reason)
    }

    /** Купить дополнительные минуты к исчерпанному лимиту. */
    fun buyExtraTime(pkg: String, appLabel: String): Boolean {
        if (prefs.points < EXTRA_TIME_COST) return false
        prefs.points -= EXTRA_TIME_COST
        prefs.addBonusMinutesToday(pkg, EXTRA_TIME_MINUTES)
        addHistory(-EXTRA_TIME_COST, "+$EXTRA_TIME_MINUTES мин для $appLabel")
        return true
    }

    fun plantStage(): PlantStage = prefs.rewardStyle.stages().last { prefs.totalEarned >= it.minPoints }

    fun nextStage(): PlantStage? = prefs.rewardStyle.stages().firstOrNull { it.minPoints > prefs.totalEarned }

    fun history(): List<HistoryEntry> = prefs.historyRaw.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split("|", limit = 3)
            if (parts.size < 3) null
            else HistoryEntry(parts[0].toLong(), parts[1].toInt(), parts[2])
        }
        .toList()

    private fun earn(points: Int, reason: String) {
        prefs.points += points
        prefs.totalEarned += points
        addHistory(points, reason)
    }

    private fun addHistory(delta: Int, reason: String) {
        val line = "${System.currentTimeMillis()}|$delta|${reason.replace("\n", " ")}"
        val lines = listOf(line) + prefs.historyRaw.lines().filter { it.isNotBlank() }
        prefs.historyRaw = lines.take(MAX_HISTORY).joinToString("\n")
    }

    private fun checkAchievements(lastDayMinutes: Int? = null) {
        val got = prefs.achievements.toMutableSet()
        fun unlock(id: String, condition: Boolean) {
            if (condition && got.add(id)) {
                val a = ACHIEVEMENTS.first { it.id == id }
                earn(ACHIEVEMENT_POINTS, "Достижение: ${a.title}")
            }
        }
        unlock("first_resist", prefs.totalResists >= 1)
        unlock("resist_50", prefs.totalResists >= 50)
        unlock("first_day", prefs.goodDays >= 1)
        unlock("streak_3", prefs.bestStreak >= 3)
        unlock("streak_7", prefs.bestStreak >= 7)
        unlock("streak_30", prefs.bestStreak >= 30)
        unlock("focus_1", prefs.focusSessionsDone >= 1)
        unlock("focus_10", prefs.focusSessionsDone >= 10)
        unlock("under_hour", lastDayMinutes != null && lastDayMinutes in 1..60)
        prefs.achievements = got
    }

    companion object {
        const val DAY_BASE = 20
        const val RESIST_POINTS = 3
        const val MAX_REWARDED_RESISTS_PER_DAY = 10
        const val EXTRA_TIME_COST = 15
        const val EXTRA_TIME_MINUTES = 5
        const val ACHIEVEMENT_POINTS = 10
        private const val MAX_HISTORY = 40

        val STAGE_POINTS = listOf(0, 30, 120, 300, 600, 1200, 2500, 5000)

        val ACHIEVEMENTS = listOf(
            Achievement("first_resist", "✋", "Первый отказ", "Не открыл соцсеть после паузы"),
            Achievement("resist_50", "🛡️", "Железная воля", "50 раз передумал открывать"),
            Achievement("first_day", "☀️", "Хороший день", "Первый день в пределах цели"),
            Achievement("streak_3", "🔥", "Три дня подряд", "Серия из 3 дней"),
            Achievement("streak_7", "📅", "Неделя", "Серия из 7 дней"),
            Achievement("streak_30", "🏆", "Месяц свободы", "Серия из 30 дней"),
            Achievement("focus_1", "🎯", "В фокусе", "Завершил первую фокус-сессию"),
            Achievement("focus_10", "🧘", "Мастер фокуса", "10 фокус-сессий"),
            Achievement("under_hour", "🌙", "Меньше часа", "День меньше чем с часом экрана"),
        )
    }
}
