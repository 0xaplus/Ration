package com.codewithaplus.appblocker.data

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import com.codewithaplus.appblocker.service.ForegroundAppAccessibilityService

fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expected = "${context.packageName}/${ForegroundAppAccessibilityService::class.java.name}"
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}

fun isUsageAccessGranted(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.unsafeCheckOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}

fun isOverlayPermissionGranted(context: Context): Boolean =
    Settings.canDrawOverlays(context)
