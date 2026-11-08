package com.qingping.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qingping.app.service.KeepAliveService

/**
 * 开机自启：手机重启、应用被覆盖安装后，把保活前台服务拉起来。
 * 注意：无障碍服务本身不能由代码自动开启（系统强制要求用户手动在设置里打开），
 * 这里只能拉起保活服务；无障碍服务掉了，用户需要在清屏首页点“去开启”。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                KeepAliveService.start(context)
            }
        }
    }
}
