package com.qingping.app

import android.app.Application
import com.qingping.app.data.Prefs
import com.qingping.app.data.StatsRepository

class QingpingApp : Application() {
    val prefs: Prefs by lazy { Prefs(this) }
    val statsRepository: StatsRepository by lazy { StatsRepository(this) }
}
