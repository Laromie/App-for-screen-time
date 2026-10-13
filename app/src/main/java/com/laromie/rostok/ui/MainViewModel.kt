package com.laromie.rostok.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.laromie.rostok.data.AppCatalog
import com.laromie.rostok.data.DayUsage
import com.laromie.rostok.data.HistoryEntry
import com.laromie.rostok.data.InstalledApp
import com.laromie.rostok.data.PlantStage
import com.laromie.rostok.data.Prep
import com.laromie.rostok.data.PrepItem
import com.laromie.rostok.data.PrepRepository
import com.laromie.rostok.data.Study
import com.laromie.rostok.data.TimeUtils
import com.laromie.rostok.data.Prefs
import com.laromie.rostok.data.Rewards
import com.laromie.rostok.data.UsageRepository
import com.laromie.rostok.service.BlockerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val loaded: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val hasAccessibility: Boolean = false,
    val today: DayUsage? = null,
    val week: List<DayUsage> = emptyList(),
    val apps: List<InstalledApp> = emptyList(),
    val tracked: Set<String> = emptySet(),
    val limits: Map<String, Int> = emptyMap(),
    val goalMinutes: Int = 120,
    val pauseSeconds: Int = 10,
    val pauseEnabled: Boolean = true,
    val focusUntil: Long = 0,
    val points: Int = 0,
    val totalEarned: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val totalResists: Int = 0,
    val stage: PlantStage = Rewards.STAGES.first(),
    val nextStage: PlantStage? = null,
    val achievements: Set<String> = emptySet(),
    val history: List<HistoryEntry> = emptyList(),
    // Режим подготовки
    val prep: Prep? = null,
    val prepMinutes: Map<String, Int> = emptyMap(),
    val studyTodayMinutes: Int = 0,
    val studyWeekMinutes: Int = 0,
    val activeItemId: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)
    private val usage = UsageRepository(app)
    val catalog = AppCatalog(app)
    private val rewards = Rewards(prefs, usage)
    private val prepRepo = PrepRepository(app)
    private val study = Study(prefs, rewards, prepRepo)

    var state by mutableStateOf(UiState())
        private set

    init {
        if (!prefs.initialized) {
            // Первый запуск: сразу предлагаем ограничить установленные соцсети.
            val installed = AppCatalog.KNOWN_DISTRACTIONS.filter { catalog.isInstalled(it) }
            prefs.trackedApps = installed.toSet()
            installed.forEach { prefs.setLimitMinutes(it, AppCatalog.DEFAULT_LIMIT_MINUTES) }
            prefs.initialized = true
        }
    }

    /** Перечитать всё: вызывается при каждом возвращении в приложение. */
    fun refresh() {
        viewModelScope.launch {
            val fresh = withContext(Dispatchers.IO) {
                rewards.evaluatePastDays()
                study.checkCompletion()
                val apps = if (state.apps.isEmpty()) catalog.launchableApps() else state.apps
                val week = usage.lastDays(7)
                buildState(apps, week)
            }
            state = fresh
        }
    }

    /** Быстрое обновление настроек без пересчёта статистики. */
    private fun refreshPrefs() {
        state = buildState(state.apps, state.week)
    }

    private fun buildState(apps: List<InstalledApp>, week: List<DayUsage>): UiState {
        val tracked = prefs.trackedApps
        val prep = prepRepo.load()
        return UiState(
            loaded = true,
            hasUsageAccess = usage.hasPermission(),
            hasAccessibility = BlockerService.isEnabled(getApplication<Application>()),
            today = week.lastOrNull(),
            week = week,
            apps = apps,
            tracked = tracked,
            limits = apps.associate { it.packageName to prefs.limitMinutes(it.packageName) },
            goalMinutes = prefs.dailyGoalMinutes,
            pauseSeconds = prefs.pauseSeconds,
            pauseEnabled = prefs.pauseEnabled,
            focusUntil = prefs.focusUntil,
            points = prefs.points,
            totalEarned = prefs.totalEarned,
            streak = prefs.streak,
            bestStreak = prefs.bestStreak,
            totalResists = prefs.totalResists,
            stage = rewards.plantStage(),
            nextStage = rewards.nextStage(),
            achievements = prefs.achievements,
            history = rewards.history(),
            prep = prep,
            prepMinutes = prep?.items?.associate { it.id to study.minutesFor(it.id) } ?: emptyMap(),
            studyTodayMinutes = study.minutesSince(TimeUtils.startOfToday()),
            studyWeekMinutes = study.minutesSince(TimeUtils.startOfToday() - 6 * TimeUtils.DAY),
            activeItemId = prepRepo.activeItemId,
        )
    }

    fun setTracked(pkg: String, tracked: Boolean) {
        prefs.setTracked(pkg, tracked)
        if (tracked && prefs.limitMinutes(pkg) == 0) {
            prefs.setLimitMinutes(pkg, AppCatalog.DEFAULT_LIMIT_MINUTES)
        }
        refreshPrefs()
    }

    fun setLimit(pkg: String, minutes: Int) {
        prefs.setLimitMinutes(pkg, minutes)
        refreshPrefs()
    }

    fun setGoal(minutes: Int) {
        prefs.dailyGoalMinutes = minutes
        refreshPrefs()
    }

    fun setPauseSeconds(seconds: Int) {
        prefs.pauseSeconds = seconds
        refreshPrefs()
    }

    fun setPauseEnabled(enabled: Boolean) {
        prefs.pauseEnabled = enabled
        refreshPrefs()
    }

    fun startFocus(minutes: Int) {
        rewards.startFocus(minutes)
        refreshPrefs()
    }

    fun cancelFocus() {
        // Если это было занятие по предмету, засчитываем уже потраченное время.
        if (prepRepo.activeItemId != null) study.stopEarly() else rewards.cancelFocus()
        refreshPrefs()
    }

    /** Вызывается из таймера на экране, когда фокус закончился. */
    fun onFocusFinished() {
        study.checkCompletion()
        refreshPrefs()
    }

    // ---------- Режим подготовки ----------

    fun savePrep(prep: Prep) {
        prepRepo.save(prep)
        refreshPrefs()
    }

    fun deletePrep() {
        if (prepRepo.activeItemId != null) study.stopEarly()
        prepRepo.delete()
        refreshPrefs()
    }

    fun setPassed(itemId: String, passed: Boolean) {
        prepRepo.setPassed(itemId, passed)
        refreshPrefs()
    }

    fun startStudy(item: PrepItem, minutes: Int) {
        study.start(item, minutes)
        refreshPrefs()
    }

    fun stopStudy() {
        study.stopEarly()
        refreshPrefs()
    }
}
