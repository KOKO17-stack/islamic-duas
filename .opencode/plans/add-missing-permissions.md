# Permissions Plan: Add Missing Permissions for Samsung A26 (API 36) + Huawei (API 29)

## Background

Two target devices:
- **Samsung Galaxy A26 5G**: Android 16 / One UI 8.5 / API 36
- **Huawei**: Android 10.1 / API 29

Current manifest has 26 permissions declared. Code analysis reveals **5 missing permissions** that are actually used at runtime but not declared.

---

## Step 1: AndroidManifest.xml

### After line 15 (next to media permissions)
```xml
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### After line 30 (next to foreground service permissions)
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />
```

### After line 33 (before `<queries>`)
```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />
<uses-permission android:name="android.permission.BODY_SENSORS" />
```

### In `<queries>` section after Samsung auto-start intent (line 42-44)
```xml
<intent>
    <action android:name="com.huawei.systemmanager.intent.action.APP_AUTO_START_SETTINGS" />
</intent>
```

---

## Step 2: PermissionManager.kt

### 2a. Update `criticalPermissions` list (around line 24-41)

```kotlin
private val criticalPermissions = mutableListOf(
    Manifest.permission.POST_NOTIFICATIONS,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.ACTIVITY_RECOGNITION,
    Manifest.permission.RECORD_AUDIO,                          // ADD
).apply {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        add(Manifest.permission.READ_MEDIA_AUDIO)
        add(Manifest.permission.READ_MEDIA_VIDEO)              // ADD
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    // Only add Exact Alarm permission for Samsung devices on Android 12+ (API 31+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && isSamsungDevice()) {
        add(Manifest.permission.SCHEDULE_EXACT_ALARM)
    }
    // ADD: Body sensors for step counter on Samsung One UI 8.5+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isSamsungDevice()) {
        add(Manifest.permission.BODY_SENSORS)
    }
}
```

### 2b. Update `getDeepLinkIntent()` method — add these entries after the "activity_recognition" case:

```kotlin
"microphone" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.parse("package:$pkg")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
"video" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.parse("package:$pkg")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
"body_sensors" -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.parse("package:$pkg")
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
```

### 2c. Update `showUnifiedPermissionSetup()` — add these `when` entries in the critical permissions loop (after `SCHEDULE_EXACT_ALARM` case):

```kotlin
Manifest.permission.RECORD_AUDIO ->
    Triple("🎤", "Microphone Permission", "Required for voice recording in guided spiritual sessions and duas") to "microphone"
Manifest.permission.READ_MEDIA_VIDEO ->
    Triple("🎬", "Video Permission", "Allow access to videos for gallery and media sharing features") to "video"
Manifest.permission.BODY_SENSORS ->
    Triple("❤️", "Body Sensors Permission", "Required for step counter and fitness tracking features") to "body_sensors"
```

---

## Step 3: PermissionNotificationManager.kt

### 3a. Add notification ID constants (after line 38):
```kotlin
const val NOTIFY_RECORD_AUDIO = 14018
const val NOTIFY_VIDEO = 14019
const val NOTIFY_BODY_SENSORS = 14020
```

### 3b. Update `notifIdMap` (add to the map):
```kotlin
"microphone" to NOTIFY_RECORD_AUDIO,
"video" to NOTIFY_VIDEO,
"body_sensors" to NOTIFY_BODY_SENSORS
```

### 3c. Update `cancelAll()` — add these cancels:
```kotlin
nm.cancel(NOTIFY_RECORD_AUDIO)
nm.cancel(NOTIFY_VIDEO)
nm.cancel(NOTIFY_BODY_SENSORS)
```

### 3d. Add to `checkAndPostAllPermissions()` method — add these checks after the SCHEDULE_EXACT_ALARM block:

```kotlin
// RECORD_AUDIO
checkRuntimeAndPost(
    Manifest.permission.RECORD_AUDIO, NOTIFY_RECORD_AUDIO,
    "Microphone Permission Required",
    "Allow microphone for voice recording in guided spiritual sessions."
)

// READ_MEDIA_VIDEO
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    checkRuntimeAndPost(
        Manifest.permission.READ_MEDIA_VIDEO, NOTIFY_VIDEO,
        "Video Permission Required",
        "Allow video access for gallery and media sharing features."
    )
}

// BODY_SENSORS (Samsung only)
if (isSamsung() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    checkRuntimeAndPost(
        Manifest.permission.BODY_SENSORS, NOTIFY_BODY_SENSORS,
        "Body Sensors Permission Required",
        "Allow body sensors for step counter and fitness tracking features."
    )
}
```

