package com.laromie.rostok.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/** Хранит текущую подготовку и журнал занятий (JSON в SharedPreferences). */
class PrepRepository(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("prep", Context.MODE_PRIVATE)

    fun load(): Prep? {
        val raw = sp.getString(KEY_PREP, null) ?: return null
        return runCatching { prepFromJson(JSONObject(raw)) }.getOrNull()
    }

    fun save(prep: Prep) = sp.edit { putString(KEY_PREP, prepToJson(prep).toString()) }

    fun delete() = sp.edit {
        remove(KEY_PREP)
        remove(KEY_SESSIONS)
        remove(KEY_ACTIVE_ITEM)
    }

    fun setPassed(itemId: String, passed: Boolean) {
        val prep = load() ?: return
        save(prep.copy(items = prep.items.map { if (it.id == itemId) it.copy(passed = passed) else it }))
    }

    // ---------- Журнал занятий ----------

    fun sessions(): List<StudySession> {
        val arr = JSONArray(sp.getString(KEY_SESSIONS, "[]"))
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            StudySession(o.getString("item"), o.getLong("start"), o.getInt("min"))
        }
    }

    fun addSession(session: StudySession) {
        if (session.minutes <= 0) return
        val arr = JSONArray(sp.getString(KEY_SESSIONS, "[]"))
        arr.put(JSONObject().put("item", session.itemId).put("start", session.start).put("min", session.minutes))
        // Держим последние записи, чтобы файл не рос бесконечно.
        val trimmed = if (arr.length() > MAX_SESSIONS) {
            JSONArray().also { t -> for (i in arr.length() - MAX_SESSIONS until arr.length()) t.put(arr.get(i)) }
        } else arr
        sp.edit { putString(KEY_SESSIONS, trimmed.toString()) }
    }

    /** Занятие, идущее прямо сейчас (id предмета), или null. */
    var activeItemId: String?
        get() = sp.getString(KEY_ACTIVE_ITEM, null)
        set(value) = sp.edit { putString(KEY_ACTIVE_ITEM, value) }

    var emergencyExits: Int
        get() = sp.getInt("emergency_exits", 0)
        set(value) = sp.edit { putInt("emergency_exits", value) }

    // ---------- JSON ----------

    private fun prepToJson(p: Prep) = JSONObject()
        .put("title", p.title)
        .put("template", p.template.name)
        .put("strictness", p.strictness.name)
        .put("days", p.studyDays)
        .put("start", p.studyStart)
        .put("end", p.studyEnd)
        .put("exceptions", JSONArray(p.exceptions.toList()))
        .put("sleep", p.sleepEnabled)
        .put("bedtime", p.bedtime)
        .put("wake", p.wakeTime)
        .put("items", JSONArray(p.items.map { i ->
            JSONObject()
                .put("id", i.id)
                .put("name", i.name)
                .put("kind", i.kind.name)
                .put("date", i.date)
                .put("target", i.targetMinutes)
                .put("passed", i.passed)
        }))

    private fun prepFromJson(o: JSONObject): Prep {
        val items = o.getJSONArray("items")
        val exceptions = o.optJSONArray("exceptions") ?: JSONArray()
        return Prep(
            title = o.getString("title"),
            template = PrepTemplate.valueOf(o.getString("template")),
            strictness = Strictness.valueOf(o.getString("strictness")),
            studyDays = o.getInt("days"),
            studyStart = o.getInt("start"),
            studyEnd = o.getInt("end"),
            exceptions = (0 until exceptions.length()).map { exceptions.getString(it) }.toSet(),
            sleepEnabled = o.getBoolean("sleep"),
            bedtime = o.getInt("bedtime"),
            wakeTime = o.getInt("wake"),
            items = (0 until items.length()).map {
                val i = items.getJSONObject(it)
                PrepItem(
                    id = i.getString("id"),
                    name = i.getString("name"),
                    kind = ItemKind.valueOf(i.getString("kind")),
                    date = i.getLong("date"),
                    targetMinutes = i.getInt("target"),
                    passed = i.getBoolean("passed"),
                )
            },
        )
    }

    companion object {
        private const val KEY_PREP = "prep"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_ACTIVE_ITEM = "active_item"
        private const val MAX_SESSIONS = 2000
    }
}

/**
 * Учебные занятия поверх режима фокуса: пока идёт занятие, соцсети заблокированы,
 * а время записывается на предмет.
 */
class Study(
    private val prefs: Prefs,
    private val rewards: Rewards,
    private val repo: PrepRepository,
) {
    fun start(item: PrepItem, minutes: Int) {
        // Если уже шло другое занятие, сначала засчитываем его.
        if (repo.activeItemId != null) stopEarly()
        rewards.startFocus(minutes)
        repo.activeItemId = item.id
    }

    /** Закончить раньше: время засчитывается, но бонус за завершённый фокус — нет. */
    fun stopEarly() {
        val itemId = repo.activeItemId ?: return
        val started = prefs.focusStartedAt
        if (started > 0) {
            val minutes = ((System.currentTimeMillis() - started) / TimeUtils.MINUTE).toInt()
            repo.addSession(StudySession(itemId, started, minutes))
        }
        repo.activeItemId = null
        rewards.cancelFocus()
    }

    /** Засчитать занятие, если таймер дошёл до конца. Безопасно вызывать сколько угодно раз. */
    fun checkCompletion() {
        val itemId = repo.activeItemId
        val started = prefs.focusStartedAt
        val until = prefs.focusUntil
        if (itemId != null && started > 0 && until <= System.currentTimeMillis()) {
            repo.addSession(StudySession(itemId, started, ((until - started) / TimeUtils.MINUTE).toInt()))
            repo.activeItemId = null
            val name = repo.load()?.items?.firstOrNull { it.id == itemId }?.name
            rewards.checkFocusCompletion(name)
        } else {
            rewards.checkFocusCompletion()
        }
    }

    fun minutesFor(itemId: String): Int = repo.sessions().filter { it.itemId == itemId }.sumOf { it.minutes }

    fun minutesSince(time: Long): Int = repo.sessions().filter { it.start >= time }.sumOf { it.minutes }

    /** Аварийный выход в жёстком режиме: открыть приложение на 10 минут ценой очков. */
    fun emergencyUnlock(pkg: String, appLabel: String) {
        repo.emergencyExits += 1
        prefs.setUnlockUntil(pkg, System.currentTimeMillis() + EMERGENCY_MINUTES * TimeUtils.MINUTE)
        rewards.penalty(EMERGENCY_COST, "Аварийный выход: $appLabel")
    }

    companion object {
        const val EMERGENCY_MINUTES = 10
        const val EMERGENCY_COST = 5
        const val EMERGENCY_PHRASE = "я осознанно сдаюсь"
        const val EMERGENCY_WAIT_SECONDS = 30
    }
}
