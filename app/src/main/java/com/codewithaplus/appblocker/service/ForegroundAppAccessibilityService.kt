package com.codewithaplus.appblocker.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class ForegroundAppAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "ForegroundAppService"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        Log.d(TAG, "Foreground app changed: $packageName")
        ForegroundMonitorService.onForegroundAppChanged(applicationContext, packageName, System.currentTimeMillis())
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")
    }
}
