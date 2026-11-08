# Production signing (SMO-S4-08)

> Alpha/test signing is a different key. See [`alpha-signing.md`](alpha-signing.md) for the Pages
> portal APK. End-to-end distribution checklist: [`distribution-gate.md`](distribution-gate.md).

The production key identifies ShowMeOutfit on Google Play. Every published build must carry the
**same** certificate: a new key means a new app identity, and an installed copy cannot be updated
over the old one.

## Where the key lives

| Item | Location |
| :--- | :--- |
| Keystore | `C:\Users\<you>\keys\showmeoutfit-release.jks` (outside the repository) |
| Credentials | `C:\Users\<you>\keys\showmeoutfit-release.credentials.txt` |
| Alias | `showmeoutfit-release` |
| Algorithm | RSA 4096, SHA384withRSA, 10,000-day validity |
| SHA-256 | `0F:3F:42:CB:38:D1:62:AB:67:0C:86:1D:2D:0D:0B:E5:5A:E1:FD:BF:1E:EA:0E:AD:BE:B8:4F:C2:65:32:09:B7` |

`*.jks`, `*.keystore` and `keystore.properties` are git-ignored. The key must **never** be committed.

> **Back this up now.** Copy the `.jks` and the password into a password manager and an offline
> backup. Losing them permanently changes the app identity.

## GitHub secrets

| Secret | Value |
| :--- | :--- |
| `RELEASE_KEYSTORE_BASE64` | Base64 of the `.jks` file |
| `RELEASE_KEYSTORE_PASSWORD` | Store password |
| `RELEASE_KEY_ALIAS` | `showmeoutfit-release` |
| `RELEASE_KEY_PASSWORD` | Key password |

The signed-release workflow ([`.github/workflows/release.yml`](../../.github/workflows/release.yml))
validates all four, decodes the keystore into `$RUNNER_TEMP/keys/release.jks`, builds
`bundleRelease` + `assembleRelease`, verifies both artifacts, uploads them, and deletes the keystore
even on failure.

Set or rotate a secret with:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Users\<you>\keys\showmeoutfit-release.jks")) |
  gh secret set RELEASE_KEYSTORE_BASE64 --repo smoh-apps/ShowMeOutfit
```

```bash
base64 -w0 showmeoutfit-release.jks | gh secret set RELEASE_KEYSTORE_BASE64 --repo smoh-apps/ShowMeOutfit
```

## How the build behaves

`app/build.gradle.kts` creates the `release` signing config **only** when
`RELEASE_KEYSTORE_PATH` resolves to an existing file:

| State | Result |
| :--- | :--- |
| `RELEASE_KEYSTORE_PATH` + password + alias + key password set | `app-release.apk` and `app-release.aab` are signed |
| Variables absent (ordinary local build) | `app-release-unsigned.apk`; the build still succeeds |

There is no `requiresSigningConfig` flag in AGP 9.x, so a missing keystore cannot fail configuration.
The workflow compensates: it asserts that `app-release.apk` exists, because AGP names the unsigned
artifact `app-release-unsigned.apk`.

## Verify locally

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:RELEASE_KEYSTORE_PATH = "C:\Users\<you>\keys\showmeoutfit-release.jks"
$env:RELEASE_KEYSTORE_PASSWORD = "<store-password>"
$env:RELEASE_KEY_ALIAS = "showmeoutfit-release"
$env:RELEASE_KEY_PASSWORD = "<key-password>"

.\gradlew.bat bundleRelease assembleRelease
```

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs `
  app\build\outputs\apk\release\app-release.apk
```

The printed SHA-256 must equal the fingerprint in the table above. For the bundle:

```powershell
& "$env:JAVA_HOME\bin\jarsigner.exe" -verify app\build\outputs\bundle\release\app-release.aab
```

`jar verified.` is the expected result. The `self-signed certificate` warning is normal for a
locally generated key and is not a failure.

## Release procedure

1. Merge the sprint branch into `main` (PR, reviewed).
2. Tag the release: `git tag v1.0.0 && git push origin v1.0.0`.
3. The **Signed Release** workflow builds and verifies the artifacts.
4. Download `signed-release` from the workflow run and upload the `.aab` to the Play Console.
5. Keep `mapping.txt` from the same artifact — it deobfuscates release crash reports.

## Rotation

Rotate only if the key is compromised or lost. With Play App Signing enabled, Google holds the app
signing key and this upload key can be replaced through the Play Console without changing the app
identity. Without Play App Signing, rotation forces every installed copy to be uninstalled first.

After rotating: update all four secrets, run the workflow, and confirm the new SHA-256 in
`apksigner --print-certs` matches the new keystore.
