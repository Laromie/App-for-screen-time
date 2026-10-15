package com.laromie.rostok.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.laromie.rostok.R

enum class LauncherIcon(val title: String, val alias: String, val preview: Int) {
    DEFAULT("Стандартная", "LauncherDefault", R.mipmap.ic_launcher),
    GLASS("Стекло", "LauncherGlass", R.mipmap.ic_launcher_glass),
}

/**
 * Смена иконки на рабочем столе: в манифесте два activity-alias с разными иконками,
 * включаем нужный и выключаем остальные.
 */
object AppIconSwitcher {

    private fun component(context: Context, icon: LauncherIcon) =
        ComponentName(context.packageName, "${context.packageName}.${icon.alias}")

    fun current(context: Context): LauncherIcon {
        val pm = context.packageManager
        return LauncherIcon.entries.firstOrNull { icon ->
            when (pm.getComponentEnabledSetting(component(context, icon))) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                // «По умолчанию» — значит как в манифесте: включена только стандартная.
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == LauncherIcon.DEFAULT
                else -> false
            }
        } ?: LauncherIcon.DEFAULT
    }

    fun set(context: Context, icon: LauncherIcon) {
        if (current(context) == icon) return
        val pm = context.packageManager
        // Сначала включаем новую, потом выключаем старые — чтобы приложение ни на миг не пропало из списка.
        pm.setComponentEnabledSetting(
            component(context, icon),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        LauncherIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(
                component(context, it),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
