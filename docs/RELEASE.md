# Releasing LockApp

## Signing keys

The release build is signed with an **upload keystore**. It is **never committed**
(`*.keystore` and `keystore.properties` are git-ignored). Keep a safe backup — if
you enroll in Play App Signing (recommended), a lost upload key can be reset, but
back it up anyway.

- Keystore: `lockapp-upload.keystore` (repo root), alias `lockapp`.
- Local config: `keystore.properties` (repo root):
  ```
  storeFile=lockapp-upload.keystore
  storePassword=********
  keyAlias=lockapp
  keyPassword=********
  ```
- `app/build.gradle.kts` reads `keystore.properties` locally, or the env vars
  `KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD` in CI. With no
  key material, release builds are simply left unsigned (so CI/debug still work).

To create a new keystore:
```
keytool -genkeypair -v -keystore lockapp-upload.keystore -alias lockapp \
  -keyalg RSA -keysize 2048 -validity 10000
```

## Backing up the key, and checking a backup is good

Losing the upload key means never shipping an update to `com.amerganim.lockapp` again
(unless Play App Signing is enabled, in which case Google can reset the upload key). Keep
at least two copies, in different places, and keep the **passwords separate from the
keystore file** — `keystore.properties` holds them in clear text, so a copy of both
together is a single point of compromise.

A copy is only a backup once you have opened it. Verify with:

```bash
sha256sum -c SHA256SUMS.txt
keytool -list -v -keystore lockapp-upload.keystore -alias lockapp
```

The certificate must match what is in every shipped APK
(`apksigner verify --print-certs app-release.apk`):

| | |
|---|---|
| Owner | `CN=LockApp, OU=Mobile, O=amerganim, L=NA, ST=NA, C=US` |
| Alias | `lockapp` (2048-bit RSA, valid until 28 Oct 2053) |
| SHA-1 | `91:D8:24:F5:EB:9A:F9:97:C5:8E:B2:6A:35:C4:AE:EC:92:E7:A7:0F` |
| SHA-256 | `9F:9F:CC:1E:3E:5C:D7:3A:67:5C:F6:0E:34:F3:70:5B:6E:2E:59:58:A3:05:E2:34:C1:64:46:08:13:B1:74:7D` |

Fingerprints are public — they ship inside every APK — so recording them here is safe and
lets a restored key be checked against a release that is already live.

To restore: copy `lockapp-upload.keystore` and `keystore.properties` back into the repo
root. Both are git-ignored.

## Build locally

```powershell
# Windows
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat bundleRelease assembleRelease
```
Outputs:
- AAB (upload to Play): `app/build/outputs/bundle/release/app-release.aab`
- APK (sideload/testing): `app/build/outputs/apk/release/app-release.apk`

Verify the APK signature:
```
"$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs app/build/outputs/apk/release/app-release.apk
```

## Version numbers

`app/build.gradle.kts` holds `versionCode` / `versionName`. Play rejects an upload whose
`versionCode` is not higher than the last one, so bump it before every release:

| Release | versionName | versionCode |
|---|---|---|
| 1.0.1 | 1.0.1 | 2 |
| 1.1.0 | 1.1.0 | 3 |

`targetSdk` must stay within Play's annual requirement — API 36 (Android 16) for new
apps and updates from 31 Aug 2026. Raising it is not a formality: Android 16 ignores
`windowOptOutEdgeToEdgeEnforcement`, so the app applies window insets itself
(`Insets.kt`). Re-check the bottom of the lock screen on a device after any bump.

## Capturing store screenshots

Every gated screen sets `FLAG_SECURE`, so `adb exec-out screencap` returns a black
image and the screens cannot be captured directly. To refresh the store screenshots:

1. Temporarily set `SecureActivity.secureWindow` to `false`, and comment out the
   `window.setFlags(FLAG_SECURE, …)` line in `LockScreenActivity.onCreate`.
2. `./gradlew :app:assembleDebug` and install on a device.
3. Capture: `adb exec-out screencap -p > shot.png`.
4. **Revert both edits** and reinstall before shipping anything. Check it worked: a
   screencap of the lock screen must come back black.

Do not commit the temporary edits — they disable the protection that keeps the lock
screen, vault and settings out of screenshots and the app switcher.

## Continuous integration

- **`.github/workflows/android-ci.yml`** — on every push/PR to `main`: builds the
  debug APK + runs lint and uploads the APK as a workflow artifact.
- **`.github/workflows/release.yml`** — on pushing a tag `v*` (e.g. `v1.0.0`): builds
  the **signed** AAB + APK and publishes a GitHub Release with both attached.

### Required GitHub secrets (for the release workflow)

Repo → Settings → Secrets and variables → Actions → New repository secret:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | base64 of `lockapp-upload.keystore` |
| `KEYSTORE_PASSWORD` | the store password |
| `KEY_ALIAS` | `lockapp` |
| `KEY_PASSWORD` | the key password |

Get the base64 (PowerShell):
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("lockapp-upload.keystore")) | Set-Content keystore.b64
```
Paste the contents of `keystore.b64` into `KEYSTORE_BASE64` (then delete the file).

### Cut a release
```
git tag v1.0.0
git push origin v1.0.0
```

## Privacy policy hosting (GitHub Pages)

The policy is hosted via GitHub Pages from a dedicated public repo,
[amerganim/applock-privacy-policy](https://github.com/amerganim/applock-privacy-policy)
(Pages → branch `main`, folder `/`). It is already live and wired into the app
(Settings → Privacy policy) and the Play listing:

`https://amerganim.github.io/applock-privacy-policy/privacy.html`

When the policy changes, update `privacy.html` in that repo (keep `docs/privacy.html`
here as the source of truth and copy it over).

## Play Console assets
- Screenshots: `docs/screenshots/` (welcome, PIN setup, pattern setup, home, settings).
- Listing text + ASO keywords: `docs/play-store.md`.
- Still to make: 512×512 icon (export from the adaptive icon) and a 1024×500
  feature graphic.
