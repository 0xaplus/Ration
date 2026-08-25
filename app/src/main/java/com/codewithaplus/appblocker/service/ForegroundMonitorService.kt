package com.codewithaplus.appblocker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.codewithaplus.appblocker.LockActivity
import com.codewithaplus.appblocker.data.AppDatabase
import com.codewithaplus.appblocker.data.DailyUsage
import com.codewithaplus.appblocker.data.isAccessibilityServiceEnabled
import com.codewithaplus.appblocker.data.todayDateString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

class ForegroundMonitorService : Service() {

    companion object {
        private const val TAG = "ForegroundMonitor"
        private const val NOTIFICATION_CHANNEL_ID = "monitor_channel"
        private const val WARNING_CHANNEL_ID = "warning_channel"
        private const val NOTIFICATION_ID = 1
        private const val WARNING_NOTIFICATION_ID = 2
        private const val PERSIST_INTERVAL_MS = 5_000L

        @Volatile
        private var instance: ForegroundMonitorService? = null

        // startForegroundService() is async: if the service is cold-starting, `instance` is
        // still null for this call. Stash the event so onCreate() can replay it once ready,
        // instead of silently dropping the transition that triggered the cold start.
        @Volatile
        private var pendingEvent: Pair<String, Long>? = null

        fun onForegroundAppChanged(context: Context, packageName: String, timestampMs: Long) {
            val running = instance
            if (running == null) {
                pendingEvent = packageName to timestampMs
                context.startForegroundService(Intent(context, ForegroundMonitorService::class.java))
            } else {
                running.handleForegroundChange(packageName, timestampMs)
            }
        }

        /** Starts the service if it isn't already running, without an associated foreground event. */
        fun ensureRunning(context: Context) {
            if (instance == null) {
                context.startForegroundService(Intent(context, ForegroundMonitorService::class.java))
            }
        }
    }

    private lateinit var db: AppDatabase

