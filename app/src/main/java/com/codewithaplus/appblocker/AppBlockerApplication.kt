package com.codewithaplus.appblocker

import android.app.Application
import com.codewithaplus.appblocker.work.MonitorHealthCheckWorker

class AppBlockerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MonitorHealthCheckWorker.schedule(this)
    }
}
