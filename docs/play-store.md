# LockApp — Google Play listing & release notes

Current release: **1.1.0 (versionCode 3)** · `minSdk 26` · `targetSdk 36` (Android 16)

## Store listing (ASO)

Android ASO is driven mainly by the **title**, **short description**, and
**full description** below — keywords are woven into the text (Play has no separate
keyword field).

### App title (max 30 chars)
```
LockApp: App Lock & Locker
```

### Short description (max 80 chars)
```
Lock apps with a PIN, pattern or fingerprint. Private app locker, no ads.
```

### Full description (max 4000 chars)
```
LockApp is a simple, private app locker that keeps your apps behind a PIN, pattern,
or fingerprint. Lock WhatsApp, Messenger, your gallery, settings, banking apps, or
anything else — in a few taps. Everything stays on your phone: no account, no ads,
no tracking.

WHY LOCKAPP
• App lock that just works — pick an app and it asks for your PIN when opened.
• PIN (4 to 8 digits), pattern, or fingerprint/face unlock — your choice.
• Protection switches on the moment you lock your first app.
• Forgot your PIN? Reset it safely with your recovery question.
• No account, no ads, no tracking, no internet permission for your data.

LOCKING
• Lock any installed app with a PIN or pattern
• Fingerprint / face quick unlock, with your PIN or pattern always as a fallback
• Choose when apps re-lock: immediately, or after 10s / 30s / 1 minute
• Everything re-locks the moment your screen turns off
• Auto-lock newly installed apps
• Scheduled pause — switch locking off during a daily time window
• Protection restarts automatically after you reboot

PRIVATE PHOTO & VIDEO VAULT
• Move photos and videos out of your gallery and into LockApp
• Encrypted on your device with AES-256, readable only after you unlock
• Export anything back to your gallery whenever you want

CATCH SNOOPERS
• Intruder selfie — after repeated wrong attempts the front camera quietly takes a
  photo, saved privately on your phone for you to review
• Wrong-attempt lockout — guessing gets slower and slower, so a PIN cannot simply
  be guessed at speed
• Scrambled keypad option so nobody can read your PIN over your shoulder
• Lock screen, vault and settings are hidden from screenshots and the app switcher

HIDE THE APP ITSELF
• Disguise LockApp as a Calculator, Clock or Notes app on your home screen
• Fake cover — show a fake "app keeps stopping" message over the lock; a secret
  long-press reveals the real PIN pad
• Optional: ask for your lock before the Recents screen opens

STOP UNINSTALLS
• Optional tamper protection locks the Settings app and blocks uninstall until you
  turn it off with your lock. Off by default, and always your choice.

LockApp is the app locker (AppLock) you can trust: your data never leaves your phone.

Note: LockApp uses Usage Access to detect when a locked app is opened, and the
"Display over other apps" permission to show the lock screen over it. The camera is
used only if you switch on intruder selfie. No personal data is collected or shared.
See our privacy policy for details.
```

**Keywords covered:** app lock, app locker, applock, lock apps, fingerprint lock,
pattern lock, gallery lock, photo vault, privacy, PIN lock, hide apps.

### What's new (max 500 chars) — 1.1.0
```
• Fixed a bypass where a locked app could be opened from the app switcher
• Apps now re-lock as soon as the screen turns off
• PINs can be 4 to 8 digits
• Repeated wrong attempts now trigger a cooldown that grows each time
• LockApp's own screen is hidden from the app switcher
• New: optionally lock the Recents screen
• Protection switches on when you lock your first app
• Updated for Android 16
```

### Privacy policy URL
`https://amerganim.github.io/applock-privacy-policy/privacy.html`
(GitHub Pages from [amerganim/applock-privacy-policy](https://github.com/amerganim/applock-privacy-policy) — already live.)

---

## Data safety form

Answer **"No data collected"** and **"No data shared"**. Play defines *collection* as
data transmitted off the device, and nothing here ever leaves it:

| Thing | Where it lives | Leaves the device? |
|---|---|---|
| PIN / pattern / recovery answer | Salted hashes in `EncryptedSharedPreferences` | No |
| Vault photos & videos | AES-256 `EncryptedFile` in app-private storage | No |
| Intruder selfies | JPEGs in app-private storage | No |
| Locked-app list & settings | App-private `SharedPreferences` | No |

The app has **no `INTERNET` permission**, which is the simplest way to back this up if
review asks. Say *yes* to "data is encrypted in transit" being not applicable, and offer
the in-app **Delete** controls (vault delete, clear intruder photos, clear app data).

## Permission declarations in Play Console

| Permission | What to say |
|---|---|
| `QUERY_ALL_PACKAGES` | **Highest rejection risk — see below.** Justification: a user-facing app locker must list every launchable app so the user can choose which to lock. |
| `SYSTEM_ALERT_WINDOW` | The lock screen must appear over the protected app the moment it is opened. |
| Foreground service (`specialUse`) | Watches for a protected app coming to the foreground so the lock prompt can be shown. Subtype string is declared in the manifest. |
| `PACKAGE_USAGE_STATS` | Granted by the user in system Settings; onboarding explains it before sending them there. |
| `CAMERA` | Only requested when the user switches on Intruder selfie. |
| Device admin | Only when the user switches on tamper protection; off by default, disclosed in the setting text and on the system screen. |

> **Before you upload:** Play's permitted uses for `QUERY_ALL_PACKAGES` are a short list
> (antivirus, file managers, browsers, device search…) and app lockers are not on it, so
> this is the likeliest reason for a rejection. The app does not actually need it: the
> list it wants is *launchable apps*, which a `<queries>` element with the
> `MAIN`/`LAUNCHER` intent already provides, with no declaration form and no broad
> permission. Switching is a small manifest change and is worth doing before the first
> submission.

## Pre-launch checklist

- [x] Real `applicationId` (`com.amerganim.lockapp`)
- [x] `targetSdk` 36 — Play requires API 36 for new apps and updates from 31 Aug 2026
- [x] Version bumped: **1.1.0 / versionCode 3**
- [x] Privacy policy live
- [x] Signed release build (upload keystore + `release` signing config) — see `RELEASE.md`
- [x] CI/CD (GitHub Actions build + tagged release)
- [x] Phone screenshots refreshed for 1.1.0 (`docs/screenshots/`)
- [ ] Replace `QUERY_ALL_PACKAGES` with a `<queries>` element (see the note above)
- [ ] Data safety form (answers above)
- [ ] Permission declarations (table above)
- [ ] Content rating questionnaire
- [ ] Target audience & content — not directed at children
- [ ] Internal testing track first, then production

## Assets

| Asset | File | Notes |
|---|---|---|
| Phone screenshots | `docs/screenshots/01-lock … 06-welcome` | 1080×2340, captured on a Galaxy A15 (Android 16) |
| Feature graphic (1024×500) | `docs/feature-graphic.png` | |
| Store icon (512×512) | `docs/play-icon-512.png` | |

**Before uploading the screenshots:** `02-home` and `05-settings` were shot on a real
phone, so they show that phone's installed apps — other companies' names and icons in
your listing can draw a trademark complaint, and the list is personal. Consider
re-shooting those two on a device with a curated set of apps. Screens are `FLAG_SECURE`,
so `screencap` returns black; `RELEASE.md` has the procedure for capturing them.
