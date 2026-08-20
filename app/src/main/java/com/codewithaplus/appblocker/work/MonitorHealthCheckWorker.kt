package com.codewithaplus.appblocker.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.codewithaplus.appblocker.service.ForegroundMonitorService
import java.util.concurrent.TimeUnit

/**
 * Failsafe for the case where the OS kills the foreground service outright (Doze/App Standby
 * on unexempted devices) without the Accessibility Service or boot receiver getting a chance
 * to restart it. WorkManager's periodic minimum is 15 minutes, matching the spec's floor.
 */
class MonitorHealthCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ForegroundMonitorService.ensureRunning(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "monitor_health_check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonitorHealthCheckWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
