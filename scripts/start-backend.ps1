$ErrorActionPreference = "Stop"

$scriptDirectory = $PSScriptRoot
$backendRoot = Split-Path -Parent $scriptDirectory
$reposRoot = Split-Path -Parent $backendRoot
$workspaceRoot = Split-Path -Parent $reposRoot
$pidFile = Join-Path $scriptDirectory ".backend.pid"
$appJar = Join-Path $backendRoot "gak-start/target/gak-start-1.0.0-SNAPSHOT.jar"
$logDirectory = Join-Path $backendRoot "logs"
$outputLog = Join-Path $logDirectory "backend.log"
$errorLog = Join-Path $logDirectory "backend-error.log"

function Get-ManagedBackendProcessId {
    if (-not (Test-Path -LiteralPath $pidFile)) {
        return $null
    }

    $pidText = (Get-Content -LiteralPath $pidFile -Raw).Trim()
    [int]$managedProcessId = 0
    if (-not [int]::TryParse($pidText, [ref]$managedProcessId)) {
        return $null
    }

    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $managedProcessId" -ErrorAction SilentlyContinue
    if ($null -eq $processInfo -or [string]::IsNullOrWhiteSpace($processInfo.CommandLine)) {
        return $null
    }

    if ($processInfo.CommandLine.IndexOf($appJar, [System.StringComparison]::OrdinalIgnoreCase) -lt 0) {
        return $null
    }

    return $managedProcessId
}

$existingProcessId = Get-ManagedBackendProcessId
if ($null -ne $existingProcessId) {
    Write-Host "Backend is already running (PID $existingProcessId)."
    return
}

if (Test-Path -LiteralPath $pidFile) {
    Remove-Item -LiteralPath $pidFile -Force
}

# The local password is defined in application.properties. Remove a stale
# session value so it cannot override the project configuration.
Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

$jdkScript = Join-Path $workspaceRoot "scripts/use-jdk21.ps1"
if (-not (Test-Path -LiteralPath $jdkScript)) {
    throw "Project JDK helper was not found at $jdkScript"
}

& $jdkScript -Quiet
if (-not $?) {
    throw "Project JDK 21 could not be configured."
}

$javaExecutable = Join-Path $env:JAVA_HOME "bin/java.exe"
if (-not (Test-Path -LiteralPath $javaExecutable)) {
    throw "JDK 21 java.exe was not found at $javaExecutable"
}

$buildArguments = @("-pl", "gak-start", "-am", "package", "-DskipTests")
Push-Location $backendRoot
try {
    Write-Host "Building backend..."
    & ".\mvnw.cmd" @buildArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Backend build failed with exit code $LASTEXITCODE."
    }
}
finally {
    Pop-Location
}

if (-not (Test-Path -LiteralPath $appJar)) {
    throw "Backend JAR was not produced at $appJar"
}

$backendProcess = Start-Process `
    -FilePath $javaExecutable `
    -ArgumentList @("-jar", $appJar) `
    -WorkingDirectory $backendRoot `
    -RedirectStandardOutput $outputLog `
    -RedirectStandardError $errorLog `
    -PassThru

Set-Content -LiteralPath $pidFile -Value $backendProcess.Id -NoNewline
$startupDeadline = (Get-Date).AddSeconds(30)
$startupConfirmed = $false
do {
    Start-Sleep -Milliseconds 500
    $startedProcessId = Get-ManagedBackendProcessId
    if ($null -eq $startedProcessId) {
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
        throw "Backend process exited during startup. See $outputLog"
    }

    $startupLog = Get-Content -LiteralPath $outputLog -Raw -ErrorAction SilentlyContinue
    if ($startupLog -like "*Started GnomishArmyKnifeApplication*") {
        $startupConfirmed = $true
    }
} while (-not $startupConfirmed -and (Get-Date) -lt $startupDeadline)

if (-not $startupConfirmed) {
    Stop-Process -Id $backendProcess.Id -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    throw "Backend did not finish startup within 30 seconds. See $outputLog"
}

Write-Host "Backend started (PID $startedProcessId)."
