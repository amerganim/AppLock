# LockApp — Google Play listing & release notes

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
Lock apps with a PIN, pattern or fingerprint. Simple, private app locker.
```

### Full description (max 4000 chars)
```
LockApp is a simple, private app locker that keeps your apps safe behind a PIN,
pattern, or fingerprint. Lock WhatsApp, Messenger, your gallery, settings, banking
apps, or anything else — in just a few taps.

Why LockApp?
• App lock that just works — pick any app and it asks for your PIN or pattern when opened.
• PIN, pattern, or fingerprint/face unlock — your choice.
• Forgot your PIN? Reset it safely with a recovery question.
• 100% private — everything stays on your device. No account, no ads, no tracking.
• Lightweight and fast.

FEATURES
• Lock any app with a PIN or pattern
• Fingerprint / face quick unlock
• Recovery question so you never get locked out
• Auto-lock timing (immediately, or after a delay)
• Light and dark themes
• Optional tamper protection that makes the app harder to uninstall
• Re-locks automatically when you leave a protected app

LockApp is the app locker (AppLock) you can trust: no data ever leaves your phone.

Note: LockApp uses Usage Access to detect when a locked app is opened and the
"Display over other apps" permission to show the lock screen. It never collects or
shares any personal data. See our privacy policy for details.
```

**Keywords covered:** app lock, app locker, applock, lock apps, fingerprint lock,
pattern lock, gallery lock, privacy, PIN lock.

### Privacy policy URL
`https://amerganim.github.io/AppLock/privacy.html`
(Enable GitHub Pages on this repo from the `/docs` folder to publish it.)

---

## Pre-launch checklist

- [x] Real `applicationId` (`com.amerganim.lockapp`) — `com.example.*` is blocked by Play
- [x] `targetSdk` 35 (Play requirement for new apps)
- [x] Privacy policy written (`docs/privacy.html`) — **host it and paste the URL in Play**
- [ ] **Signed release build**: create an upload keystore and a `release` signing
      config, then `./gradlew :app:bundleRelease` to produce the AAB.
- [ ] **Data safety form**: declare *no data collected, no data shared*.
- [ ] **Permissions declarations** in Play Console:
  - `QUERY_ALL_PACKAGES` → justification: *core app-locker functionality requires
    showing the user every launchable app so they can choose which to lock.*
  - Foreground service (`specialUse`) → describe the monitoring use.
  - Prominent in-app disclosure for Usage Access is already shown during onboarding.
- [ ] **Device admin**: tamper protection is **off by default and clearly disclosed**.
      If Play review flags "prevents uninstall," be ready to keep only the
      Settings-lock (the device-admin part can be removed without affecting the rest).
- [ ] Content rating questionnaire.
- [ ] Screenshots (phone) + 512×512 icon + 1024×500 feature graphic.
- [ ] Target audience & content (not directed at children).

## Assets still to produce
- Phone screenshots of: onboarding, lock screen (PIN + pattern), app list, settings.
- Feature graphic (1024×500).
