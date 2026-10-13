package com.laromie.rostok.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Сегодня", Icons.Filled.Today),
    Tab("Учёба", Icons.Filled.School),
    Tab("Приложения", Icons.Filled.Apps),
    Tab("Награды", Icons.Filled.EmojiEvents),
    Tab("Настройки", Icons.Filled.Settings),
)

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()
    private val tab = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tab.intValue = savedInstanceState?.getInt(KEY_TAB) ?: intent.getIntExtra(EXTRA_TAB, 0)
        setContent {
            RostokTheme {
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = tab.intValue == i,
                                    onClick = { tab.intValue = i },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(t.title, maxLines = 1) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    val modifier = Modifier.padding(padding)
                    val state = vm.state
                    when (tab.intValue) {
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

    private fun openUsageAccess() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun openAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    companion object {
        private const val EXTRA_TAB = "tab"
        private const val KEY_TAB = "tab"
        const val TAB_PREP = 1

        /** Открыть приложение сразу на вкладке «Учёба». */
        fun prepIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_TAB, TAB_PREP)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
