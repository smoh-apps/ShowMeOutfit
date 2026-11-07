# Doze / standby soak (SMO-S4-02)

The morning alert is only useful if it survives a night in Doze. The automated
`MorningAlarmDozeTest` proves the pending alarm survives idle transitions; this harness proves the
whole chain end to end and records the battery cost.

## Before you start

Launch the app once and finish onboarding (age, fit, sensitivity, and either a selected city or the
location permission). `MorningOutfitRoutine` returns `AlertsDisabled` for an unconfigured profile,
so a fresh install will keep the alarm registered but never post the notification — the soak would
end with exit code `2` for a reason unrelated to Doze.

The device also needs a network path to Open-Meteo (or a fresh cache), because the worker cannot
build a recommendation without weather.

## What it does

1. Installs (optionally) a **debug** APK and grants `SCHEDULE_EXACT_ALARM` + `POST_NOTIFICATIONS`.
2. Arms the real alarm through the debug-only `SoakAlarmReceiver` (`app/src/debug`).
3. Unplugs the battery and forces the device into Doze (`dumpsys deviceidle force-idle`).
4. Samples battery level, Doze state, pending-alarm state and notification presence into a CSV.
5. Wakes the device, then passes only if the morning notification was actually delivered.

The production `MorningAlarmReceiver` is not exported, so the soak cannot use it directly. The
debug receiver is restricted to `android.permission.DUMP` (shell/system only) and is absent from
release builds.

## Emulator run

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
.\scripts\soak\doze-soak.ps1 -DurationMinutes 480 `
    -ApkPath app\build\outputs\apk\debug\app-debug.apk
```

Short smoke version (2 minutes, 30-second alarm):

```powershell
.\scripts\soak\doze-soak.ps1 -DurationMinutes 2 -SampleMinutes 1 `
    -AlarmDelaySeconds 60 -ApkPath app\build\outputs\apk\debug\app-debug.apk
```

## Physical-device matrix (ticket requirement)

Repeat the command with `-Serial <device>` for each target. The script is device-agnostic.

| Target | API | Status |
| :--- | :--- | :--- |
| Emulator | 37 | Script + instrumented suite executed |
| Physical | 26 | **Not executed — no device attached** |
| Physical | 31 | **Not executed — no device attached** |
| Physical | 34 | **Not executed — no device attached** |

The Sprint 4 environment has no physical devices. The matrix is recorded honestly in
`sprint_4_qa_s4-02.md`; nothing here claims a physical-device result that was not measured.

## Reading the result

- `pendingAlarm` must stay `True` for the whole window.
- `delivered` flips to `True` once the notification channel has a posted notification.
- `delta pp/h` is the battery drain in percentage points per hour. For an idle overnight device a
  healthy result is a small single-digit value; compare against a baseline night with the alert
  disabled before calling a regression.
- Exit code `0` = delivered, `2` = not delivered, `1` = setup failure.

Outputs land in `scripts/soak/out/` (git-ignored).
