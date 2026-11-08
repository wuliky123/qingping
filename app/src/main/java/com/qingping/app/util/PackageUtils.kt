package com.qingping.app.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.qingping.app.service.QingpingAccessibilityService

/** 误触跳转的目标包名：常见购物 App + 浏览器。命中即视为开屏广告误触跳转。 */
val SHOPPING_AND_BROWSER_PACKAGES: Set<String> = setOf(
    // 购物
    "com.taobao.taobao",          // 淘宝
    "com.jingdong.app.mall",      // 京东
    "com.xunmeng.pinduoduo",      // 拼多多
    "com.alibaba.wireless",       // 1688
    "com.sankuai.meituan",        // 美团
    "com.sankuai.meituan.takeoutnew", // 美团外卖
    "com.xiaomi.shop",            // 小米商城
    // 浏览器
    "com.android.browser",
    "com.UCMobile",               // UC 浏览器
    "com.tencent.mtt",            // QQ 浏览器
    "org.mozilla.firefox",
    "com.microsoft.emmx",         // Edge
    "com.qihoo.browser",          // 360 浏览器
    "com.oppo.browser",           // OPPO 浏览器
    "com.huawei.browser",         // 华为浏览器
    "com.mi.globalbrowser",       // 小米浏览器
    "com.vivo.browser",           // vivo 浏览器
    "com.samsung.android.app.sbrowser", // 三星浏览器
    "com.baidu.searchbox"         // 百度 App（常被广告跳转打开）
)

/** 本 App 自己的包名，误触判断时排除。 */
const val OWN_PACKAGE = "com.qingping.app"

/** 无障碍服务是否已开启：读系统 Settings.Secure 里的已启用服务列表。 */
fun isAccessibilityEnabled(context: Context): Boolean {
    val expected = ComponentName(context, QingpingAccessibilityService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.split(":").any { it.trim() == expected }
}

/** 跳系统无障碍设置页（用户手动打开清屏的服务）。 */
fun openAccessibilitySettings(context: Context) {
    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/** 是否已忽略电池优化。 */
fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/** 弹系统框请求忽略电池优化（用户点“允许”才生效）。 */
fun requestIgnoreBatteryOptimizations(context: Context) {
    if (isIgnoringBatteryOptimizations(context)) return
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

/** 打开本 App 的应用详情页（自启动、耗电管理等入口都在这里）。 */
fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

/** 打开电池优化设置页（手动把清屏设为“不优化”的备用入口）。 */
fun openBatteryOptimizationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure { openAppDetailsSettings(context) }
}

/** 尝试打开 OPPO 自启动管理页，打不开就退回应用详情页。 */
fun openAutoStartSettings(context: Context) {
    val candidates = listOf(
        // ColorOS 自启动管理（不同版本 activity 名可能变化，逐个试）
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")
    )
    for (cn in candidates) {
        val intent = Intent().apply {
            component = cn
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
    openAppDetailsSettings(context)
}

/** 已安装的可启动应用（供首页按 App 列表展示），按名字排序。 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable?
)

fun getInstalledApps(context: Context): List<InstalledApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
    }
    return resolveInfos
        .mapNotNull { ri ->
            val ai: ApplicationInfo = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
            if (ai.packageName == OWN_PACKAGE) return@mapNotNull null
            InstalledApp(
                packageName = ai.packageName,
                label = pm.getApplicationLabel(ai).toString(),
                icon = runCatching { pm.getApplicationIcon(ai) }.getOrNull()
            )
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label }
}

/** 判断一个包名是不是系统包（首页默认只展示用户常开的 App 时可用）。 */
fun isSystemApp(pm: PackageManager, packageName: String): Boolean {
    return runCatching {
        val ai = pm.getApplicationInfo(packageName, 0)
        (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
    }.getOrDefault(false)
}
