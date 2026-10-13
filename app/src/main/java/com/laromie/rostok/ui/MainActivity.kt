package com.laromie.rostok.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import com.laromie.rostok.data.Prefs
import com.laromie.rostok.work.Background

private val tabs = listOf(
    NavTab("Сегодня", Icons.Filled.Today),
    NavTab("Учёба", Icons.Filled.School),
    NavTab("Приложения", Icons.Filled.Apps),
    NavTab("Награды", Icons.Filled.EmojiEvents),
    NavTab("Настройки", Icons.Filled.Settings),
)

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val tab = mutableIntStateOf(0)
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tab.intValue = savedInstanceState?.getInt(KEY_TAB) ?: intent.getIntExtra(EXTRA_TAB, 0)
        Background.schedule(this)
        askNotificationsOnce()
        setContent {
            RostokTheme {
                Scaffold(
                    bottomBar = {
                        CompactNavBar(tabs, tab.intValue) { tab.intValue = it }
                    },
                ) { padding ->
                    val state = vm.state
                    // Разделы сменяются со сдвигом в сторону нажатой вкладки и растворением.
                    AnimatedContent(
                        targetState = tab.intValue,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(tween(260)) { it * dir / 5 } + fadeIn(tween(260))) togetherWith
                                (slideOutHorizontally(tween(200)) { -it * dir / 5 } + fadeOut(tween(160)))
                        },
                        modifier = Modifier.padding(padding).fillMaxSize(),
                        label = "tabs",
                    ) { current ->
                    val modifier = Modifier.fillMaxSize()
                    when (current) {
                        0 -> TodayScreen(
                            state = state,
                            catalog = vm.catalog,
                            onOpenUsageAccess = ::openUsageAccess,
                            onOpenAccessibility = ::openAccessibility,
                            onOpenPrep = { tab.intValue = TAB_PREP },
                            modifier = modifier,
                        )
                        TAB_PREP -> PrepScreen(state, vm, modifier)
                        2 -> AppsScreen(state, vm, modifier)
                        3 -> RewardsScreen(state, modifier)
                        else -> SettingsScreen(
                            state = state,
                            vm = vm,
                            onOpenUsageAccess = ::openUsageAccess,
                            onOpenAccessibility = ::openAccessibility,
                            modifier = modifier,
                        )
                    }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasExtra(EXTRA_TAB)) tab.intValue = intent.getIntExtra(EXTRA_TAB, 0)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, tab.intValue)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    /** Один раз спрашиваем разрешение на уведомления — для вечернего итога (Android 13+). */
    private fun askNotificationsOnce() {
        val prefs = Prefs(this)
        if (prefs.notificationsAsked || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        prefs.notificationsAsked = true
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun openUsageAccess() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    companion object {
        private const val EXTRA_TAB = "tab"
        private const val KEY_TAB = "tab"
        const val TAB_TODAY = 0
        const val TAB_PREP = 1

        /** Открыть приложение сразу на нужной вкладке. */
        fun tabIntent(context: Context, tab: Int): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TAB, tab)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        fun prepIntent(context: Context): Intent = tabIntent(context, TAB_PREP)
    }
}
