[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$runtimeRoot = Join-Path $repoRoot "temp\local-runtime"
$composeFile = Join-Path $repoRoot "deploy\local-infra\compose.yaml"
$envFile = Join-Path $repoRoot "deploy\local-infra\.env.local"
$infraModeFile = Join-Path $runtimeRoot "infrastructure-mode.txt"

if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $separator = $line.IndexOf("=")
        if ($separator -gt 0) {
            [Environment]::SetEnvironmentVariable(
                $line.Substring(0, $separator),
                $line.Substring($separator + 1),
                "Process"
            )
        }
    }
}

function Test-DockerReady {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & docker info *> $null
        return $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $previousPreference
    }
}

function Stop-ManagedProcess([string]$name) {
    $pidFile = Join-Path $runtimeRoot "$name.pid"
    if (-not (Test-Path -LiteralPath $pidFile)) {
        return
    }
    $processId = 0
    if ([int]::TryParse((Get-Content -Raw -LiteralPath $pidFile).Trim(), [ref]$processId)) {
        $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
        if ($process) {
            Stop-Process -Id $processId -Force
            Write-Host "Stopped $name process $processId"
        }
    }
    Remove-Item -LiteralPath $pidFile -Force
}

foreach ($name in @("frontend", "backend")) {
    Stop-ManagedProcess $name
}

$infrastructureMode = if (Test-Path -LiteralPath $infraModeFile) {
    (Get-Content -Raw -LiteralPath $infraModeFile).Trim()
} else {
    "Unknown"
}

if ($infrastructureMode -eq "Local" -or $infrastructureMode -eq "Unknown") {
    Stop-ManagedProcess "redis"

    $mysqlPidFile = Join-Path $runtimeRoot "mysql.pid"
    if (Test-Path -LiteralPath $mysqlPidFile) {
        $mysqlAdmin = Get-Command mysqladmin.exe -ErrorAction SilentlyContinue
        if ($mysqlAdmin -and $env:RD_DB_PASSWORD -and $env:RD_DB_PORT) {
            $env:MYSQL_PWD = $env:RD_DB_PASSWORD
            $oldPreference = $ErrorActionPreference
            $ErrorActionPreference = "Continue"
            try {
                & $mysqlAdmin.Source --host=127.0.0.1 --port=$env:RD_DB_PORT --user=root shutdown *> $null
            } finally {
                $ErrorActionPreference = $oldPreference
                Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
            }
            Start-Sleep -Seconds 2
        }
        Stop-ManagedProcess "mysql"
    }
}

if (($infrastructureMode -eq "Docker" -or $infrastructureMode -eq "Unknown") -and (Test-DockerReady) -and (Test-Path -LiteralPath $envFile)) {
    & docker compose --env-file $envFile -f $composeFile stop
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to stop local Docker services"
    }
}

Remove-Item -LiteralPath $infraModeFile -Force -ErrorAction SilentlyContinue

Write-Host "DUOXU ERP local services stopped. Database data was preserved."
