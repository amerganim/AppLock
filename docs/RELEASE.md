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

1. Repo → **Settings → Pages**.
2. **Build and deployment → Source:** *Deploy from a branch*.
3. **Branch:** `main`, **folder:** `/docs`. Save.
4. After a minute the policy is live at:
   `https://amerganim.github.io/AppLock/privacy.html`
   (already wired into the app's Settings → Privacy policy).

## Play Console assets
- Screenshots: `docs/screenshots/` (welcome, PIN setup, pattern setup, home, settings).
- Listing text + ASO keywords: `docs/play-store.md`.
- Still to make: 512×512 icon (export from the adaptive icon) and a 1024×500
  feature graphic.
