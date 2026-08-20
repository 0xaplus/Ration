package com.codewithaplus.appblocker.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * The 4 permissions from the spec's onboarding flow, in the order they're requested.
 * Usage Access and Overlay aren't actually read by any code path today (the app relies on the
 * Accessibility Service for real-time detection and a full-screen Activity for the lock, per the
 * architecture doc's own reasoning) — they're included because the spec's Step 6 acceptance
 * criterion explicitly calls for all 4 permission screens.
 */
enum class AppPermission(val title: String, val explanation: String, val hint: String) {
    USAGE_ACCESS(
        title = "Usage Access",
        explanation = "Lets Ration read how long you've used each app.",
        hint = "Find \"Ration\" in the list, tap it, then turn on \"Permit usage access\"."
    ) {
        override fun isGranted(context: Context) = isUsageAccessGranted(context)
        override fun settingsIntent(context: Context) = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    },
    ACCESSIBILITY(
        title = "Accessibility",
        explanation = "Lets Ration detect which app is in the foreground in real time, so it can start and stop timers instantly.",
        hint = "Look for \"Ration\" — on most phones it's under a section called \"Installed apps\" or \"Downloaded apps\", not in the main list. Tap it, turn the toggle on, then confirm \"Allow\" on the popup."
    ) {
        override fun isGranted(context: Context) = isAccessibilityServiceEnabled(context)
        override fun settingsIntent(context: Context) = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    },
    OVERLAY(
        title = "Display over other apps",
        explanation = "Lets Ration show the lock screen on top of a blocked app.",
        hint = "Find \"Ration\" in the list and turn its toggle on."
    ) {
        override fun isGranted(context: Context) = isOverlayPermissionGranted(context)
        override fun settingsIntent(context: Context) = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    },
    BATTERY_OPTIMIZATION(
        title = "Ignore battery optimizations",
        explanation = "Prevents the system from stopping Ration's monitoring service in the background.",
        hint = "A popup will appear directly — just tap \"Allow\"."
    ) {
        override fun isGranted(context: Context) = isIgnoringBatteryOptimizations(context)
        override fun settingsIntent(context: Context) = requestIgnoreBatteryOptimizationsIntent(context)
    };

    abstract fun isGranted(context: Context): Boolean
    abstract fun settingsIntent(context: Context): Intent
}
