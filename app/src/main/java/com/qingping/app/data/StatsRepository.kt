package com.qingping.app.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 日期工具：统计按 yyyy-MM-dd 分天。 */
object DateUtils {
    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun today(): String = fmt.format(Date())

    fun daysAgo(n: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -n)
        return fmt.format(cal.time)
    }

    /** 最近 7 天（含今天），供 7 天简表用。 */
    fun last7Days(): List<String> = (6 downTo 0).map { daysAgo(it) }
}

/**
 * 统计仓库：跳过 / 误触各记一次。
 * 调用方先 insertIfAbsent 再自增，保证“当天该 App 第一行”存在。
 */
class StatsRepository(context: Context) {
    private val dao = StatsDatabase.get(context).statsDao()

    suspend fun recordSkip(packageName: String) {
        val date = DateUtils.today()
        val now = System.currentTimeMillis()
        dao.insertIfAbsent(DailyStats(packageName, date, 0, 0, now))
        dao.incSkip(packageName, date, now)
    }

    suspend fun recordMistap(packageName: String) {
        val date = DateUtils.today()
        val now = System.currentTimeMillis()
        dao.insertIfAbsent(DailyStats(packageName, date, 0, 0, now))
        dao.incMistap(packageName, date, now)
    }

    suspend fun todayTotal(): Pair<Int, Int> {
        val date = DateUtils.today()
        return dao.todaySkipTotal(date) to dao.todayMistapTotal(date)
    }

    suspend fun allTimeTotal(): Pair<Int, Int> {
        return dao.allTimeSkipTotal() to dao.allTimeMistapTotal()
    }

    suspend fun todayByApp(): List<DailyStats> = dao.todayByApp(DateUtils.today())

    suspend fun allTimeByApp(): List<DailyStats> = dao.allTimeByApp()

    /** 某 App 最近 7 天：返回按日期对齐的列表，缺失的日期补 0。 */
    suspend fun last7Days(packageName: String): List<DailyStats> {
        val days = DateUtils.last7Days()
        val rows = dao.lastDays(packageName, days.first()).associateBy { it.date }
        return days.map { d -> rows[d] ?: DailyStats(packageName, d, 0, 0, 0L) }
    }
}
