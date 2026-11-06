# Alpha signing (SMO-S3-06)

> End-to-end Pages deployment checklist: [`distribution-gate.md`](distribution-gate.md).

The GitHub Pages portal publishes a **debug-variant** APK so testers can install the current build
in one tap. Android identifies an app by `applicationId` **plus** its signing certificate, so an APK
signed with a different key than the installed copy is rejected with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` — the tester has to uninstall first and loses their profile.

On a hosted runner there is no `~/.android/debug.keystore`, so AGP generates a fresh one per run.
Every published alpha therefore had a different certificate. This document describes the stable key
that replaces it and how the build reacts to it.

## How the build behaves

`app/build.gradle.kts` reads four environment variables:

| Variable | Meaning |
| :--- | :--- |
| `ALPHA_KEYSTORE_PATH` | Absolute path to the keystore file (`.jks`). |
| `ALPHA_KEYSTORE_PASSWORD` | Store password. |
| `ALPHA_KEY_ALIAS` | Key alias inside the store. |
| `ALPHA_KEY_PASSWORD` | Key password (usually equal to the store password). |
| `ALPHA_VERSION_CODE` | Optional. `versionCode` override; the workflow passes `github.run_number`. |
| `ALPHA_SIGNING_REQUIRED` | Optional. `true` makes a missing keystore a **build failure**. |

* **All four signing variables present** → the debug variant is signed with the alpha key. Nothing
  else about the build changes: same artifact path (`app/build/outputs/apk/debug/app-debug.apk`),
  same tests, no R8.
* **No variables** (normal local builds) → the default debug keystore is used, exactly as before.
* **`ALPHA_SIGNING_REQUIRED=true` and no keystore** → Gradle fails with a pointer to this file. The
  Pages workflow sets it so a rotating signature can never be published silently.

## One-time setup

### 1. Create the keystore

Run this **once** and keep the file and passwords safe. Losing the key means testers must uninstall
the app one last time before the new build installs.

```bash
keytool -genkeypair -v \
  -keystore showmeoutfit-alpha.jks \
  -alias showmeoutfit-alpha \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "<store-password>" -keypass "<key-password>" \
  -dname "CN=ShowMeOutfit Alpha, O=ShowMeOutfit, C=VN"
```

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
  -keystore showmeoutfit-alpha.jks `
  -alias showmeoutfit-alpha `
  -keyalg RSA -keysize 4096 -validity 10000 `
  -storepass "<store-password>" -keypass "<key-password>" `
  -dname "CN=ShowMeOutfit Alpha, O=ShowMeOutfit, C=VN"
```

`*.jks`, `*.keystore` and `keystore.properties` are git-ignored; the key never enters the repository.

### 2. Base64-encode it for the secret

```bash
base64 -w0 showmeoutfit-alpha.jks > alpha-keystore.b64
```

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("showmeoutfit-alpha.jks")) |
  Set-Content -NoNewline alpha-keystore.b64
```

### 3. Add four repository secrets

Settings → Secrets and variables → Actions → **New repository secret**:

| Secret | Value |
| :--- | :--- |
| `ALPHA_KEYSTORE_BASE64` | Contents of `alpha-keystore.b64` |
| `ALPHA_KEYSTORE_PASSWORD` | The store password |
| `ALPHA_KEY_ALIAS` | `showmeoutfit-alpha` |
| `ALPHA_KEY_PASSWORD` | The key password |

The workflow decodes the keystore into `$RUNNER_TEMP`, `chmod 600`s it, exports the signing
variables and sets `ALPHA_VERSION_CODE` from the workflow run number.

### 4. Delete the local copies of the encoded file

`alpha-keystore.b64` contains the key in the clear. Keep the `.jks` in a password manager or an
offline backup, not in the working tree.

## Verifying a build

```powershell
$env:ALPHA_KEYSTORE_PATH = "C:\keys\showmeoutfit-alpha.jks"
$env:ALPHA_KEYSTORE_PASSWORD = "<store-password>"
$env:ALPHA_KEY_ALIAS = "showmeoutfit-alpha"
$env:ALPHA_KEY_PASSWORD = "<key-password>"
$env:ALPHA_VERSION_CODE = "42"
.\gradlew.bat assembleDebug
```

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\<version>\apksigner.bat" verify --print-certs `
  app\build\outputs\apk\debug\app-debug.apk
```

The printed SHA-256 must match `keytool -list -v -keystore <file> -alias <alias>`. Two consecutive
builds must produce the **same** digest — that is the property this setup exists to guarantee.

The version code is visible in the same way:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\<version>\aapt2.exe" dump badging `
  app\build\outputs\apk\debug\app-debug.apk | Select-String versionCode
```

## Why `debug` and not a new build type

A separate `alpha` build type would need its own `applicationId` suffix, resource overrides and a
second test matrix for one property (a stable certificate). The debug variant already has no R8,
which is what an early alpha wants, so the signing config is overridden in place and the workflow
keeps its existing artifact path.

## Rotation

Only rotate the key if it is compromised or lost. Rotating always forces one uninstall for every
tester, because Android treats the new certificate as a different app. If a rotation becomes
necessary, update the four secrets and note the uninstall requirement on the portal.
