param(
    [ValidateSet("All", "Kankanews")]
    [string]$Mode = "All",
    [switch]$NoClear,
    [switch]$ProbeOnly
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$WorkspaceRoot = Split-Path -Parent (Split-Path -Parent $ProjectRoot)
$LogDir = Join-Path $WorkspaceRoot "tests\logs"
$AdbHome = Join-Path $LogDir "adb-home"
$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$LogFile = Join-Path $LogDir "xingshitv-logcat-$Timestamp.txt"

$AllPattern = "KANKAN|HLS_PROXY|PLAYER_TEST|ChannelCatalog|JSTV|MGTV|WEBVIEW_TEST|AndroidRuntime|FATAL EXCEPTION"
$KankanewsPattern = "KANKAN|HLS_PROXY|PLAYER_TEST|AndroidRuntime|FATAL EXCEPTION"
$Pattern = if ($Mode -eq "Kankanews") { $KankanewsPattern } else { $AllPattern }

function Write-Info {
    param([string]$Message)
    Write-Host ("[{0}] {1}" -f (Get-Date -Format "HH:mm:ss"), $Message)
}

function Ensure-Directory {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Force -Path $Path | Out-Null
    }
}

function Read-Properties {
    param([string]$Path)
    $Result = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $Result
    }
    Get-Content -LiteralPath $Path -Encoding UTF8 | ForEach-Object {
        $Line = $_.Trim()
        if ($Line.Length -eq 0 -or $Line.StartsWith("#")) {
            return
        }
        $Index = $Line.IndexOf("=")
        if ($Index -le 0) {
            return
        }
        $Key = $Line.Substring(0, $Index).Trim()
        $Value = $Line.Substring($Index + 1).Trim()
        $Result[$Key] = $Value
    }
    return $Result
}

