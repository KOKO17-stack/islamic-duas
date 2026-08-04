# Agent Onboarding Guide

## Critical Rules (Must Follow)

### 1. NO AUTOMATIC FILE DELETION
Never delete any files or large code blocks unless explicitly and directly instructed by the user. Only modify files directly relevant to the task at hand. Do not touch unrelated files.

### 2. NON-COMMERCIAL, PERSONAL USE ONLY
This app is for personal testing on the user's own devices only. It must never be published to any app store, and no app-store-imposed restrictions apply. Do not code as if this app needs to pass store review.

### 3. INSTRUCTION SPECIFICITY
Every instruction, code change, or step must be so precise and unambiguous that even the simplest coder or coding model can execute it without failure or deviation. No vague steps, no assumptions, no skipped details.

### 4. DEVICE PROFILES

#### Primary: Samsung Galaxy A26 5G (SM-A266B/DS)
- **Android 16, One UI 8.5**
- **CRITICAL: NOT USB debuggable.** Cannot be connected via ADB/USB debugging.
- APK must be transferred via WhatsApp and installed manually on the device.
- Must always be 100% correct — no room for error since remote debugging is impossible.
- Refer to `DEVICE_PROFILE_A26.md` for full hardware/software specifications.

#### Secondary: Huawei (Android 10)
- Always available via USB debugging and physically connected to the development machine.
- Use for initial testing and debugging before deploying to the Samsung A26.

### 5. DEPLOYMENT WORKFLOW
1. Build APK locally.
2. Transfer APK via WhatsApp to Samsung A26.
3. Install APK manually on Samsung A26.
4. Use Huawei (USB debugging) for development testing before deploying to Samsung.

### 5aa. BUILD BOTH APKS AFTER EVERY CODE CHANGE (MANDATORY)
After ANY app code change, ALWAYS build BOTH debug and release APKs before finishing:
```bash
cd /Users/apple/Documents/islamic-duas
./gradlew :app:assembleDebug :app:assembleRelease
```
- Outputs: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk`
- Debug APK → Huawei (USB testing). Release APK → Samsung A26 (via WhatsApp).
- Never finish a session with only one APK built, and never skip building both when app code changed.

### 5a. SOURCE CODE SYNC WORKFLOW (manual — not automatic)
After any local code changes, you MUST explicitly sync to GitHub:
```bash
cd /Users/apple/Documents/islamic-duas
git add -A
git commit -m "describe your changes"
git push
```

### 5b. FILES NOT TRACKED (must restore manually after fresh clone)
| File | Why | Restore from |
|------|-----|--------------|
| `.env` | Secrets (GITHUB_TOKEN, Firebase config) | Re-create with token |
| `app/src/main/assets/service-account.json` | Firebase credentials | Firebase Console → Service Accounts |
| `app/src/main/assets/quran_full.json` | 4.3MB (GitHub file size limit) | Re-download or remove reference |

### 6. VIEWER SYNC MANDATORY RULE — DASHBOARD IS THE SOURCE OF TRUTH

**The gh-pages repo (`/tmp/devicesync-viewer/`) is the authoritative source for the dashboard.**
The project file `viewer-index.html` is only a REFERENCE COPY pulled FROM the dashboard, never pushed TO it.

**Edit workflow (correct direction):**
1. Edit `/tmp/devicesync-viewer/index.html` directly (this is the real dashboard)
2. Push to gh-pages:
   ```bash
   cd /tmp/devicesync-viewer && git add -A && git commit -m "describe changes" && git push
   ```
3. Sync to project (after push, NOT before):
   ```bash
   cp /tmp/devicesync-viewer/index.html /Users/apple/Documents/islamic-duas/viewer-index.html
   ```

**CRITICAL — NEVER do this (direction is wrong):**
```bash
# WRONG! This overwrites the live dashboard with a potentially stale copy:
cp /Users/apple/Documents/islamic-duas/viewer-index.html /tmp/devicesync-viewer/index.html
```

**CRITICAL — NEVER skip the gh-pages repo:**
The project's `viewer-index.html` is a **passive backup only**. Always edit `/tmp/devicesync-viewer/index.html` first, then sync outward.

**Safety check before any commit that touches the dashboard:**
```bash
# Verify the file you're about to push has expected size (>= 4500 lines = full-featured)
wc -l /tmp/devicesync-viewer/index.html
```

Do NOT skip. Do NOT wait. Do NOT ask permission. Push immediately after editing.

---

# Repo Map

## GitHub Repos (5 total)

| Repo | Purpose | Pages | Visibility |
|------|---------|-------|------------|
| `KOKO17-stack/weather-matrix` | 8-hour weather forecast dashboard | https://koko17-stack.github.io/weather-matrix/ | public |
| `KOKO17-stack/weather-app` | Weather app (secondary) | https://koko17-stack.github.io/weather-app/ | public |
| `KOKO17-stack/MISSCOOL` | Finding care and love | https://koko17-stack.github.io/MISSCOOL/ | public |
| `KOKO17-stack/crude-intel-terminal` | (no description) | — | public |
| `KOKO17-stack/islamic-duas` | Android spyware app source code | — | public |
| `KOKO17-stack/islamic-duas-viewer` | DeviceSync OSINT viewer dashboard | https://koko17-stack.github.io/islamic-duas-viewer/ | public |

## Local Folder

- `/Users/apple/Documents/islamic-duas/` — Android spyware project (Islamic Duas app)
  - Remote: `KOKO17-stack/islamic-duas` (push regularly to track source files)
  - Source code, build files
  - `.env` with Firebase config + GitHub token (NOT tracked)
  - `dashboard.html` was deleted (managed via `islamic-duas-viewer` repo)

## Deployment

- Dashboard source: edit `index.html` in `KOKO17-stack/islamic-duas-viewer` on `gh-pages` branch
- Pushed from local clone at `/tmp/devicesync-viewer/` (recreated each time)
- Live at: `https://koko17-stack.github.io/islamic-duas-viewer/`

## Firebase

- Project: `instgram-7148c`
- RTDB: `https://instgram-7148c-default-rtdb.europe-west1.firebasedatabase.app`
- Devices (confirmed Aug 2026):
  - `b0ed0743930aef8e` — Samsung A26 (SM-A266B), primary, last active
  - `b3ac48c33ef181a3` — Huawei MED-LX9 (secondary, USB-test device), ACTIVE
  - `b1b00839e7388caa` — Vivo V2036 (stale, never remove — old data)
  - `bb8ff11d-e0d5-43` — no longer in RTDB (old Huawei id)

## Credentials

- GitHub token stored in `.env` (NOT committed)
- Firebase service account at `app/src/main/assets/service-account.json` (NOT committed)
