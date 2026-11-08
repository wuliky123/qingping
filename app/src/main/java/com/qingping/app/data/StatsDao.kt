package com.qingping.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 按 App + 日期统计：每天每个 App 一行。
 * skipCount = 成功点“跳过”的次数；mistapCount = 误触跳转后自动返回的次数。
 */
@Entity(tableName = "daily_stats", primaryKeys = ["packageName", "date"])
data class DailyStats(
    val packageName: String,
    val date: String, // yyyy-MM-dd
    val skipCount: Int = 0,
    val mistapCount: Int = 0,
    val lastTime: Long = 0L
)

/** 按 App 的单独开关（默认全开），与统计分开存：无障碍服务里要同步读，SharedPreferences 更合适，见 Prefs。 */
@Dao
interface StatsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(stats: DailyStats)

    @Query("UPDATE daily_stats SET skipCount = skipCount + 1, lastTime = :time WHERE packageName = :pkg AND date = :date")
    suspend fun incSkip(pkg: String, date: String, time: Long)

    @Query("UPDATE daily_stats SET mistapCount = mistapCount + 1, lastTime = :time WHERE packageName = :pkg AND date = :date")
    suspend fun incMistap(pkg: String, date: String, time: Long)

    @Query("SELECT COALESCE(SUM(skipCount), 0) FROM daily_stats WHERE date = :date")
    suspend fun todaySkipTotal(date: String): Int

    @Query("SELECT COALESCE(SUM(mistapCount), 0) FROM daily_stats WHERE date = :date")
    suspend fun todayMistapTotal(date: String): Int

    @Query("SELECT COALESCE(SUM(skipCount), 0) FROM daily_stats")
    suspend fun allTimeSkipTotal(): Int

    @Query("SELECT COALESCE(SUM(mistapCount), 0) FROM daily_stats")
    suspend fun allTimeMistapTotal(): Int

    @Query("SELECT * FROM daily_stats WHERE date = :date ORDER BY skipCount + mistapCount DESC")
    suspend fun todayByApp(date: String): List<DailyStats>

    @Query("SELECT packageName, '' AS date, SUM(skipCount) AS skipCount, SUM(mistapCount) AS mistapCount, MAX(lastTime) AS lastTime FROM daily_stats GROUP BY packageName ORDER BY skipCount + mistapCount DESC")
    suspend fun allTimeByApp(): List<DailyStats>

    @Query("SELECT * FROM daily_stats WHERE packageName = :pkg AND date >= :fromDate ORDER BY date ASC")
    suspend fun lastDays(pkg: String, fromDate: String): List<DailyStats>
}
