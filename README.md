# AppLock

A simple Android app locker. Set a 4-digit PIN, choose which installed apps to
protect, and whenever one of those apps is opened a full-screen PIN prompt appears
over it. The app's own settings are PIN-protected too.

Built **natively in Kotlin** (not Flutter) because everything that makes an app
locker work — detecting the foreground app, drawing over other apps, surviving
reboot — is platform-level Android. See the note at the bottom for why.

## Features

- 4-digit PIN, stored only as a salted SHA-256 hash inside `EncryptedSharedPreferences`.
- Per-app lock toggles for every launchable app on the device.
- A foreground service watches the foreground app and pops the lock screen when a
  protected app is opened.
- Unlocked apps re-lock automatically once you leave them.
- Protection auto-restarts after reboot.
- The app's own settings screen is gated behind the PIN.

## Build & install

You need the Android SDK (platform 34). Easiest path: open the folder in
**Android Studio** and let it sync, then Run.

From the command line:

```powershell
# Windows
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
```

```bash
# macOS / Linux
./gradlew :app:assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Install it:

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> `local.properties` (the SDK path) is machine-specific and git-ignored. Android
> Studio creates it automatically; for CLI builds set `sdk.dir` or `ANDROID_HOME`.

## First run / required permissions

App lockers rely on three permissions that the user must grant manually. Open the
app and tap each row on the main screen to grant:

1. **Usage access** — lets the app see which app is in the foreground. *(required)*
2. **Display over other apps** — lets the lock screen appear over other apps. *(required)*
3. **Notifications** — for the ongoing "protection is on" notification. *(Android 13+)*

Then:

1. Set and confirm a 4-digit PIN.
2. Grant the two required permissions.
3. Flip **Protection** on.
4. Toggle the apps you want to lock.

## How it works

| File | Role |
|------|------|
| `SetPinActivity` | First-run PIN creation + confirmation. |
| `MainActivity` | PIN-gated settings: permissions, protection switch, per-app toggles. |
| `AppLockService` | Foreground service; polls `UsageStatsManager` every ~600 ms and launches the lock screen when a locked app is detected. |
| `LockScreenActivity` | Full-screen PIN prompt shown over a locked app (and to protect the app's own UI). `FLAG_SECURE`, excluded from recents, single-instance. |
| `BootReceiver` | Restarts protection after reboot. |
| `PinManager` | Salted-hash PIN storage in `EncryptedSharedPreferences`. |
| `LockPrefs` / `LockState` | Persisted settings and in-memory unlock state. |

### Known limitations

This is a lightweight locker, not a security product:

- Pressing **Home** dismisses the lock prompt (the protected app re-locks on next
  open). A non-device-admin app can't block Home.
- Polling foreground apps adds a small battery cost and has up to ~600 ms latency.
- On aggressive OEMs (Xiaomi, Oppo, etc.) you may need to disable battery
  optimization / enable autostart so the service isn't killed.

## Why native Kotlin instead of Flutter?

The hard parts of an app locker are all Android system APIs: `UsageStatsManager`
for foreground detection, overlay/background-activity-start for the lock screen,
and a foreground service + boot receiver for persistence. In Flutter all of that
would still be written in Kotlin behind platform channels, adding a bridge layer
with no payoff. The only easy part (the PIN pad) isn't worth that overhead.
