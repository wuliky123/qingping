package com.qingping.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.qingping.app.QingpingApp
import com.qingping.app.util.OWN_PACKAGE
import com.qingping.app.util.SHOPPING_AND_BROWSER_PACKAGES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * 核心服务：自动点开屏广告“跳过”。
 *
 * 原理（诚实说明）：不是把广告拦在外面不让它加载，而是等开屏广告出现后，
 * 在屏幕上找到“跳过”按钮并替用户点掉；误触跳转到购物/浏览器时再按返回拉回来。
 *
 * 安全边界：
 * - 只点文字/描述/控件 id 明确含“跳过”的节点，绝不点广告本体、绝不点其他按钮；
 * - 跳过按钮要在可见节点里找，文字长度限制在 10 字以内，避免把广告文案误认成按钮；
 * - 每个 App 1 秒内最多点一次，防止重复点击；
 * - 统计只写本地 Room 数据库，不联网、不上传。
 */
class QingpingAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 每个包上次点跳过的时间：1 秒去重。 */
    private val lastSkipAt = ConcurrentHashMap<String, Long>()

    /** 上次误触拉回的时间：3 秒内同一跳转只记一次。 */
    private var lastMistapAt = 0L

    /** 最近一次“看到开屏广告（找到跳过按钮）”的包名和时间，供误触判断用。 */
    @Volatile
    private var lastAdPackage: String? = null

    @Volatile
    private var lastAdAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        // 服务一启动就拉起保活前台服务（常驻通知），提高存活率。
        KeepAliveService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == OWN_PACKAGE) return

        val app = applicationContext as? QingpingApp ?: return
        if (!app.prefs.globalEnabled) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // 前台切到购物/浏览器，且 2 秒内刚出现过开屏广告 → 视为误触跳转，拉回来。
                checkMistap(pkg)
                // 窗口变了也顺手扫一次跳过按钮（有的广告在窗口切换后才出现按钮）。
                if (app.prefs.isAppEnabled(pkg)) tryClickSkip(pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (app.prefs.isAppEnabled(pkg)) tryClickSkip(pkg)
            }
        }
    }

    /** 在当前窗口找跳过按钮并点掉。 */
    private fun tryClickSkip(pkg: String) {
        val now = System.currentTimeMillis()
        if (now - (lastSkipAt[pkg] ?: 0L) < SKIP_DEBOUNCE_MS) return

        val root = rootInActiveWindow ?: return
        try {
            val skipNode = findSkipNode(root) ?: return
            // 看到跳过按钮 = 出现开屏广告，先记下来，供误触判断用（无论这次点没点上）。
            lastAdPackage = pkg
            lastAdAt = now
            val clicked = performClick(skipNode)
            if (clicked) {
                lastSkipAt[pkg] = now
                val app = applicationContext as? QingpingApp ?: return
                scope.launch { app.statsRepository.recordSkip(pkg) }
            }
        } finally {
            root.recycle()
        }
    }

    /** 误触拦截：2 秒内从开屏广告跳到购物/浏览器，就按返回并记一次。 */
    private fun checkMistap(newPkg: String) {
        if (newPkg !in SHOPPING_AND_BROWSER_PACKAGES) return
        val adPkg = lastAdPackage ?: return
        if (adPkg == newPkg) return
        val now = System.currentTimeMillis()
        if (now - lastAdAt > MISTAP_WINDOW_MS) return
        if (now - lastMistapAt < MISTAP_DEBOUNCE_MS) return
        lastMistapAt = now
        lastAdPackage = null
        performGlobalAction(GLOBAL_ACTION_BACK)
        val app = applicationContext as? QingpingApp ?: return
        scope.launch { app.statsRepository.recordMistap(adPkg) }
    }

    /**
     * 广度优先遍历找跳过节点。
     * 命中条件（三选一）：文本含“跳过”、无障碍描述含“跳过”、控件 id 含 skip。
     * 文本超过 10 字的不认，避免把广告正文当按钮。
     */
    private fun findSkipNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++
            try {
                if (node.isVisibleToUser && isSkipNode(node)) return node
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            } catch (_: Exception) {
                // 个别节点读失败就跳过，不中断整轮遍历。
            }
        }
        return null
    }

    private fun isSkipNode(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.trim().orEmpty()
        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        if (text.isNotEmpty() && text.length <= 10 && SKIP_TEXTS.any { text.contains(it) }) return true
        if (desc.isNotEmpty() && desc.length <= 10 && SKIP_TEXTS.any { desc.contains(it) }) return true
        if (viewId.contains("skip")) return true
        return false
    }

    /** 点节点本身；本身不可点就往上找最多 3 层可点的父节点。只点跳过节点及其父容器。 */
    private fun performClick(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth <= 3) {
            if (current.isClickable) {
                return try {
                    current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                } catch (_: Exception) {
                    false
                }
            }
            current = current.parent
            depth++
        }
        return false
    }

    override fun onInterrupt() {
        // 系统要求实现：服务被中断时无需特殊处理，下次事件继续工作。
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    companion object {
        private val SKIP_TEXTS = listOf("跳过", "跳过广告")
        private const val SKIP_DEBOUNCE_MS = 1_000L
        private const val MISTAP_WINDOW_MS = 2_000L
        private const val MISTAP_DEBOUNCE_MS = 3_000L
        private const val MAX_NODES = 500
    }
}
