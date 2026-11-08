package com.qingping.app.data

import android.content.Context

/**
 * 开关存在 SharedPreferences 里（不是 Room），因为无障碍服务要在事件回调里
 * 同步读取，不能等协程查库。
 * 全局开关 + 每个 App 的单独开关，默认全部开启。
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("qingping_prefs", Context.MODE_PRIVATE)

    var globalEnabled: Boolean
        get() = sp.getBoolean("global_enabled", true)
        set(v) = sp.edit().putBoolean("global_enabled", v).apply()

    var keepAliveStarted: Boolean
        get() = sp.getBoolean("keep_alive_started", false)
        set(v) = sp.edit().putBoolean("keep_alive_started", v).apply()

    fun isAppEnabled(packageName: String): Boolean =
        sp.getBoolean("app_enabled_$packageName", true)

    fun setAppEnabled(packageName: String, enabled: Boolean) {
        sp.edit().putBoolean("app_enabled_$packageName", enabled).apply()
    }
}
