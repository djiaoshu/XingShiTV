param(
    [switch]$Clean
)

$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ConfigFile = Join-Path $ProjectRoot "build-local.properties"
$ExampleFile = Join-Path $ProjectRoot "build-local.properties.example"

function Read-LocalProperties($path) {
    $result = @{}
    Get-Content -LiteralPath $path -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith("#")) {
            return
        }
        $index = $line.IndexOf("=")
        if ($index -le 0) {
            return
        }
        $key = $line.Substring(0, $index).Trim()
        $value = $line.Substring($index + 1).Trim()
        $result[$key] = $value
    }
    return $result
}

function Convert-SdkDirForLocalProperties($path) {
    return $path.Replace("\", "\\").Replace(":", "\:")
}

function Ensure-Directory($path) {
    if (-not (Test-Path -LiteralPath $path)) {
        New-Item -ItemType Directory -Force -Path $path | Out-Null
    }
}

function Get-ApplicationId {
    $gradleFile = Join-Path $ProjectRoot "app\build.gradle"
    $content = Get-Content -LiteralPath $gradleFile -Raw -Encoding UTF8
    $match = [regex]::Match($content, "applicationId\s+['""]([^'""]+)['""]")
    if (-not $match.Success) {
        return $null
    }
    return $match.Groups[1].Value
}

function Ensure-GeneratedRDirectory($buildRoot) {
    $applicationId = Get-ApplicationId
    if (-not $applicationId) {
        return
    }
    $packagePath = $applicationId.Replace(".", "\")
    foreach ($relative in @(
        "app\build\generated\source\r\debug\$packagePath",
        "app\build\generated\not_namespaced_r_class_sources\debug\processDebugResources\r\$packagePath"
    )) {
        Ensure-Directory (Join-Path $buildRoot $relative)
    }
}

if (-not (Test-Path -LiteralPath $ConfigFile)) {
    Write-Host "Missing local build config: $ConfigFile"
    Write-Host "Copy $ExampleFile to build-local.properties and set jdk8.dir/android.sdk.dir."
    exit 1
}

$config = Read-LocalProperties $ConfigFile
$Jdk8Dir = $config["jdk8.dir"]
$SdkDir = $config["android.sdk.dir"]

if (-not $Jdk8Dir -or -not (Test-Path -LiteralPath (Join-Path $Jdk8Dir "bin\java.exe"))) {
    throw "Invalid jdk8.dir in build-local.properties"
}
if (-not $SdkDir -or -not (Test-Path -LiteralPath $SdkDir)) {
    throw "Invalid android.sdk.dir in build-local.properties"
}

$localProperties = Join-Path $ProjectRoot "local.properties"
$sdkLine = "sdk.dir=$(Convert-SdkDirForLocalProperties $SdkDir)"
Set-Content -LiteralPath $localProperties -Encoding ASCII -Value $sdkLine

$env:JAVA_HOME = $Jdk8Dir
$env:Path = (Join-Path $Jdk8Dir "bin") + ";" + $env:Path
$env:GRADLE_USER_HOME = Join-Path $ProjectRoot ".gradle"
$env:JAVA_TOOL_OPTIONS = "-Duser.home=$(Join-Path $ProjectRoot ".gradle-user-home")"

Ensure-Directory $env:GRADLE_USER_HOME
Ensure-Directory (Join-Path $ProjectRoot ".gradle-user-home")
Ensure-GeneratedRDirectory $ProjectRoot

Push-Location $ProjectRoot
try {
    Write-Host "Using JAVA_HOME=$env:JAVA_HOME"
    Write-Host "Using Android SDK=$SdkDir"
    Write-Host "Using build root=$ProjectRoot"
    & java -version
    if ($Clean) {
        & .\gradlew.bat clean --console=plain
    }
    & .\gradlew.bat assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
