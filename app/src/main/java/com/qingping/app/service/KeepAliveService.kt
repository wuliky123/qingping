package com.qingping.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.qingping.app.QingpingApp
import com.qingping.app.R
import com.qingping.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 保活前台服务：挂一条常驻通知，让系统把清屏当作“正在干活”的应用，别轻易杀掉。
 *
 * 前台服务类型说明（针对用户机型 OPPO Find X8 Pro / ColorOS 16 = Android 16）：
 * - targetSdk 保持 34，不升 36；Android 14 起前台服务必须声明类型，这里用 specialUse，
 *   并在 Manifest 里申请了 FOREGROUND_SERVICE_SPECIAL_USE；
 * - Android 16 对 dataSync 等类型加了 6 小时超时，specialUse 不受该超时限制；
 * - 从 BOOT_COMPLETED 启动前台服务属于系统豁免的场景，不受后台启动限制；
 * - Android 13+ 发通知要先拿到 POST_NOTIFICATIONS 权限，主界面首次打开时会申请，
 *   用户拒绝也不会崩，只是通知发不出来、保活效果打折。
 */
class KeepAliveService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }
        (applicationContext as? QingpingApp)?.prefs?.keepAliveStarted = true
        return START_STICKY // 被杀后系统尽量重启本服务
    }

    private fun buildNotification(): Notification {
        val app = applicationContext as? QingpingApp
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        var todayText = ""
        if (app != null) {
            scope.launch {
                val (skip, mistap) = app.statsRepository.todayTotal()
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                val updated = NotificationCompat.Builder(this@KeepAliveService, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle("清屏正在运行")
                    .setContentText("今日已跳过 $skip 次 · 拦截误触 $mistap 次")
                    .setContentIntent(pending)
                    .setOngoing(true)
                    .setSilent(true)
                    .build()
                runCatching { nm.notify(NOTIFICATION_ID, updated) }
            }
            todayText = "点开查看今日拦截统计"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("清屏正在运行")
            .setContentText(todayText.ifEmpty { "正在帮你跳过开屏广告" })
            .setContentIntent(pending)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "清屏保活",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "清屏正在运行的常驻通知，删掉它服务容易被系统杀掉"
            setShowBadge(false)
        }
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "qingping_keepalive"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, KeepAliveService::class.java)
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
