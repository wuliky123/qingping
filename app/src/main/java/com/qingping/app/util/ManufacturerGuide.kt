package com.qingping.app.util

/**
 * 保活引导：按品牌给具体路径。
 *
 * 用户机型（2026-11-08 确认）：OPPO Find X8 Pro，ColorOS 16.0.10
 * （软件版本 PKC110_16.0.10.500，16GB+12GB，天玑 9400）。
 * ColorOS 16 对应 Android 16，所以 OPPO / ColorOS 16 排第一，路径按实测写法。
 * 其他品牌放后面作参考。
 */
data class BrandGuide(
    val brand: String,
    val title: String,
    val steps: List<String>,
    val note: String = ""
)

object ManufacturerGuide {
    val guides: List<BrandGuide> = listOf(
        BrandGuide(
            brand = "OPPO",
            title = "OPPO / ColorOS 16（Find X8 Pro 优先）",
            steps = listOf(
                "设置 → 应用管理 → 清屏 → 耗电管理 → 允许后台运行",
                "设置 → 应用管理 → 自启动管理 → 允许清屏自启动",
                "打开最近任务（从底部上滑停住），找到清屏的卡片，下滑卡片点锁形图标，把清屏上锁",
                "电池优化里设为不优化：点本页上方的“忽略电池优化”按钮，按提示选“允许”；或手动进 设置 → 电池 → 应用耗电管理 → 清屏 → 不优化",
                "回到清屏首页，确认“无障碍服务”显示已开启、“保活服务”显示运行中"
            ),
            note = "ColorOS 16 不同小版本菜单名字可能略有差别，找不到时在设置顶部直接搜“自启动”“耗电管理”“电池优化”。"
        ),
        BrandGuide(
            brand = "小米",
            title = "小米 / Redmi（MIUI / 澎湃 OS）",
            steps = listOf(
                "设置 → 应用设置 → 应用管理 → 清屏 → 自启动 → 允许",
                "设置 → 电池与性能 → 应用省电管理 → 清屏 → 无限制",
                "最近任务里长按清屏卡片 → 点锁形图标上锁"
            )
        ),
        BrandGuide(
            brand = "华为",
            title = "华为 / 荣耀（HarmonyOS）",
            steps = listOf(
                "设置 → 应用和服务 → 应用管理 → 清屏 → 允许自启动",
                "设置 → 电池 → 应用耗电管理 → 清屏 → 手动管理 → 打开“允许自启动”“允许关联启动”“允许后台活动”",
                "最近任务里下滑清屏卡片上锁"
            )
        ),
        BrandGuide(
            brand = "vivo",
            title = "vivo / iQOO（OriginOS）",
            steps = listOf(
                "设置 → 应用与权限 → 权限管理 → 自启动 → 允许清屏",
                "设置 → 电池 → 后台耗电管理 → 清屏 → 允许后台高耗电",
                "最近任务里长按清屏卡片上锁"
            )
        ),
        BrandGuide(
            brand = "三星",
            title = "三星（One UI）",
            steps = listOf(
                "设置 → 应用程序 → 清屏 → 电池 → 允许后台活动",
                "设置 → 电池和设备维护 → 电池 → 后台使用限制 → 永不休眠的应用 → 添加清屏",
                "设置 → 应用程序 → 清屏 → 移动数据 → 允许后台数据使用"
            )
        ),
        BrandGuide(
            brand = "其他",
            title = "其他品牌通用",
            steps = listOf(
                "设置里搜“自启动”，把清屏打开",
                "设置里搜“电池优化”，把清屏设为“不优化”",
                "最近任务里把清屏上锁，防止一键清理杀掉"
            ),
            note = "核心就三件事：允许自启动、不限制耗电、任务上锁，缺一个都可能被杀后台。"
        )
    )

    /** 按当前手机品牌挑默认展开的引导：OPPO 优先，其次按品牌名匹配。 */
    fun defaultForThisPhone(): BrandGuide {
        val brand = android.os.Build.BRAND.lowercase()
        val manufacturer = android.os.Build.MANUFACTURER.lowercase()
        val hit = guides.firstOrNull {
            brand.contains(it.brand.lowercase()) || manufacturer.contains(it.brand.lowercase())
        }
        return hit ?: guides.first() // 兜底：OPPO / ColorOS 16
    }
}
