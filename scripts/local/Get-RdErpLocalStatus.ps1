[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$runtimeRoot = Join-Path $repoRoot "temp\local-runtime"
$envFile = Join-Path $repoRoot "deploy\local-infra\.env.local"
$infraModeFile = Join-Path $runtimeRoot "infrastructure-mode.txt"

$settings = @{
    RD_DB_PORT = "3307"
    RD_REDIS_PORT = "6379"
    RD_BACKEND_PORT = "48080"
    RD_WEB_PORT = "9000"
}
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }
        $separator = $trimmed.IndexOf("=")
        if ($separator -gt 0) {
            $settings[$trimmed.Substring(0, $separator)] = $trimmed.Substring($separator + 1)
        }
    }
}

function Test-Http([string]$url) {
    try {
        return (Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3).StatusCode -eq 200
    } catch {
        return $false
    }
}

function Test-Tcp([int]$port) {
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $pending = $client.BeginConnect("127.0.0.1", $port, $null, $null)
        if (-not $pending.AsyncWaitHandle.WaitOne(1000)) {
            return $false
        }
        $client.EndConnect($pending)
        return $true
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-PidState([string]$name) {
    $pidFile = Join-Path $runtimeRoot "$name.pid"
    if (-not (Test-Path -LiteralPath $pidFile)) {
        return "stopped"
    }
    $processId = 0
    if (-not [int]::TryParse((Get-Content -Raw -LiteralPath $pidFile).Trim(), [ref]$processId)) {
        return "invalid pid file"
    }
    if (Get-Process -Id $processId -ErrorAction SilentlyContinue) {
        return "running (PID $processId)"
    }
    return "stale pid file"
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

$dockerReady = Test-DockerReady
$infrastructureMode = if (Test-Path -LiteralPath $infraModeFile) {
    (Get-Content -Raw -LiteralPath $infraModeFile).Trim()
} else {
    "Unknown"
}
$mysqlState = Get-PidState "mysql"
$redisState = Get-PidState "redis"
$localInfrastructureReady = $mysqlState.StartsWith("running") -and
    $redisState.StartsWith("running") -and
    (Test-Tcp ([int]$settings.RD_DB_PORT)) -and
    (Test-Tcp ([int]$settings.RD_REDIS_PORT))
$infrastructureReady = if ($infrastructureMode -eq "Docker") {
    $dockerReady
} elseif ($infrastructureMode -eq "Local") {
    $localInfrastructureReady
} else {
    $dockerReady -or $localInfrastructureReady
}

$backendUrl = "http://127.0.0.1:$($settings.RD_BACKEND_PORT)/admin-api/actuator/health"
$frontendUrl = "http://127.0.0.1:$($settings.RD_WEB_PORT)/login"
$backendReady = Test-Http $backendUrl
$frontendReady = Test-Http $frontendUrl

@(
    [pscustomobject]@{ Service = "Infrastructure"; State = if ($infrastructureReady) { "ready" } else { "stopped" }; Detail = $infrastructureMode }
    [pscustomobject]@{ Service = "MySQL"; State = if ($infrastructureMode -eq "Docker") { "Docker managed" } else { $mysqlState }; Detail = "127.0.0.1:$($settings.RD_DB_PORT)" }
    [pscustomobject]@{ Service = "Redis"; State = if ($infrastructureMode -eq "Docker") { "Docker managed" } else { $redisState }; Detail = "127.0.0.1:$($settings.RD_REDIS_PORT)" }
    [pscustomobject]@{ Service = "Backend"; State = Get-PidState "backend"; Detail = if ($backendReady) { $backendUrl } else { "health check failed" } }
    [pscustomobject]@{ Service = "Web"; State = Get-PidState "frontend"; Detail = if ($frontendReady) { $frontendUrl } else { "page check failed" } }
) | Format-Table -AutoSize

if (-not ($infrastructureReady -and $backendReady -and $frontendReady)) {
    exit 1
}
