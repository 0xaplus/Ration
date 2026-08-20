# App Blocker — Full Spec (Phase 1: Android MVP)

**Scope note:** This spec covers Phase 1 only (Android native MVP, per the build order in the architecture doc). iOS and cross-platform layers come later, once this is working and tested on your S24U. Not in scope for this spec: backend, accounts, sync, subscriptions, ads.

---

## 0. Non-Goals (explicit, so the agent doesn't add scope)
- No "snooze," "skip," or "5 more minutes" bypass button anywhere. That defeats the app's purpose.
- No multiple profiles/users.
- No per-day-of-week limit variation — same limit every day for v1.
- No website-level blocking (browser tabs), only app-level.
- No cross-device sync, no backend, no login.
- No subscription/paywall UI yet.

---

## 1. Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Local DB:** Room
- **Async:** Kotlin Coroutines + Flow
- **Prefs:** DataStore (for simple flags like onboarding-complete)
- **minSdk:** 26 · **targetSdk:** latest stable (35/36 depending on current AGP/SDK at build time)
- **Package name:** `com.<yourname>.appblocker` (agent should ask or pick a placeholder)

---

## 2. Screens

| Screen | Purpose |
|---|---|
| Onboarding | Sequential permission requests, one per screen, each explaining *why* before deep-linking to the system settings page |
| Dashboard (Home) | List of tracked apps: icon, name, "used X / limit Y today," progress bar. "+ Add app" button. Banner if a required permission has been revoked. |
| App Picker | List of installed launchable apps (icon + name), multi-select to add to tracking |
| Set Limit | Per selected app, pick daily limit in minutes (5–240, step of 5) |
| Settings | Edit/remove tracked apps, change limits, view permission status |
| Lock Screen | Full-screen, non-dismissible via back button. Shows blocked app's icon, "Daily limit reached," countdown to next reset (local midnight). **No buttons that unlock or skip.** |

---

## 3. Data Model (Room)

```kotlin
@Entity
data class TrackedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val dailyLimitSeconds: Int,
    val createdAt: Long
)

@Entity
data class DailyUsage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val date: String,          // "yyyy-MM-dd", local timezone
    val secondsUsedToday: Int,
    val isLockedToday: Boolean
)
```
Global flags (onboarding complete, etc.) go in DataStore, not Room.

---

## 4. Core Modules

### 4.1 Accessibility Service
- Extends `AccessibilityService`, listens for `TYPE_WINDOW_STATE_CHANGED`.
- On event: read `event.packageName`.
- If it's a tracked package → notify the monitor that this app is now foregrounded (with timestamp).
- If foreground moves away from a tracked package (home screen, another app, **or this app's own lock screen** — exclude self package explicitly) → stop the timer and flush elapsed seconds to Room immediately.
- No periodic-only flushing — flush on every transition, so a fast app-switch doesn't lose time.

### 4.2 Foreground Monitor Service
- `ForegroundService` (persistent notification required by Android).
- Holds in-memory state: currently-foregrounded tracked app + start timestamp.
- On each Accessibility event (not on a slow poll), recompute elapsed time and compare `secondsUsedToday + elapsed` against `dailyLimitSeconds`.
- Persist `secondsUsedToday` to Room at least every 5–10 seconds and on every app-switch (covers process death).
- When limit is crossed: set `isLockedToday = true` for that package, launch the Lock Activity (`FLAG_ACTIVITY_NEW_TASK`).

### 4.3 Lock Enforcement
- Once `isLockedToday = true` for a package, **every subsequent foreground event for that package re-triggers the Lock Activity immediately** — this is what makes it non-bypassable via home-button-and-reopen, not a one-time interstitial.
- Midnight reset: compare `DailyUsage.date` to today's date. Reset `secondsUsedToday = 0`, `isLockedToday = false` when the date has changed. This check runs on: app foreground, service start, **and** a backup exact alarm scheduled for the next local midnight (`setExactAndAllowWhileIdle` or `setAlarmClock` — plain `AlarmManager.set()` is not reliable enough under Doze).

### 4.4 Boot Persistence
- `BroadcastReceiver` on `BOOT_COMPLETED` restarts the Foreground Monitor Service.
- On every service start, re-verify Accessibility Service is still enabled; if not, surface a notification prompting the user back into Settings.

### 4.5 Permission Flow
Request these one at a time, each with a short explanation screen before the deep link, and a check-on-resume to confirm it was actually granted:
1. Usage Access — `Settings.ACTION_USAGE_ACCESS_SETTINGS`
2. Accessibility — `Settings.ACTION_ACCESSIBILITY_SETTINGS`
3. Display over other apps — `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`
4. Ignore battery optimizations — `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`

Dashboard shows a persistent warning banner if any of these is later revoked, since the app silently stops working without it.

---

## 5. Non-Functional Requirements
- **Battery:** event-driven, not tight-loop polling; target negligible background drain.
- **Reliability:** `START_STICKY` on the foreground service, plus a `WorkManager` periodic task (~every 15 min, the practical minimum) as a failsafe that re-syncs state and restarts the service if it's dead.
- **Privacy:** fully local for this phase — no network calls, no data leaves the device.

---

## 6. Build Order & Acceptance Criteria

| Step | Deliverable | Done when |
|---|---|---|
| 1 | Accessibility Service that logs foreground package changes to Logcat | You switch apps on the S24U and see correct package names logged in real time |
| 2 | Room DB + timer logic wired to the service | Opening a tracked app for N seconds increases `secondsUsedToday` correctly, confirmed by reading the DB |
| 3 | Lock Activity triggered on limit breach | Manually set a 1-minute limit, use the app for 60s, lock screen appears immediately, reappears if you back out and reopen the app |
| 4 | Settings/App Picker/Set Limit UI | You can add an app, set a limit, and see it reflected on the dashboard without touching the DB manually |
| 5 | Boot receiver + battery optimization handling | Reboot the phone, confirm tracking resumes without reopening the app |
| 6 | Full onboarding + permission banners | Fresh install walks through all 4 permissions; revoking one later shows the dashboard banner |

Hand these to the agent **one at a time**, in order — confirm each works on-device before moving to the next.