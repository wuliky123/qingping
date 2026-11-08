package com.qingping.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.qingping.app.service.KeepAliveService
import com.qingping.app.ui.theme.QingpingTheme
import com.qingping.app.util.ManufacturerGuide
import com.qingping.app.util.openAccessibilitySettings
import com.qingping.app.util.openAppDetailsSettings
import com.qingping.app.util.openAutoStartSettings
import com.qingping.app.util.openBatteryOptimizationSettings
import com.qingping.app.util.requestIgnoreBatteryOptimizations

class MainActivity : ComponentActivity() {

    private val viewModel: QingpingViewModel by viewModels()

    // Android 13+ 发通知要先申请 POST_NOTIFICATIONS 权限（常驻保活通知就靠它）。
    // 用户拒绝也不崩，只是通知发不出来、保活效果打折。
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // 打开 App 就拉起保活前台服务。
        KeepAliveService.start(this)
        setContent {
            QingpingTheme {
                QingpingScreen(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QingpingScreen(viewModel: QingpingViewModel) {
    var tab by remember { mutableIntStateOf(0) }
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("首页") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("保活设置") })
        }
        if (tab == 0) {
            HomeTab(viewModel, state)
        } else {
            KeepAliveTab(state)
        }
    }

    // 点某个 App 弹 7 天简表
    if (state.detailPackage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.closeDetail() },
            title = { Text("${state.detailLabel} · 最近 7 天") },
            text = {
                Column {
                    state.detail7Days.forEach { d ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(d.date)
                            Text("跳过 ${d.skipCount} 次 · 误触 ${d.mistapCount} 次")
                        }
                        Divider()
                    }
                    if (state.detail7Days.all { it.skipCount == 0 && it.mistapCount == 0 }) {
                        Spacer(Modifier.height(8.dp))
                        Text("这 7 天还没有拦截记录，打开这个 App 出现开屏广告时会自动记。")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.closeDetail() }) { Text("关闭") }
            }
        )
    }
}

@Composable
fun HomeTab(viewModel: QingpingViewModel, state: HomeState) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("总开关", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = state.globalEnabled,
                            onCheckedChange = { viewModel.toggleGlobal(it) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (state.serviceEnabled) "● 无障碍服务：已开启，正在帮你跳过广告"
                        else "○ 无障碍服务：未开启，点下面按钮去打开（必须手动开，代码开不了）",
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    if (!state.serviceEnabled) {
                        Button(onClick = { openAccessibilitySettings(context) }) {
                            Text("去开启无障碍服务")
                        }
                    }
                }
            }
        }
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("今日跳过", "${state.todaySkip}", Modifier.weight(1f))
                StatCard("今日误触拦截", "${state.todayMistap}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("累计跳过", "${state.allSkip}", Modifier.weight(1f))
                StatCard("累计误触拦截", "${state.allMistap}", Modifier.weight(1f))
            }
        }
        item {
            Text("按 App 统计（点一行看最近 7 天）", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("只列出有拦截记录的和已安装的 App，关掉某个 App 的开关后就不对它跳广告。", fontSize = 13.sp)
        }
        if (state.loading) {
            item { CircularProgressIndicator() }
        } else {
            items(state.apps.filter { it.todaySkip + it.todayMistap + it.allSkip + it.allMistap > 0 || it.enabled }) { row ->
                AppRowCard(row, onToggle = { viewModel.toggleApp(row.packageName, it) }) {
                    viewModel.openDetail(row.packageName, row.label)
                }
            }
        }
    }
}

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 13.sp)
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AppRowCard(row: AppRow, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(row.icon, Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(row.label, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    "今日 ${row.todaySkip} 次 · 累计 ${row.allSkip} 次" +
                        if (row.todayMistap + row.allMistap > 0) " · 误触 ${row.todayMistap}/${row.allMistap}" else "",
                    fontSize = 13.sp
                )
            }
            Switch(checked = row.enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
fun AppIcon(icon: Drawable?, modifier: Modifier = Modifier) {
    val bitmap = remember(icon) {
        icon?.let { drawableToBitmap(it) }?.asImageBitmap()
    }
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
    } else {
        Spacer(modifier = modifier)
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) return drawable.bitmap
    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    return bitmap
}

@Composable
fun KeepAliveTab(state: HomeState) {
    val context = LocalContext.current
    var expandedBrand by remember { mutableStateOf(ManufacturerGuide.defaultForThisPhone().brand) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("保活设置", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "安卓厂商杀后台很凶，只装 App 不设置，重启、清后台、开省电后服务都会停。" +
                    "下面四件事都做了，存活率才高。无障碍服务掉了只能手动重开，代码开不了。",
                fontSize = 14.sp
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("① 无障碍服务：${if (state.serviceEnabled) "已开启" else "未开启"}", fontWeight = FontWeight.Bold)
                    Button(onClick = { openAccessibilitySettings(context) }) { Text("去系统设置里开启") }
                    Divider()
                    Text("② 电池优化：${if (state.batteryIgnored) "已设为不优化" else "未设置"}", fontWeight = FontWeight.Bold)
                    Button(onClick = { requestIgnoreBatteryOptimizations(context) }) { Text("设为不优化") }
                    OutlinedButton(onClick = { openBatteryOptimizationSettings(context) }) { Text("手动进电池优化列表") }
                    Divider()
                    Text("③ 自启动与耗电管理", fontWeight = FontWeight.Bold)
                    Button(onClick = { openAutoStartSettings(context) }) { Text("打开自启动管理（OPPO）") }
                    OutlinedButton(onClick = { openAppDetailsSettings(context) }) { Text("打开清屏的应用详情页") }
                    Text("④ 最近任务上锁：从底部上滑停住打开最近任务，找到清屏卡片，下滑点锁形图标。", fontSize = 14.sp)
                }
            }
        }
        item {
            Text("各品牌具体路径（点一行展开）", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("当前手机默认展开：${ManufacturerGuide.defaultForThisPhone().title}", fontSize = 13.sp)
        }
        items(ManufacturerGuide.guides) { guide ->
            val expanded = expandedBrand == guide.brand
            Card(
                modifier = Modifier.fillMaxWidth().clickable { expandedBrand = if (expanded) "" else guide.brand }
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(guide.title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    if (expanded) {
                        Spacer(Modifier.height(8.dp))
                        guide.steps.forEachIndexed { i, step ->
                            Text("${i + 1}. $step", fontSize = 14.sp)
                            Spacer(Modifier.height(4.dp))
                        }
                        if (guide.note.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(guide.note, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        item {
            Text(
                "注意：ColorOS 16 对应 Android 16。清屏 targetSdk 保持 34，" +
                    "前台服务用 specialUse 类型，常驻通知需要通知权限，" +
                    "首次打开 App 时会申请，拒绝也能用，只是保活效果打折。",
                fontSize = 13.sp
            )
        }
    }
}
