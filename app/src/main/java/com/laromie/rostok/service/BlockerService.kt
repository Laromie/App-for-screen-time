package com.laromie.rostok.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.laromie.rostok.data.Prefs
import com.laromie.rostok.data.TimeUtils
import com.laromie.rostok.data.UsageRepository
import com.laromie.rostok.ui.InterceptActivity
import java.util.concurrent.Executors

/**
 * Следит только за тем, какое приложение сейчас на экране.
 * Для отвлекающих приложений показывает паузу, а при исчерпанном лимите
 * или во время фокуса — экран блокировки.
 */
class BlockerService : AccessibilityService() {

    private lateinit var prefs: Prefs
    private lateinit var usage: UsageRepository
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile private var currentPkg: String? = null
    private var lastLaunchPkg: String? = null
    private var lastLaunchTime = 0L
    private var keyboardPackages: Set<String> = emptySet()

    private val periodicCheck = object : Runnable {
        override fun run() {
            currentPkg?.let { check(it) }
            main.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = Prefs(this)
        usage = UsageRepository(this)
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        keyboardPackages = imm.enabledInputMethodList.map { it.packageName }.toSet()
        main.postDelayed(periodicCheck, CHECK_INTERVAL_MS)
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

        val limit = prefs.limitMinutes(pkg)
        if (limit > 0) {
            val allowedMs = (limit + prefs.bonusMinutesToday(pkg)) * TimeUtils.MINUTE
            if (usage.today().timeFor(pkg) >= allowedMs) return InterceptActivity.MODE_LIMIT
        }

        if (!prefs.pauseEnabled) return null
        return if (prefs.unlockUntil(pkg) > now) null else InterceptActivity.MODE_PAUSE
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
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 15_000L
        private const val RELAUNCH_GUARD_MS = 2_000L

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
