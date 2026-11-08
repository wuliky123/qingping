package com.qingping.app.ui

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.qingping.app.QingpingApp
import com.qingping.app.data.DailyStats
import com.qingping.app.util.InstalledApp
import com.qingping.app.util.getInstalledApps
import com.qingping.app.util.isAccessibilityEnabled
import com.qingping.app.util.isIgnoringBatteryOptimizations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppRow(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val todaySkip: Int,
    val todayMistap: Int,
    val allSkip: Int,
    val allMistap: Int,
    val enabled: Boolean
)

data class HomeState(
    val globalEnabled: Boolean = true,
    val serviceEnabled: Boolean = false,
    val batteryIgnored: Boolean = false,
    val todaySkip: Int = 0,
    val todayMistap: Int = 0,
    val allSkip: Int = 0,
    val allMistap: Int = 0,
    val apps: List<AppRow> = emptyList(),
    val loading: Boolean = true,
    val detailPackage: String? = null,
    val detailLabel: String? = null,
    val detail7Days: List<DailyStats> = emptyList()
)

class QingpingViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as QingpingApp
    private val _state = MutableStateFlow(HomeState(globalEnabled = app.prefs.globalEnabled))
    val state: StateFlow<HomeState> = _state.asStateFlow()

    init {
        refresh()
        // 每 2 秒检测一次无障碍服务是否还开着（用户在系统设置里开关后首页实时变）。
        viewModelScope.launch {
            while (isActive) {
                val enabled = isAccessibilityEnabled(getApplication())
                val battery = isIgnoringBatteryOptimizations(getApplication())
                _state.update {
                    if (it.serviceEnabled != enabled || it.batteryIgnored != battery) {
                        it.copy(serviceEnabled = enabled, batteryIgnored = battery)
                    } else it
                }
                delay(2000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            // 读已安装应用（含取图标）是磁盘 IO，放 IO 线程做。
            val installed: List<InstalledApp> = withContext(Dispatchers.IO) {
                getInstalledApps(getApplication())
            }
            val (todaySkip, todayMistap) = app.statsRepository.todayTotal()
            val (allSkip, allMistap) = app.statsRepository.allTimeTotal()
            val todayMap = app.statsRepository.todayByApp().associateBy { it.packageName }
            val allMap = app.statsRepository.allTimeByApp().associateBy { it.packageName }
            // 有统计但已卸载的 App 也保留一行（名字显示包名），避免数字对不上。
            val knownPkgs = (todayMap.keys + allMap.keys).filter { pkg ->
                installed.none { it.packageName == pkg }
            }
            val rows = installed.map { inst ->
                val t = todayMap[inst.packageName]
                val a = allMap[inst.packageName]
                AppRow(
                    packageName = inst.packageName,
                    label = inst.label,
                    icon = inst.icon,
                    todaySkip = t?.skipCount ?: 0,
                    todayMistap = t?.mistapCount ?: 0,
                    allSkip = a?.skipCount ?: 0,
                    allMistap = a?.mistapCount ?: 0,
                    enabled = app.prefs.isAppEnabled(inst.packageName)
                )
            } + knownPkgs.map { pkg ->
                val t = todayMap[pkg]
                val a = allMap[pkg]
                AppRow(pkg, pkg, null, t?.skipCount ?: 0, t?.mistapCount ?: 0, a?.skipCount ?: 0, a?.mistapCount ?: 0, app.prefs.isAppEnabled(pkg))
            }
            _state.update {
                it.copy(
                    globalEnabled = app.prefs.globalEnabled,
                    serviceEnabled = isAccessibilityEnabled(getApplication()),
                    batteryIgnored = isIgnoringBatteryOptimizations(getApplication()),
                    todaySkip = todaySkip,
                    todayMistap = todayMistap,
                    allSkip = allSkip,
                    allMistap = allMistap,
                    apps = rows.sortedWith(
                        compareByDescending<AppRow> { it.todaySkip + it.todayMistap }
                            .thenBy { it.label }
                    ),
                    loading = false
                )
            }
        }
    }

    fun toggleGlobal(enabled: Boolean) {
        app.prefs.globalEnabled = enabled
        _state.update { it.copy(globalEnabled = enabled) }
    }

    fun toggleApp(packageName: String, enabled: Boolean) {
        app.prefs.setAppEnabled(packageName, enabled)
        _state.update { s ->
            s.copy(apps = s.apps.map { if (it.packageName == packageName) it.copy(enabled = enabled) else it })
        }
    }

    fun openDetail(packageName: String, label: String) {
        viewModelScope.launch {
            val days = app.statsRepository.last7Days(packageName)
            _state.update { it.copy(detailPackage = packageName, detailLabel = label, detail7Days = days) }
        }
    }

    fun closeDetail() {
        _state.update { it.copy(detailPackage = null, detailLabel = null, detail7Days = emptyList()) }
    }
}
