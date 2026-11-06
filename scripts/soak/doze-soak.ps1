<#
.SYNOPSIS
    Overnight Doze / standby soak for the ShowMeOutfit morning alert (SMO-S4-02).

.DESCRIPTION
    Drives a connected device or emulator through a real Doze standby window and records:
      * whether the exact morning alarm stays registered,
      * whether the alert is actually delivered while the device is idle,
      * the battery delta per hour.

    The alarm is armed through the debug-only SoakAlarmReceiver (src/debug), because the
    production receiver is intentionally not exported. Install a *debug* APK.

.PARAMETER DurationMinutes
    Length of the standby window. Default 480 (8 hours, the ticket's overnight soak).

.PARAMETER SampleMinutes
    Sampling interval for the CSV. Default 15.

.PARAMETER AlarmDelaySeconds
    How far ahead the soak arms the alarm. Default 120. Must be 60..3600.

.PARAMETER ApkPath
    Optional debug APK to install with `adb install -r` before the soak.

.PARAMETER SkipInstall
    Use the APK already installed on the device.

.PARAMETER OutputDir
    Directory for the CSV / dumpsys snapshots. Default `scripts/soak/out`.

.EXAMPLE
    .\scripts\soak\doze-soak.ps1 -DurationMinutes 480 -ApkPath app\build\outputs\apk\debug\app-debug.apk
#>
[CmdletBinding()]
param(
    [string]$Serial = "",
    [int]$DurationMinutes = 480,
    [int]$SampleMinutes = 15,
    [int]$AlarmDelaySeconds = 120,
    [string]$ApkPath = "",
    [switch]$SkipInstall,
    [string]$OutputDir = "scripts/soak/out"
)

$ErrorActionPreference = "Stop"
$Package = "com.smoh.showmeoutfit"
$Channel = "morning_outfit_channel"
$ScheduleAction = "com.smoh.showmeoutfit.soak.SCHEDULE"
$Receiver = "$Package/.debug.SoakAlarmReceiver"

function Resolve-Adb {
    if ($env:ADB) { return $env:ADB }
    $candidate = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $candidate) { return $candidate }
    $onPath = Get-Command adb -ErrorAction SilentlyContinue
    if ($onPath) { return $onPath.Source }
    throw "adb not found. Set `$env:ADB or install the Android platform-tools."
}

$adb = Resolve-Adb
$adbArgs = @()
if ($Serial) { $adbArgs += @("-s", $Serial) }

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Command)
    $output = & $adb @adbArgs @Command 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($Command -join ' ') failed:`n$output"
    }
    return ($output -join "`n")
}

function Get-BatteryLevel {
    $dump = Invoke-Adb shell dumpsys battery
    $match = [regex]::Match($dump, "level:\s*(\d+)")
    if (-not $match.Success) { return $null }
    return [int]$match.Groups[1].Value
}

function Test-PendingAlarm {
    $dump = Invoke-Adb shell dumpsys alarm
    $pending = $dump.Substring(0, [Math]::Max(0, $dump.IndexOf("Removal history:")))
    return ($pending.Contains($Package) -and $pending.Contains("com.smoh.showmeoutfit.action.MORNING_ALARM"))
}

function Get-DeepIdleState {
    return (Invoke-Adb shell dumpsys deviceidle get deep).Trim()
}

function Test-NotificationDelivered {
    $dump = Invoke-Adb shell dumpsys notification --noredact
    return ($dump.Contains($Channel) -and $dump.Contains($Package))
}

# ---------------------------------------------------------------- pre-flight
Write-Host "== ShowMeOutfit Doze soak ==" -ForegroundColor Cyan
$devices = & $adb devices
if ($devices -notmatch "`tdevice") {
    throw "No device/emulator is attached.`n$devices"
}
if (-not $Serial) {
    $Serial = (($devices -split "`n") | Where-Object { $_ -match "`tdevice" } | Select-Object -First 1) -split "`t" | Select-Object -First 1
}

