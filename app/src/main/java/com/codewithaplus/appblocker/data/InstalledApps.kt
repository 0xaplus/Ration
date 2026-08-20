package com.codewithaplus.appblocker.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable
)

suspend fun loadLaunchableApps(context: Context): List<InstalledAppInfo> = withContext(Dispatchers.Default) {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    pm.queryIntentActivities(intent, 0)
        .asSequence()
        .map { it.activityInfo.applicationInfo }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .map { appInfo: ApplicationInfo ->
            InstalledAppInfo(
                packageName = appInfo.packageName,
                appName = pm.getApplicationLabel(appInfo).toString(),
                icon = pm.getApplicationIcon(appInfo.packageName)
            )
        }
        .sortedBy { it.appName.lowercase() }
        .toList()
}
