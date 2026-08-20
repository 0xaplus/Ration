package com.codewithaplus.appblocker.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.codewithaplus.appblocker.data.AppDatabase
import com.codewithaplus.appblocker.data.TrackedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Debug-only test harness: lets `adb shell am broadcast` insert a TrackedApp row
 * without needing the App Picker / Set Limit UI (Step 4) or an on-device sqlite3 binary.
 * Only present in the debug build via the src/debug source set.
 */
class DebugTrackedAppReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val packageName = intent.getStringExtra("packageName") ?: return
        val appName = intent.getStringExtra("appName") ?: packageName
        val dailyLimitSeconds = intent.getIntExtra("dailyLimitSeconds", 3600)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getInstance(context).trackedAppDao().insert(
                    TrackedApp(
                        packageName = packageName,
                        appName = appName,
                        dailyLimitSeconds = dailyLimitSeconds,
                        createdAt = System.currentTimeMillis()
                    )
                )
                Log.d("DebugTrackedAppReceiver", "Inserted TrackedApp: $packageName limit=${dailyLimitSeconds}s")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
