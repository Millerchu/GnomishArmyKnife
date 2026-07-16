$ErrorActionPreference = "Stop"

$scriptDirectory = $PSScriptRoot
$backendRoot = Split-Path -Parent $scriptDirectory
$pidFile = Join-Path $scriptDirectory ".backend.pid"
$appJar = Join-Path $backendRoot "gak-start/target/gak-start-1.0.0-SNAPSHOT.jar"

function Get-ManagedBackendProcess {
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

    return $processInfo
}

$managedProcess = Get-ManagedBackendProcess
if ($null -eq $managedProcess) {
    if (Test-Path -LiteralPath $pidFile) {
        Remove-Item -LiteralPath $pidFile -Force
        Write-Host "Backend PID file was stale; no unrelated process was stopped."
    }
    else {
        Write-Host "Backend is not running."
    }
    return
}

Write-Host "Stopping backend (PID $($managedProcess.ProcessId))..."
Stop-Process -Id $managedProcess.ProcessId -Force

$deadline = (Get-Date).AddSeconds(10)
do {
    Start-Sleep -Milliseconds 250
    $stillRunning = Get-CimInstance Win32_Process -Filter "ProcessId = $($managedProcess.ProcessId)" -ErrorAction SilentlyContinue
} while ($null -ne $stillRunning -and (Get-Date) -lt $deadline)

if ($null -ne $stillRunning) {
    throw "Backend process $($managedProcess.ProcessId) did not stop."
}

Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
Write-Host "Backend stopped."
