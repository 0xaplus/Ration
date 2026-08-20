package com.codewithaplus.appblocker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d("BootReceiver", "Boot completed, restarting foreground monitor service")
        ForegroundMonitorService.ensureRunning(context)
    }
}
