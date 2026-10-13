package com.laromie.rostok.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.laromie.rostok.data.AppCatalog
import com.laromie.rostok.data.Prefs
import com.laromie.rostok.data.PrepRepository
import com.laromie.rostok.data.Strictness
import com.laromie.rostok.data.TimeUtils
import com.laromie.rostok.data.UsageRepository
import com.laromie.rostok.ui.InterceptActivity
import com.laromie.rostok.widget.Widgets
import java.util.concurrent.Executors

/**
 * Следит только за тем, какое приложение сейчас на экране.
 * Для отвлекающих приложений показывает паузу, а при исчерпанном лимите,
 * во время фокуса, в жёстком учебном режиме или ночью перед экзаменом — экран блокировки.
 */
class BlockerService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private lateinit var usage: UsageRepository
    private lateinit var prepRepo: PrepRepository
    private val catalog by lazy { AppCatalog(this) }
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile private var currentPkg: String? = null
    private var lastLaunchPkg: String? = null
    private var lastLaunchTime = 0L
    private var keyboardPackages: Set<String> = emptySet()

    private var lastWidgetUpdate = 0L

    private val periodicCheck = object : Runnable {
        override fun run() {
            currentPkg?.let { check(it) }
            val now = System.currentTimeMillis()
            if (now - lastWidgetUpdate >= WIDGET_INTERVAL_MS) {
                lastWidgetUpdate = now
                Widgets.updateAll(this@BlockerService)
            }
            main.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    /** При разблокировке телефона обновляем виджеты, чтобы на рабочем столе были свежие цифры. */
    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lastWidgetUpdate = System.currentTimeMillis()
            Widgets.updateAll(context)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        usage = UsageRepository(this)
        prepRepo = PrepRepository(this)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        keyboardPackages = imm.enabledInputMethodList.map { it.packageName }.toSet()
        main.postDelayed(periodicCheck, CHECK_INTERVAL_MS)
        ContextCompat.registerReceiver(
            this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Шторка уведомлений и клавиатура не меняют «текущее приложение».
        if (pkg == "com.android.systemui" || pkg in keyboardPackages) return
        if (pkg == packageName) {
            currentPkg = pkg
            return
        }
        val changed = pkg != currentPkg
        currentPkg = pkg
        if (changed) check(pkg)
    }

    /** Проверяет приложение; периодически — чтобы сработать, когда лимит или разрешённое время кончились. */
    private fun check(pkg: String) {
        if (pkg == packageName || !prefs.isTracked(pkg)) return
        worker.execute {
            val mode = decide(pkg) ?: return@execute
            main.post {
                // Пока решали, пользователь мог уже уйти из приложения.
                if (currentPkg == pkg) launchIntercept(pkg, mode)
            }
        }
    }

    /** Что показать для приложения прямо сейчас, или null — не мешать. */
    private fun decide(pkg: String): String? {
        val now = System.currentTimeMillis()
        if (prefs.isFocusActive(now)) return InterceptActivity.MODE_FOCUS

        val unlocked = prefs.unlockUntil(pkg) > now
        val prep = prepRepo.load()?.takeIf { it.nearest() != null && pkg !in it.exceptions }
        val studyTime = prep?.isStudyTime(now) == true

        if (prep != null && prep.isSleepTime(now)) {
            return if (unlocked) null else InterceptActivity.MODE_SLEEP
        }
        if (studyTime && prep?.strictness == Strictness.HARD) {
            return if (unlocked) null else InterceptActivity.MODE_STUDY
        }

        val limit = prefs.limitMinutes(pkg)
        if (limit > 0) {
            val allowedMs = (limit + prefs.bonusMinutesToday(pkg)) * TimeUtils.MINUTE
            val used = usage.today().timeFor(pkg)
            if (used >= allowedMs) return InterceptActivity.MODE_LIMIT
            maybeWarn(pkg, allowedMs - used)
        }

        // В учебные часы пауза включена всегда, даже если в настройках её выключили.
        if (!prefs.pauseEnabled && !studyTime) return null
        return if (unlocked) null else InterceptActivity.MODE_PAUSE
    }

    /** Уже показанные предупреждения: "день:пакет:порог". */
    private val warned = HashSet<String>()

    /** Предупреждение за 5 и за 1 минуту до конца лимита, каждое — один раз в день. */
    private fun maybeWarn(pkg: String, remainingMs: Long) {
        if (!prefs.limitWarnings) return
        val day = TimeUtils.startOfToday()
        val due = WARN_THRESHOLDS_MIN.filter { remainingMs <= it * TimeUtils.MINUTE }
        if (due.isEmpty()) return
        val keys = due.map { "$day:$pkg:$it" }
        if (keys.all { it in warned }) return
        if (warned.none { it.startsWith("$day:") }) warned.clear() // новый день — старое забываем
        warned.addAll(keys)

        val minutesLeft = ((remainingMs + TimeUtils.MINUTE - 1) / TimeUtils.MINUTE).coerceAtLeast(1)
        val label = catalog.label(pkg)
        main.post {
            Toast.makeText(this, "⏳ $label: осталось $minutesLeft мин из дневного лимита", Toast.LENGTH_LONG).show()
        }
    }

    private fun launchIntercept(pkg: String, mode: String) {
        val now = System.currentTimeMillis()
        if (pkg == lastLaunchPkg && now - lastLaunchTime < RELAUNCH_GUARD_MS) return
        lastLaunchPkg = pkg
        lastLaunchTime = now
        startActivity(InterceptActivity.intent(this, pkg, mode))
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        main.removeCallbacks(periodicCheck)
        runCatching { unregisterReceiver(unlockReceiver) }
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 15_000L
        private const val RELAUNCH_GUARD_MS = 2_000L
        private const val WIDGET_INTERVAL_MS = 5 * 60_000L
        private val WARN_THRESHOLDS_MIN = listOf(5L, 1L)

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, BlockerService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return enabled.split(':').any {
                ComponentName.unflattenFromString(it) == expected
            }
        }
    }
}
