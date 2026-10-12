package com.laromie.rostok.data

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import java.util.concurrent.ConcurrentHashMap

data class InstalledApp(val packageName: String, val label: String, val icon: Drawable)

/** Установленные приложения с иконками и список популярных «пожирателей времени». */
class AppCatalog(context: Context) {

    private val pm = context.applicationContext.packageManager
    private val ownPackage = context.packageName
    private val labelCache = ConcurrentHashMap<String, String>()
    private val iconCache = ConcurrentHashMap<String, Drawable>()

    fun launchableApps(): List<InstalledApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != ownPackage }
            .map { info ->
                val label = pm.getApplicationLabel(info).toString().also { labelCache[info.packageName] = it }
                val icon = iconCache.getOrPut(info.packageName) { pm.getApplicationIcon(info) }
                InstalledApp(info.packageName, label, icon)
            }
            .sortedBy { it.label.lowercase() }
    }

    fun label(pkg: String): String = labelCache.getOrPut(pkg) {
        runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(pkg)
    }

    fun icon(pkg: String): Drawable? = iconCache[pkg]
        ?: runCatching { pm.getApplicationIcon(pkg) }.getOrNull()?.also { iconCache[pkg] = it }

    fun isInstalled(pkg: String): Boolean =
        runCatching { pm.getApplicationInfo(pkg, 0); true }.getOrDefault(false)

    companion object {
        /** Соцсети и бесконечные ленты, которые предлагаем ограничить сразу. */
        val KNOWN_DISTRACTIONS = listOf(
            "com.instagram.android",
            "com.zhiliaoapp.musically",      // TikTok
            "com.ss.android.ugc.trill",      // TikTok (некоторые регионы)
            "com.google.android.youtube",
            "com.vkontakte.android",
            "com.vk.vkvideo",
            "ru.ok.android",
            "com.facebook.katana",
            "com.twitter.android",
            "com.reddit.frontpage",
            "com.snapchat.android",
            "com.pinterest",
            "com.instagram.barcelona",       // Threads
            "tv.twitch.android.app",
            "ru.rutube.app",
            "com.yandex.zen",
            "com.likee.video",
            "video.like",
        )
        const val DEFAULT_LIMIT_MINUTES = 30
    }
}
