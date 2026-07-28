# Samsung Galaxy A26 5G (SM-A266B/DS) - Device Profile

## Core Software Environment
- **Operating System:** Android 16
- **Custom Skin / SDK Layer:** Samsung One UI 8.5
- **Target API Level:** API Level 36 (Android 16 baseline)
- **System Update Baseline:** July 2026

## Hardware Profile
- **Chipset / SoC:** Samsung Exynos 1380 (5 nm)
- **CPU Architecture:** Octa-core (4x2.4 GHz Cortex-A78 & 4x2.0 GHz Cortex-A55)
- **GPU:** Mali-G68 MP5
- **Display:** 6.7-inch Super AMOLED, 1080 x 2340 (FHD+), 120Hz
- **Camera:** Rear 50MP (OIS) + 8MP UW + 2MP Macro, Front 13MP
- **Biometrics:** Side-mounted fingerprint + 2D face recognition
- **Ports:** USB-C 2.0 (OTG), no 3.5mm jack

## Critical Architecture & Optimization Rules

### UI/UX Design Constraints
- **Camera Cutout:** Top-center teardrop notch - ensure status bar padding
- **One UI 8.5:** Bottom-heavy interaction zones for one-handed use
- **Refresh Rate:** 120Hz - target 120fps fluid rendering

### Performance (Exynos 1380)
- Mid-range: handles standard ops, throttles to ~70-80% under sustained multi-threaded load
- Keep heavy tasks brief/offloaded
- **No heavy on-device LLMs/GenAI** - use cloud APIs (Firebase, Gemini)

### Connectivity & Sensors
- 5G/LTE, dual-band Wi-Fi 5 (802.11ac)
- USB-C 2.0 with OTG

## Step Counter Implications (Android 16 + One UI 8.5)

### Required Manifest Changes
```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />
<!-- Update StepCounterService: -->
android:foregroundServiceType="health"
```

### Battery Optimization (One UI 8.5)
- "Auto Start" → **Settings → Battery → Background Usage Limits → "Never sleeping apps"**
- OR **Settings → Apps → [App] → Battery → "Unrestricted"**
- App must guide users to correct location

### Sensor Requirements
- Primary: `Sensor.TYPE_STEP_COUNTER` (hardware)
- Fallback: `Sensor.TYPE_STEP_DETECTOR` + manual counting
- Some A-series delegate to Samsung Health

### Notification (Android 16)
- `IMPORTANCE_MIN` may be hidden
- Use `IMPORTANCE_LOW` or `DEFAULT` for ongoing step notification
- Must be non-removable while service runs

### Sensor Permissions (Android 16)
- `BODY_SENSORS` may require runtime grant
- `ACTIVITY_RECOGNITION` for step detection fallback