$installed = (& $adb @adbArgs shell pm list packages $Package) -match $Package
if (-not $SkipInstall) {
    if (-not $ApkPath) {
        throw "Pass -ApkPath <debug apk> or -SkipInstall."
    }
    if (-not (Test-Path $ApkPath)) { throw "APK not found: $ApkPath" }
    Write-Host "Installing $ApkPath ..."
    Invoke-Adb install -r $ApkPath | Out-Null
}
elseif (-not $installed) {
    throw "$Package is not installed and -SkipInstall was set."
}

# Exact alarms and notifications have to be granted for the soak to mean anything.
Invoke-Adb shell appops set $Package SCHEDULE_EXACT_ALARM allow | Out-Null
Invoke-Adb shell pm grant $Package android.permission.POST_NOTIFICATIONS 2>$null | Out-Null

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$csvPath = Join-Path $OutputDir "doze-$stamp.csv"
$alarmDumpPath = Join-Path $OutputDir "alarm-$stamp.txt"

# ---------------------------------------------------------------- arm + idle
Invoke-Adb shell dumpsys battery unplug | Out-Null
$startLevel = Get-BatteryLevel
$startedAt = Get-Date

Write-Host "Arming the morning alarm $AlarmDelaySeconds s ahead ..."
Invoke-Adb shell am broadcast -a $ScheduleAction --ei seconds $AlarmDelaySeconds -n $Receiver | Out-Null
Start-Sleep -Seconds 2

if (-not (Test-PendingAlarm)) {
    throw "The alarm was not registered. Is this a debug build with the soak receiver?"
}

Write-Host "Forcing Doze ..."
Invoke-Adb shell dumpsys deviceidle force-idle | Out-Null
$idle = Get-DeepIdleState
if ($idle -ne "IDLE") {
    throw "The device did not enter Doze (deep state: $idle)."
}

"timestamp,batteryLevel,deepIdle,pendingAlarm,notificationDelivered" |
    Set-Content -Path $csvPath -Encoding utf8

$deadline = $startedAt.AddMinutes($DurationMinutes)
$delivered = $false
$samples = 0

while ((Get-Date) -lt $deadline) {
    $samples++
    $level = Get-BatteryLevel
    $deep = Get-DeepIdleState
    $pending = Test-PendingAlarm
    $delivered = $delivered -or (Test-NotificationDelivered)

    "{0},{1},{2},{3},{4}" -f (Get-Date -Format "o"), $level, $deep, $pending, $delivered |
        Add-Content -Path $csvPath -Encoding utf8

    Write-Host ("[{0:HH:mm:ss}] battery={1}% deep={2} pending={3} delivered={4}" -f (Get-Date), $level, $deep, $pending, $delivered)

    $sleep = [Math]::Min($SampleMinutes * 60, [Math]::Max(1, ($deadline - (Get-Date)).TotalSeconds))
    Start-Sleep -Seconds $sleep
}

# ---------------------------------------------------------------- collect
Invoke-Adb shell dumpsys alarm | Set-Content -Path $alarmDumpPath -Encoding utf8
$delivered = $delivered -or (Test-NotificationDelivered)
$endLevel = Get-BatteryLevel

Invoke-Adb shell dumpsys deviceidle unforce | Out-Null
Invoke-Adb shell dumpsys battery reset | Out-Null

$hours = ((Get-Date) - $startedAt).TotalHours
$delta = if ($null -ne $startLevel -and $null -ne $endLevel) { $startLevel - $endLevel } else { $null }
$perHour = if ($null -ne $delta -and $hours -gt 0) { [Math]::Round($delta / $hours, 2) } else { $null }

Write-Host ""
Write-Host "== Result ==" -ForegroundColor Cyan
Write-Host "Device        : $Serial"
Write-Host "Window        : {0:N2} h ($samples samples)" -f $hours
Write-Host "Alarm pending : $(Test-PendingAlarm)"
Write-Host "Delivered     : $delivered"
Write-Host "Battery       : $startLevel% -> $endLevel% (delta $delta pp, $perHour pp/h)"
Write-Host "CSV           : $csvPath"
Write-Host "Alarm dump    : $alarmDumpPath"

if (-not $delivered) {
    Write-Host "FAIL: the morning notification was not observed during the soak." -ForegroundColor Red
    exit 2
}

Write-Host "PASS: exact alarm survived Doze and the notification was delivered." -ForegroundColor Green
exit 0