function Get-AdbCandidates {
    $Candidates = New-Object System.Collections.Generic.List[string]

    $PathAdb = Get-Command adb.exe -ErrorAction SilentlyContinue
    if (-not $PathAdb) {
        $PathAdb = Get-Command adb -ErrorAction SilentlyContinue
    }
    if ($PathAdb) {
        $Candidates.Add($PathAdb.Source)
    }

    $PathNoxAdb = Get-Command nox_adb.exe -ErrorAction SilentlyContinue
    if (-not $PathNoxAdb) {
        $PathNoxAdb = Get-Command nox_adb -ErrorAction SilentlyContinue
    }
    if ($PathNoxAdb) {
        $Candidates.Add($PathNoxAdb.Source)
    }

    foreach ($Name in @("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
        $Sdk = [Environment]::GetEnvironmentVariable($Name)
        if ($Sdk) {
            $Candidates.Add((Join-Path $Sdk "platform-tools\adb.exe"))
        }
    }

    $LocalConfig = Read-Properties (Join-Path $ProjectRoot "build-local.properties")
    if ($LocalConfig["android.sdk.dir"]) {
        $Candidates.Add((Join-Path $LocalConfig["android.sdk.dir"] "platform-tools\adb.exe"))
    }

    $LocalProperties = Read-Properties (Join-Path $ProjectRoot "local.properties")
    if ($LocalProperties["sdk.dir"]) {
        $SdkDir = $LocalProperties["sdk.dir"].Replace("\:", ":").Replace("\\", "\")
        $Candidates.Add((Join-Path $SdkDir "platform-tools\adb.exe"))
    }

    $NoxAdbProcess = Get-Process -ErrorAction SilentlyContinue |
            Where-Object { $_.ProcessName -ieq "nox_adb" -and $_.Path } |
            Select-Object -First 1
    if ($NoxAdbProcess) {
        $Candidates.Add($NoxAdbProcess.Path)
    }

    foreach ($Base in @($env:ProgramFiles, ${env:ProgramFiles(x86)})) {
        if ($Base) {
            $Candidates.Add((Join-Path $Base "Nox\bin\nox_adb.exe"))
            $Candidates.Add((Join-Path $Base "Bignox\BigNoxVM\RT\nox_adb.exe"))
        }
    }

    return @($Candidates |
            Where-Object { $_ -and (Test-Path -LiteralPath $_) } |
            Select-Object -Unique)
}

function Resolve-Adb {
    $Candidates = @(Get-AdbCandidates)
    foreach ($Candidate in $Candidates) {
        try {
            $Output = & $Candidate devices 2>&1
            $Text = ($Output | Out-String)
            if ($LASTEXITCODE -eq 0 -and $Text -notmatch "Cannot mkdir '\\\\.android'") {
                return $Candidate
            }
            Write-Info "Skip adb candidate: $Candidate"
        } catch {
            Write-Info "Skip adb candidate: $Candidate"
        }
    }
    return $null
}

function Invoke-Adb {
    param(
        [string]$Adb,
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Args
    )
    & $Adb @Args
}

function Get-AdbDevices {
    param([string]$Adb)
    $Output = Invoke-Adb $Adb devices
    $Devices = @()
    foreach ($Line in $Output) {
        if ($Line -match "^\s*([^\s]+)\s+(device|offline|unauthorized)\s*$") {
            $Devices += [PSCustomObject]@{
                Serial = $Matches[1]
                State = $Matches[2]
            }
        }
    }
    return $Devices
}

function Connect-NoxPorts {
    param([string]$Adb)
    foreach ($Port in @(62001, 62025, 62026, 62027, 62028, 62029)) {
        $Serial = "127.0.0.1:$Port"
        try {
            $Result = (Invoke-Adb $Adb connect $Serial | Out-String).Trim()
            if ($Result -match "connected|already connected") {
                Write-Info "Nox connect $Serial : $Result"
            }
        } catch {
            Write-Info "Nox connect $Serial failed"
        }
    }
}

function Select-NoxDevice {
    param([object[]]$Devices)

    $Ready = @($Devices | Where-Object { $_.State -eq "device" })
    $NoxLike = @($Ready | Where-Object {
        $_.Serial -match "^127\.0\.0\.1:62\d+$" -or $_.Serial -match "^emulator-"
    })

    if ($NoxLike.Count -eq 1) {
        return $NoxLike[0].Serial
    }

    if ($NoxLike.Count -gt 1) {
        Write-Info "Multiple emulator-like devices detected. Stop auto selection:"
        $NoxLike | ForEach-Object { Write-Host ("  {0}  {1}" -f $_.Serial, $_.State) }
        return $null
    }

    if ($Ready.Count -eq 1 -and $Ready[0].Serial -match "^127\.0\.0\.1:") {
        return $Ready[0].Serial
    }

    if ($Ready.Count -gt 0) {
        Write-Info "Android devices detected, but none can be reliably identified as Nox:"
        $Ready | ForEach-Object { Write-Host ("  {0}  {1}" -f $_.Serial, $_.State) }
        return $null
    }

    return $null
}

Ensure-Directory $LogDir
$AdbHomeForEnv = $AdbHome
Ensure-Directory $AdbHomeForEnv
$env:ANDROID_SDK_HOME = $AdbHomeForEnv
$env:ANDROID_USER_HOME = $AdbHomeForEnv
$env:USERPROFILE = $AdbHomeForEnv
$env:HOME = $AdbHomeForEnv
$AdbHomeRoot = [System.IO.Path]::GetPathRoot($AdbHomeForEnv).TrimEnd("\")
$AdbHomePath = $AdbHomeForEnv.Substring([System.IO.Path]::GetPathRoot($AdbHomeForEnv).Length - 1)
$env:HOMEDRIVE = $AdbHomeRoot
$env:HOMEPATH = $AdbHomePath

$Adb = Resolve-Adb
if (-not $Adb) {
    Write-Info "adb not found. Check PATH, Android SDK platform-tools, or Nox adb."
    exit 1
}

Write-Info "ADB: $Adb"
Write-Info "Mode: $Mode"
Write-Info "Log file: $LogFile"
Write-Info "Filter: $Pattern"

$Devices = @(Get-AdbDevices $Adb)
$Device = Select-NoxDevice $Devices

if (-not $Device) {
    Write-Info "Nox emulator not detected. Trying common Nox ports."
    Connect-NoxPorts $Adb
    $Devices = @(Get-AdbDevices $Adb)
    $Device = Select-NoxDevice $Devices
}

if (-not $Device) {
    Write-Info "Nox emulator not detected. Start Nox and run this script again."
    exit 1
}

Write-Info "Device: $Device"

if ($ProbeOnly) {
    Write-Info "ProbeOnly: adb and device detection finished. logcat was not started."
    exit 0
}

if ($NoClear) {
    Write-Info "Skip logcat clear because -NoClear was set."
} else {
    Write-Info "Clearing old logcat."
    try {
        Invoke-Adb $Adb -s $Device logcat -c | Out-Null
    } catch {
        Write-Info "logcat clear failed; continue capturing current logcat."
    }
}

Write-Info "Monitoring XingShiTV logcat. Press Ctrl+C to stop."
Write-Info "Kankanews mode: .\tools\monitor-nox-log.ps1 -Mode Kankanews"

try {
    Invoke-Adb $Adb -s $Device logcat -v time 2>&1 | ForEach-Object {
        $Line = [string]$_
        if ($Line -match $Pattern) {
            Write-Host $Line
            Add-Content -LiteralPath $LogFile -Encoding UTF8 -Value $Line
        }
    }
} finally {
    $Size = 0
    if (Test-Path -LiteralPath $LogFile) {
        $Size = (Get-Item -LiteralPath $LogFile).Length
    }
    Write-Info "Log monitor stopped."
    Write-Info "Device: $Device"
    Write-Info "Log file: $LogFile"
    Write-Info "Log size: $Size bytes"
}
