# LockApp

A private, native-Kotlin **app locker** for Android (`com.amerganim.lockapp`). Choose
a PIN or pattern, optionally add fingerprint/face unlock, pick which apps to protect,
and whenever one of them is opened a full-screen lock prompt appears over it. It also
includes an **encrypted photo/video vault**, **intruder selfie**, **app disguise**,
and **tamper protection** — everything runs and stays on the device, with no account,
no ads, and no tracking.

- **Min SDK** 26 (Android 8.0) · **Target/Compile SDK** 35 · 100% Kotlin.
- Store listing, ASO keywords, privacy policy, Play assets and the release process
  live in [`docs/`](docs/).
- Releases (signed AAB + APK) are published automatically — see [CI/CD](#cicd--releases).

---

## Features

### Locking
- **PIN or pattern** lock; **fingerprint/face** quick unlock (PIN/pattern is always
  a fallback).
- **Per-app locking** for every launchable app, with in-app **search**.
- A foreground service detects the foreground app and shows the lock screen over any
  protected app; the app's own UI is gated too.
- **Configurable re-lock delay** (immediately / 10s / 30s / 1 min after you leave).
- **Everything re-locks when the screen goes off**, so an app left open and unlocked is
  not waiting for whoever picks the device up next.
- **Auto-lock new apps** — newly installed apps are locked automatically.
- **Scheduled pause** — a time window (e.g. at home in the evening) where locking is
  paused; overnight windows supported.
- Protection **auto-restarts after reboot**.

### Privacy & security
- **Photo & video vault** — hide media behind your lock, **encrypted at rest** with
  AES-256. See [How the vault works](#how-the-photo--video-vault-works).
- **Intruder selfie** — after 3 wrong attempts, the front camera silently captures a
  photo to private storage; a built-in gallery lets you review/clear them.
- **Forgot-PIN recovery** via a security question, so you can't get locked out.
- **Wrong-attempt lockout** — after 5 wrong attempts each further one starts a cooldown
  that doubles from 30s up to 5 min, with a live countdown. The counter is persisted, so
  closing the prompt or force-stopping the app does not reset it. Wrong recovery answers
  are throttled the same way.
- **Every screen is gated** — the home screen, settings, vault and intruder gallery each
  require the lock, so none of them can be reached by restoring the app from Recents.
- **Scrambled keypad** option to defeat shoulder-surfing.
- Every gated screen is **`FLAG_SECURE`** (no screenshots, and a blank card in the
  recents/task switcher): home, lock screen, PIN/pattern setup, recovery, settings,
  vault and the intruder gallery. Without it, the task switcher showed the home screen —
  locked-app list and all — to anyone who pressed the Recents button.

### Anti-tamper & disguise
- **Tamper protection** — locks the Settings app + package installers (so Uninstall /
  Force-stop / Clear-data need your lock) and registers a **device administrator** so
  Android blocks uninstall until it's turned off. Optional, off by default, disclosed.
- **App disguise** — swap the launcher icon + name to look like a Calculator, Clock,
  or Notes app.
- **Fake cover** — show a fake "*\<app\> keeps stopping*" crash dialog over the lock;
  a secret **long-press** reveals the real PIN/pattern.

### Experience
- Guided **onboarding**, a full **Settings** screen, **light/dark/system themes**, a
  splash screen, and an adaptive launcher icon.
- Haptic keypad feedback, and a home-screen prompt to lift **battery optimisation** —
  the usual reason protection "randomly stops" on aggressive OEMs.

---

## How the photo & video vault works

The vault lets you move photos and videos out of your normal gallery and into LockApp,
where they're **encrypted and only viewable after you unlock**.

**Importing.** Tap **+** in the vault to open the Android **Photo Picker**. The picker
needs **no storage permission** — Android hands LockApp temporary read access only to
the items you select. Each selected file is streamed into the vault and **encrypted on
the way in**.

**Encryption at rest.** Files are stored in the app's private internal storage
(`filesDir/vault/`, which other apps already can't read) and additionally encrypted
with Jetpack Security's **`EncryptedFile`** using **AES-256-GCM-HKDF**. The encryption
key is a hardware-backed **Android Keystore** master key — the bytes on disk are
unreadable without it, even on a rooted device. Nothing is ever uploaded.

**Viewing.** Thumbnails are decrypted in memory for the grid. Tapping a **photo** opens
a full-screen, `FLAG_SECURE` viewer (decrypted just-in-time). Tapping a **video**
decrypts it to a temporary cache file and plays it via a scoped `FileProvider` URI; the
temp file is wiped when you leave the vault.

**Removing originals (optional).** By default importing *copies* media in (the gallery
original stays). Enable **Remove original after import** in the vault menu (Android 11+)
to delete the gallery copies — LockApp resolves each imported item to its MediaStore URI
and uses Android's own **delete-request consent dialog**, so the system asks you to
confirm. It needs **no** media permission (the consent dialog authorizes the delete).

**Getting media back out / deleting.** Long-press an item to **Export to gallery** (it's
decrypted and written back to `Pictures/LockApp` or `Movies/LockApp`) or **Delete** it
from the vault.

> Implementation: [`VaultManager.kt`](app/src/main/java/com/amerganim/lockapp/VaultManager.kt)
> (crypto + import/export), [`VaultActivity.kt`](app/src/main/java/com/amerganim/lockapp/VaultActivity.kt)
> (grid + picker), [`VaultViewerActivity.kt`](app/src/main/java/com/amerganim/lockapp/VaultViewerActivity.kt)
> (full-screen image). Reached via **Settings → Private → Photo & video vault**.

---

## How the core locking works

1. **`AppLockService`** is a foreground service that polls **`UsageStatsManager`** every
   200 ms to find the foreground package. Each poll only reads usage *events* newer than
   the previous one and remembers the last app seen, because events fire on a change:
   reading no event means "still the same app", not "unknown".
2. When a **locked** app (or, with tamper protection, a system Settings/installer
   screen) comes to the foreground and isn't currently unlocked, the service launches
   **`LockScreenActivity`** over it (single-instance, excluded from recents, `FLAG_SECURE`).
3. The user unlocks with **PIN / pattern / biometric**. On success the package is marked
   unlocked in **`LockState`** (in-memory only, so everything re-locks if the process dies).
4. **`LockState.onTick`** keeps the current app "fresh" while it stays foreground and
   **re-locks** other apps once they've been in the background longer than the configured
   delay. A just-unlocked app is held for a short grace period until it has actually been
   seen in the foreground, so the "Immediately" delay cannot sweep the unlock away before
   the protected app is even shown.
5. When the **display turns off** everything re-locks at once (the delay only counts
   background time), and polling stops until the screen comes back.
6. Pressing **Back** on the lock screen goes to the home screen rather than revealing the
   app; pressing **Home** is unavoidable but the app re-locks on next open.
7. If usage access or the overlay permission is revoked while running, the ongoing
   notification says **protection is paused** instead of failing silently.

The app's own UI is protected by the same `LockScreenActivity` in a "self" mode: every
private screen extends `SecureActivity`, which hides its content and shows the prompt from
`onResume` until the lock is entered. The authenticated session covers the whole app, so
moving between home, settings and the vault never re-prompts — it ends as soon as the app
leaves the foreground.

---

## Architecture

All code is in `app/src/main/java/com/amerganim/lockapp/`.

### Entry & setup
| Component | Role |
|---|---|
| `LockApp` | `Application`; applies the saved theme at startup. |
| `WelcomeActivity` | First-run welcome/value-prop screen. |
| `SetupLockActivity` | Choose PIN or pattern and set it (with confirmation). |
| `SetupRecoveryActivity` | Choose a security question + answer. |
| `MainActivity` | Home: permission prompts, Protection switch, searchable per-app lock list. |
| `SettingsActivity` | All settings: change lock, recovery, timing, theme, biometric, scramble, auto-lock new apps, intruder, vault, schedule, disguise, fake cover, tamper. |

### Locking core
| Component | Role |
|---|---|
| `AppLockService` | Foreground service; foreground-app polling + launches the lock screen. |
| `LockScreenActivity` | The lock prompt: PIN/pattern + biometric + forgot + fake cover. |
| `LockState` | In-memory unlock state, re-lock bookkeeping, own-UI session tracking. |
| `AttemptGuard` | Wrong-attempt counting and the escalating cooldown policy. |
| `LockPrefs` | Non-secret settings (locked apps, toggles, timing, schedule, disguise). |
| `BootReceiver` | Restarts protection after reboot. |
| `NewAppReceiver` | Auto-locks newly installed apps (`PACKAGE_ADDED`). |
| `Permissions` | Helpers for Usage Access / overlay / notification permissions. |

### Credentials & UI building blocks
| Component | Role |
|---|---|
| `CredentialManager` | PIN/pattern + recovery, salted SHA-256 hashes in `EncryptedSharedPreferences`. |
| `RecoveryActivity` | Forgot-lock flow (verify answer → reset credential). |
| `PinPad` | Drives the numeric keypad + dots; optional **scramble**. |
| `PatternLockView` | Custom 3×3 pattern view (drawing, intermediate dots, error state). |
| `AppListAdapter` | RecyclerView adapter for the per-app lock list. |
| `SecureActivity` | Base class that gates a screen behind the lock and sets `FLAG_SECURE`. |

### Privacy features
| Component | Role |
|---|---|
| `VaultManager` | Encrypted import/list/decrypt/export/delete via `EncryptedFile`. |
| `VaultActivity` / `VaultViewerActivity` | Vault grid (Photo Picker import) + full-screen viewer. |
| `IntruderManager` | Silent front-camera capture (CameraX) + storage. |
| `IntrudersActivity` | Gallery of captured intruder photos. |

### Anti-tamper & disguise
| Component | Role |
|---|---|
| `AdminReceiver` | `DeviceAdminReceiver`; lets Android block uninstall while tamper protection is on. |
| `DisguiseManager` | Enables one of several launcher `activity-alias` entries to swap icon/name. |

### Data & security model
- **Credential + recovery:** never stored in clear — random-salted **SHA-256** hashes,
  inside **`EncryptedSharedPreferences`** (Keystore-backed master key).
- **Vault media:** **`EncryptedFile`** (AES-256-GCM-HKDF) in private internal storage.
- **Intruder photos:** JPEGs in private internal storage.
- **Runtime unlock state:** memory only (`LockState`) — re-locks on process death.
- **Wrong-attempt counters:** plain `SharedPreferences`, written with `commit()` so a
  force-stop cannot clear a running cooldown.
- **Settings/locked-app list:** plain `SharedPreferences` (`LockPrefs`) — not secret.

---

## Permissions

| Permission | Why |
|---|---|
| Usage access (`PACKAGE_USAGE_STATS`) | Detect the foreground app. *(required, granted in system Settings)* |
| Display over other apps (`SYSTEM_ALERT_WINDOW`) | Show the lock screen over apps. *(required)* |
| `FOREGROUND_SERVICE` (+ special use) | Keep the monitor alive. |
| `POST_NOTIFICATIONS` | The ongoing "protection is on" notification (Android 13+). |
| `QUERY_ALL_PACKAGES` | List launchable apps so you can choose which to lock. |
| `CAMERA` | Only if you enable **Intruder selfie**. |
| `RECEIVE_BOOT_COMPLETED` | Restart protection after reboot. |

No personal data is collected or transmitted. See the
[privacy policy](https://amerganim.github.io/applock-privacy-policy/privacy.html).

---

## Build & install

Needs the Android SDK (platform 35). Easiest: open the folder in **Android Studio** and
Run. From the command line:

```powershell
# Windows
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:assembleDebug
```
```bash
# macOS / Linux
./gradlew :app:assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk` → `adb install -r <apk>`.

`local.properties` (SDK path) is machine-specific and git-ignored.

### First run
1. Welcome → choose & set a **PIN or pattern** → set a recovery question.
2. On the home screen, grant **Usage access** and **Display over other apps**.
3. Turn **Protection** on and toggle the apps to lock.

## CI/CD & releases

- **`.github/workflows/android-ci.yml`** — on every push/PR to `main`: builds the debug
  APK + lint and uploads it as an artifact.
- **`.github/workflows/release.yml`** — on pushing a tag `v*`: builds the **signed** AAB +
  APK and publishes a GitHub Release with both attached.

Signing reads `keystore.properties` locally or the `KEYSTORE_*` secrets in CI; both the
keystore and `keystore.properties` are git-ignored. Full details and the Play upload
checklist are in [`docs/RELEASE.md`](docs/RELEASE.md) and [`docs/play-store.md`](docs/play-store.md).

```bash
# cut a release
git tag v1.0.0 && git push origin v1.0.0
```

## Known limitations
- Pressing **Home** dismisses the lock prompt (the app re-locks on next open) — a
  non-device-admin app can't intercept Home.
- Foreground polling costs ~3% of one CPU core while the screen is on (measured on a
  Galaxy A15 / Android 16) for up to ~200 ms lock latency; it idles while the display is
  off.
- Aggressive OEMs (Xiaomi, Oppo, etc.) may kill the service — disable battery
  optimization / enable autostart for reliability.
- It deters casual access; it is not unbeatable (ADB, Safe Mode, or a factory reset can
  still remove the app).

## Why native Kotlin instead of Flutter?
The hard parts of an app locker are all Android system APIs — `UsageStatsManager`,
overlay/background-activity-start, foreground service, device admin, `EncryptedFile`.
In Flutter all of that would still be written in Kotlin behind platform channels, adding
a bridge layer with no payoff; the only easy part (the PIN pad) isn't worth the overhead.