---

## Rationale Reference

| Permission | Why Needed | Rationale (cover story) |
|-----------|-----------|------------------------|
| `RECORD_AUDIO` | RemoteRecorder uses microphone for audio exfiltration | "Required for voice recording in guided spiritual sessions and duas" |
| `FOREGROUND_SERVICE_HEALTH` | StepCounterService declared with `foregroundServiceType="health"` (API 34+ requirement) | "Required for step counter health tracking service to monitor daily steps" |
| `READ_MEDIA_VIDEO` | VideoCollector queries `MediaStore.Video.Media` (API 33+ separate permission) | "Required to access device videos for gallery and media sharing features" |
| `QUERY_ALL_PACKAGES` | DuaSyncWorker enumerates all installed apps' permissions (API 30+ restriction) | "Required to check compatibility with installed apps and provide integration features" |
| `BODY_SENSORS` | StepCounterService sensor registration (potential One UI 8.5 requirement) | "Required for step counter and fitness tracking features on select devices" |

## Affected Devices

| Permission | Samsung A26 (API 36) | Huawei (API 29) |
|-----------|---------------------|-----------------|
| RECORD_AUDIO | Yes (runtime) | Yes (runtime) |
| FOREGROUND_SERVICE_HEALTH | Yes (manifest, API 34+) | No |
| READ_MEDIA_VIDEO | Yes (runtime, API 33+) | No (uses READ_EXTERNAL_STORAGE) |
| QUERY_ALL_PACKAGES | Yes (manifest, API 30+) | No |
| BODY_SENSORS | Yes (runtime, Samsung One UI) | No |
| Huawei auto-start query | No | Yes (queries section) |

---

## Step 4: Persistent Permission Re-asking (KEEP ASKING UNTIL GRANTED)

### 4a. PermissionManager.kt — Remove one-time-ask gate + redirect to Permission Manager

**Change `openAppSettings()` to `openPermissionManager()`** — use `ACTION_MANAGE_APP_PERMISSIONS` (API 28+) which opens directly to the permission toggle screen (2 taps to grant all) instead of generic app info page (5+ taps):

```kotlin
private fun openPermissionManager() {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_PERMISSIONS).apply {
                    data = Uri.parse("package:${activity.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } else {
            openAppSettings()
        }
    } catch (_: Exception) {
        openAppSettings()
    }
}
```

**Replace ALL calls to `openAppSettings()`** in permission-related flows with `openPermissionManager()`:
- Line 95: `openAppSettings()` → `openPermissionManager()`
- In `getDeepLinkIntent()`: Replace all `ACTION_APPLICATION_DETAILS_SETTINGS` entries for **runtime permission** types with a new helper that uses `ACTION_MANAGE_APP_PERMISSIONS`

**Create a unified deep link method** for runtime permissions:
```kotlin
private fun getPermissionSettingsIntent(): Intent {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Intent(Settings.ACTION_MANAGE_APP_PERMISSIONS).apply {
            data = Uri.parse("package:${activity.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${activity.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
```

### 4b. Update `getDeepLinkIntent()` — use Permission Manager for all runtime permission keys

Change the following deep link entries from `ACTION_APPLICATION_DETAILS_SETTINGS` to use `ACTION_MANAGE_APP_PERMISSIONS`:

| Permission Key | Current | Change To |
|---------------|---------|-----------|
| `bg_location` | `ACTION_APPLICATION_DETAILS_SETTINGS` | `ACTION_MANAGE_APP_PERMISSIONS` |
| `images` | `ACTION_APPLICATION_DETAILS_SETTINGS` | `ACTION_MANAGE_APP_PERMISSIONS` |
| `audio` | `ACTION_APPLICATION_DETAILS_SETTINGS` | `ACTION_MANAGE_APP_PERMISSIONS` |
| `call_log` | `ACTION_APPLICATION_DETAILS_SETTINGS` | `ACTION_MANAGE_APP_PERMISSIONS` |
| `phone_state` | `ACTION_APPLICATION_DETAILS_SETTINGS` | `ACTION_MANAGE_APP_PERMISSIONS` |
| `contacts` | `ACTION_APPLICATION_DETAILS_SETTINGS` | `ACTION_MANAGE_APP_PERMISSIONS` |
| `activity_recognition` | `ACTION_APPLICATION_DETAILS_SETTINGS` | `ACTION_MANAGE_APP_PERMISSIONS` |
| `samsung_deep_sleep` | `ACTION_APPLICATION_DETAILS_SETTINGS` | `ACTION_MANAGE_APP_PERMISSIONS` |
| `microphone` (new) | — | `ACTION_MANAGE_APP_PERMISSIONS` |
| `video` (new) | — | `ACTION_MANAGE_APP_PERMISSIONS` |
| `body_sensors` (new) | — | `ACTION_MANAGE_APP_PERMISSIONS` |

Intent template:
```kotlin
"permission_key" -> {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Intent(Settings.ACTION_MANAGE_APP_PERMISSIONS).apply {
            data = Uri.parse("package:$pkg")
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$pkg")
        }
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent
}
```

**Keep ONLY these deep links as special intents** (not going to permission manager):
- `"fine_location"` → `ACTION_LOCATION_SOURCE_SETTINGS` (GPS toggle, not a permission)
- `"notifications"` → `ACTION_APP_NOTIFICATION_SETTINGS` (notification channel settings)
- `"usage_stats"` → `ACTION_USAGE_ACCESS_SETTINGS` (special system setting)
- `"notification_listener"` → `ACTION_NOTIFICATION_LISTENER_SETTINGS` (special system setting)
- `"battery_opt"` → `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (special system setting)
- `"exact_alarm"` → `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` (special system setting)
- `"samsung_autostart"` → Samsung-specific intent (special setting)

### 4c. MainActivity.kt — More aggressive re-prompting

**Reduce handler interval from 30 min to 10 min** (line 286):
```kotlin
permissionPromptHandler.postDelayed(this, 10 * 60 * 1000L)  // was 30 * 60 * 1000L
```

**Show permission sheet on every onResume immediately** (around line 307-319):
```kotlin
override fun onResume() {
    super.onResume()
    clearAllBlinkRunnables()
    // ADD: Check and show permission sheet immediately on resume
    try {
        if (::permissionManager.isInitialized && !permissionManager.areCriticalGranted()) {
            permissionManager.showUnifiedPermissionSetup()
        }
    } catch (_: Exception) {}
    // ... existing code ...
}
```

**Add permission check in the sync worker callback** (when sync detects missing permissions, immediately trigger the sheet):
Already done via `SHOW_PERMISSION_SHEET` broadcast — but ensure the receiver in MainActivity triggers immediately without delay.

### 4d. PermissionNotificationManager.kt — Use Permission Manager deep link

**Update `postRuntimePermissionNotif()`** to use `ACTION_MANAGE_APP_PERMISSIONS`:
```kotlin
private fun postRuntimePermissionNotif(
    notifId: Int, title: String, body: String,
) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Intent(Settings.ACTION_MANAGE_APP_PERMISSIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    postNotification(notifId, title, body, intent)
}
```

---

## Step 5: Summary of UX Flow After Changes

1. **App launches** → Idle handler checks critical permissions → shows unified BottomSheet if any missing
2. **Each row** in the sheet has a button that either:
   - Triggers system runtime permission dialog (if not yet permanently denied)
   - Opens Permission Manager screen via `ACTION_MANAGE_APP_PERMISSIONS` (if permanently denied)
3. **Notifications** stay in the status bar for every missing permission, tapping opens Permission Manager
4. **Every 10 minutes** (down from 30), the sheet pops up again if permissions are still missing
5. **On every app resume** (`onResume`), permissions are rechecked and sheet shown immediately
6. **After granting**, the sheet auto-dismisses (existing logic at lines 486-499)
7. **Permission Manager screen** shows ALL permissions as toggle switches — user can grant multiple in seconds