    // A genuinely dedicated single OS thread, not a "view" over a shared elastic pool
    // (Dispatchers.IO.limitedParallelism(1) is supposed to guarantee the same mutual exclusion,
    // but proved hard to verify empirically under real device load with many concurrent
    // accessibility events; this removes any ambiguity about the underlying primitive).
    private val singleThreadExecutor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + singleThreadExecutor.asCoroutineDispatcher())

    private var currentTrackedPackage: String? = null
    private var lastPersistedAtMs: Long = 0L
    private var tickerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        db = AppDatabase.getInstance(this)
        startForeground(NOTIFICATION_ID, buildNotification())
        instance = this
        Log.d(TAG, "Foreground monitor service created")

        if (!isAccessibilityServiceEnabled(this)) {
            Log.d(TAG, "Accessibility service is disabled, posting warning notification")
            postAccessibilityWarningNotification()
        }

        pendingEvent?.let { (packageName, timestampMs) ->
            pendingEvent = null
            handleForegroundChange(packageName, timestampMs)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTicker()
        instance = null
        scope.cancel()
        singleThreadExecutor.shutdown()
        super.onDestroy()
    }

    private fun handleForegroundChange(packageName: String, timestampMs: Long) {
        // "Single-threaded dispatcher" only guarantees one coroutine's code is actively
        // running at any instant — it does NOT make a whole suspend-function call chain
        // atomic. If this coroutine suspends (e.g. on a Room DB call) before it has updated
        // currentTrackedPackage/lastPersistedAtMs, a second near-simultaneous event's
        // coroutine can run its own guard check against the stale values and duplicate work
        // (confirmed live: two "Started timing" logs 3ms apart for the same transition).
        // Fix: claim the new state SYNCHRONOUSLY, before any suspension, using values
        // captured up front — so a duplicate/interleaved event immediately sees the update.
        scope.launch {
            if (packageName == currentTrackedPackage) return@launch

            val previousPackage = currentTrackedPackage
            val previousStart = lastPersistedAtMs
            currentTrackedPackage = packageName
            lastPersistedAtMs = timestampMs
            stopTicker()

            if (previousPackage != null) {
                val elapsedSeconds = ((timestampMs - previousStart) / 1000L).toInt()
                if (elapsedSeconds > 0) {
                    persistSeconds(previousPackage, elapsedSeconds)
                }
            }

            val tracked = db.trackedAppDao().getByPackageName(packageName)
            if (tracked != null) {
                ensureTodayRow(packageName)
                val usage = db.dailyUsageDao().getForPackageAndDate(packageName, todayDateString())
                if (usage?.isLockedToday == true) {
                    Log.d(TAG, "Already locked, re-triggering lock: $packageName")
                    // A newer transition may have already superseded our claim while we were
                    // suspended on the DB lookups above — only clear it if we're still current.
                    if (currentTrackedPackage == packageName) currentTrackedPackage = null
                    LockActivity.launch(this@ForegroundMonitorService, packageName)
                } else {
                    if (currentTrackedPackage == packageName) {
                        startTicker()
                        Log.d(TAG, "Started timing: $packageName")
                    }
                }
            } else {
                if (currentTrackedPackage == packageName) currentTrackedPackage = null
            }
        }
    }

    private fun startTicker() {
        tickerJob = scope.launch {
            while (true) {
                kotlinx.coroutines.delay(PERSIST_INTERVAL_MS)
                val pkg = currentTrackedPackage ?: break
                val nowMs = System.currentTimeMillis()
                val elapsedSeconds = ((nowMs - lastPersistedAtMs) / 1000L).toInt()
                if (elapsedSeconds <= 0) continue
                // Advance synchronously, before the suspend DB write below, for the same
                // interleaving reason as above; also carries the sub-second remainder forward
                // instead of discarding it (avoids a systematic undercount over long sessions).
                lastPersistedAtMs += elapsedSeconds * 1000L
                persistSeconds(pkg, elapsedSeconds)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private suspend fun persistSeconds(pkg: String, seconds: Int) {
        val today = todayDateString()
        ensureTodayRow(pkg)
        db.dailyUsageDao().addSeconds(pkg, today, seconds)
        Log.d(TAG, "Persisted ${seconds}s for $pkg")
        checkLimitAndEnforce(pkg, today)
    }

    private suspend fun checkLimitAndEnforce(pkg: String, today: String) {
        val tracked = db.trackedAppDao().getByPackageName(pkg) ?: return
        val usage = db.dailyUsageDao().getForPackageAndDate(pkg, today) ?: return
        if (!usage.isLockedToday && usage.secondsUsedToday >= tracked.dailyLimitSeconds) {
            db.dailyUsageDao().setLocked(pkg, today, true)
            if (currentTrackedPackage == pkg) {
                stopTicker()
                currentTrackedPackage = null
            }
            Log.d(TAG, "Limit breached for $pkg (${usage.secondsUsedToday}s >= ${tracked.dailyLimitSeconds}s), locking")
            LockActivity.launch(this@ForegroundMonitorService, pkg)
        }
    }

    private suspend fun ensureTodayRow(packageName: String) {
        val today = todayDateString()
        val existing = db.dailyUsageDao().getForPackageAndDate(packageName, today)
        if (existing == null) {
            db.dailyUsageDao().insert(
                DailyUsage(
                    packageName = packageName,
                    date = today,
                    secondsUsedToday = 0,
                    isLockedToday = false
                )
            )
        }
    }

    private fun postAccessibilityWarningNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                WARNING_CHANNEL_ID,
                "Permission warnings",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = Notification.Builder(this, WARNING_CHANNEL_ID)
            .setContentTitle("Ration isn't tracking usage")
            .setContentText("Accessibility permission was turned off. Tap to fix.")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(WARNING_NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "App usage monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Ration")
            .setContentText("Monitoring app usage")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .build()
    }
}
