package com.qingping.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [DailyStats::class], version = 1, exportSchema = false)
abstract class StatsDatabase : RoomDatabase() {
    abstract fun statsDao(): StatsDao

    companion object {
        @Volatile
        private var INSTANCE: StatsDatabase? = null

        fun get(context: Context): StatsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StatsDatabase::class.java,
                    "qingping_stats.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
