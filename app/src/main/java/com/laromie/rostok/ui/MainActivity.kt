package com.laromie.rostok.ui

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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Сегодня", Icons.Filled.Today),
    Tab("Приложения", Icons.Filled.Apps),
    Tab("Награды", Icons.Filled.EmojiEvents),
    Tab("Настройки", Icons.Filled.Settings),
)

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RostokTheme {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            tabs.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(t.icon, contentDescription = null) },
                                    label = { Text(t.title) },
                                )
                            }
                        }
                    },
                ) { padding ->
                    val modifier = Modifier.padding(padding)
                    val state = vm.state
                    when (tab) {
                        0 -> TodayScreen(
                            state = state,
                            catalog = vm.catalog,
                            onOpenUsageAccess = ::openUsageAccess,
                            onOpenAccessibility = ::openAccessibility,
                            modifier = modifier,
                        )
                        1 -> AppsScreen(state, vm, modifier)
                        2 -> RewardsScreen(state, modifier)
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
}
