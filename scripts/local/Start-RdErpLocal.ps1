[CmdletBinding()]
param(
    [switch]$SkipBackendBuild,
    [switch]$SkipFrontendBuild,
    [ValidateSet("Auto", "Docker", "Local")]
    [string]$InfrastructureMode = "Auto",
    [string]$InstanceName = "",
    [int]$DbPort = 3307,
    [int]$RedisPort = 6379,
    [int]$BackendPort = 48080,
    [int]$WebPort = 9000,
    [int]$StartupTimeoutSeconds = 240
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$backendRoot = Join-Path $repoRoot "backend"
$frontendRoot = Join-Path $repoRoot "frontend"
$composeFile = Join-Path $repoRoot "deploy\local-infra\compose.yaml"
$runtimeRoot = Join-Path $repoRoot $(if ($InstanceName) { "temp\local-runtime-$InstanceName" } else { "temp\local-runtime" })
$envFile = if ($InstanceName) { Join-Path $runtimeRoot ".env.local" } else { Join-Path $repoRoot "deploy\local-infra\.env.local" }
$backendPidFile = Join-Path $runtimeRoot "backend.pid"
$frontendPidFile = Join-Path $runtimeRoot "frontend.pid"
$mysqlPidFile = Join-Path $runtimeRoot "mysql.pid"
$redisPidFile = Join-Path $runtimeRoot "redis.pid"
$infraModeFile = Join-Path $runtimeRoot "infrastructure-mode.txt"
$mysqlDataRoot = Join-Path ([Environment]::GetFolderPath("LocalApplicationData")) $(if ($InstanceName) { "RdErp\mysql-data-$InstanceName" } else { "RdErp\mysql-data" })
$garnetToolRoot = Join-Path $runtimeRoot "garnet"
$garnetDataRoot = Join-Path $runtimeRoot "garnet-data"

New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null

function New-LocalSecret([string]$prefix) {
    return "$prefix-$([guid]::NewGuid().ToString('N'))-Aa1!"
}

function Initialize-LocalEnvironment {
    if (-not (Test-Path -LiteralPath $envFile)) {
        $lines = @(
            "RD_DB_PASSWORD=$(New-LocalSecret 'Db')",
            "RD_REDIS_PASSWORD=$(New-LocalSecret 'Redis')",
            "RD_ADMIN_INITIAL_PASSWORD=$(New-LocalSecret 'Admin')",
            "RD_DB_PORT=$DbPort",
            "RD_REDIS_PORT=$RedisPort",
            "RD_BACKEND_PORT=$BackendPort",
            "RD_WEB_PORT=$WebPort"
        )
        [IO.File]::WriteAllLines($envFile, $lines, [Text.UTF8Encoding]::new($false))
    }

    foreach ($line in Get-Content -LiteralPath $envFile) {
        $trimmed = $line.Trim()
        if (-not $trimmed -or $trimmed.StartsWith("#")) {
            continue
        }
        $separator = $trimmed.IndexOf("=")
        if ($separator -lt 1) {
            throw "Invalid local environment entry: $trimmed"
        }
        $name = $trimmed.Substring(0, $separator)
        $value = $trimmed.Substring($separator + 1)
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

function Invoke-Checked([string]$filePath, [string[]]$arguments, [string]$workingDirectory) {
    Push-Location $workingDirectory
    try {
        & $filePath @arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$filePath failed with exit code $LASTEXITCODE"
        }
    } finally {
        Pop-Location
    }
}

function Get-ManagedProcess([string]$pidFile) {
    if (-not (Test-Path -LiteralPath $pidFile)) {
        return $null
    }
    $processId = 0
    if (-not [int]::TryParse((Get-Content -Raw -LiteralPath $pidFile).Trim(), [ref]$processId)) {
        Remove-Item -LiteralPath $pidFile -Force
        return $null
    }
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if (-not $process) {
        Remove-Item -LiteralPath $pidFile -Force
    }
    return $process
}

function Assert-PortAvailable([int]$port, [string]$pidFile) {
    $listener = Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue
    if (-not $listener) {
        return
    }
    $managedProcess = Get-ManagedProcess $pidFile
    if (-not $managedProcess -or $listener.OwningProcess -notcontains $managedProcess.Id) {
        throw "Port $port is already used by process $($listener.OwningProcess -join ',')"
    }
}

function Wait-ForUrl([string]$url, [int]$timeoutSeconds, [System.Diagnostics.Process]$process) {
    $deadline = [DateTime]::UtcNow.AddSeconds($timeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) {
                return
            }
        } catch {
        }
        if ($process -and $process.HasExited) {
            throw "Process $($process.Id) exited before $url became ready"
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for $url"
}

function Find-Maven {
    $command = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    $candidates = @()
    if ($env:MAVEN_HOME) {
        $candidates += (Join-Path $env:MAVEN_HOME "bin\mvn.cmd")
    }
    $candidates += "D:\Develop\IntelliJ IDEA Community Edition 2025.2.2\plugins\maven\lib\maven3\bin\mvn.cmd"
    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }
    throw "Maven was not found. Install Maven or set MAVEN_HOME."
}

function Wait-ForPort([int]$port, [int]$timeoutSeconds, [System.Diagnostics.Process]$process) {
    $deadline = [DateTime]::UtcNow.AddSeconds($timeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        $client = [Net.Sockets.TcpClient]::new()
        try {
            $pending = $client.BeginConnect("127.0.0.1", $port, $null, $null)
            if ($pending.AsyncWaitHandle.WaitOne(1000)) {
                $client.EndConnect($pending)
                return
            }
        } catch {
        } finally {
            $client.Dispose()
        }
        if ($process -and $process.HasExited) {
            throw "Process $($process.Id) exited before port $port became ready"
        }
        Start-Sleep -Seconds 1
    }
    throw "Timed out waiting for port $port"
}

function Find-MySqlTools {
    $server = Get-Command mysqld.exe -ErrorAction SilentlyContinue
    $client = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if (-not $server -or -not $client) {
        throw "Local fallback requires MySQL Server 8.x (mysqld.exe and mysql.exe)"
    }
    return [pscustomobject]@{
        Server = $server.Source
        Client = $client.Source
        BaseDir = Split-Path -Parent (Split-Path -Parent $server.Source)
    }
}

function Invoke-ProcessWithInput(
    [string]$filePath,
    [string[]]$arguments,
    [string]$inputFile,
    [string]$logName
) {
    $process = Start-Process -FilePath $filePath `
        -ArgumentList $arguments `
        -WorkingDirectory $repoRoot `
        -RedirectStandardInput $inputFile `
        -RedirectStandardOutput (Join-Path $runtimeRoot "$logName.out.log") `
        -RedirectStandardError (Join-Path $runtimeRoot "$logName.err.log") `
        -WindowStyle Hidden `
        -Wait `
        -PassThru
    if ($process.ExitCode -ne 0) {
        throw "$filePath failed with exit code $($process.ExitCode); see $logName.err.log"
    }
}

function Test-MySqlLogin([string]$clientPath, [int]$port, [string]$password) {
    $oldPassword = $env:MYSQL_PWD
    $oldPreference = $ErrorActionPreference
    try {
        if ([string]::IsNullOrEmpty($password)) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            $env:MYSQL_PWD = $password
        }
        $ErrorActionPreference = "Continue"
        & $clientPath --host=127.0.0.1 --port=$port --user=root --batch --skip-column-names --execute="SELECT 1" *> $null
        return $LASTEXITCODE -eq 0
    } finally {
        $ErrorActionPreference = $oldPreference
        if ($null -eq $oldPassword) {
            Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
        } else {
            $env:MYSQL_PWD = $oldPassword
        }
    }
}

function Start-LocalMySql([int]$port) {
    $process = Get-ManagedProcess $mysqlPidFile
    if ($process) {
        Wait-ForPort $port $StartupTimeoutSeconds $process
        return $process
    }

    Assert-PortAvailable $port $mysqlPidFile
    $tools = Find-MySqlTools
    $isFresh = -not (Test-Path -LiteralPath (Join-Path $mysqlDataRoot "mysql"))
    New-Item -ItemType Directory -Path $mysqlDataRoot -Force | Out-Null
    if ($isFresh) {
        Invoke-Checked $tools.Server @(
            "--no-defaults",
            "--initialize-insecure",
            "--basedir=`"$($tools.BaseDir)`"",
            "--datadir=`"$mysqlDataRoot`""
        ) $repoRoot
    }

    $process = Start-Process -FilePath $tools.Server `
        -ArgumentList @(
            "--no-defaults",
            "--standalone",
            "--basedir=`"$($tools.BaseDir)`"",
            "--datadir=`"$mysqlDataRoot`"",
            "--port=$port",
            "--bind-address=127.0.0.1",
            "--mysqlx-port=$($port + 30000)",
            "--server-id=2026",
            "--shared-memory-base-name=RD_ERP_MYSQL_$InstanceName",
            "--skip-log-bin",
            "--character-set-server=utf8mb4",
            "--collation-server=utf8mb4_unicode_ci",
            "--default-time-zone=+08:00",
            "--console"
        ) `
        -WorkingDirectory $repoRoot `
        -RedirectStandardOutput (Join-Path $runtimeRoot "mysql.log") `
        -RedirectStandardError (Join-Path $runtimeRoot "mysql.err.log") `
        -WindowStyle Hidden `
        -PassThru
    [IO.File]::WriteAllText($mysqlPidFile, [string]$process.Id)
    Wait-ForPort $port $StartupTimeoutSeconds $process

    $schemaMarker = Join-Path $mysqlDataRoot ".rd-erp-schema-ready"
    if (-not (Test-Path -LiteralPath $schemaMarker)) {
        $passwordReady = Test-MySqlLogin $tools.Client $port $env:RD_DB_PASSWORD
        if (-not $passwordReady) {
            if (-not (Test-MySqlLogin $tools.Client $port "")) {
                throw "Local MySQL data exists but its root password does not match deploy/local-infra/.env.local"
            }
            $escapedPassword = $env:RD_DB_PASSWORD.Replace("'", "''")
            $bootstrapFile = Join-Path $runtimeRoot "mysql-bootstrap.sql"
            [IO.File]::WriteAllText(
                $bootstrapFile,
                "ALTER USER 'root'@'localhost' IDENTIFIED BY '$escapedPassword';`nCREATE DATABASE IF NOT EXISTS rd_erp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`n",
                [Text.UTF8Encoding]::new($false)
            )
            Invoke-ProcessWithInput $tools.Client @(
                "--host=127.0.0.1",
                "--port=$port",
                "--user=root",
                "--default-character-set=utf8mb4"
            ) $bootstrapFile "mysql-bootstrap"
            Remove-Item -LiteralPath $bootstrapFile -Force
        }

        $env:MYSQL_PWD = $env:RD_DB_PASSWORD
        $schemaFile = Join-Path $backendRoot "sql\mysql\rd_erp_clean.sql"
        Invoke-ProcessWithInput $tools.Client @(
            "--host=127.0.0.1",
            "--port=$port",
            "--user=root",
            "--database=rd_erp",
            "--default-character-set=utf8mb4"
        ) $schemaFile "mysql-schema"
        [IO.File]::WriteAllText($schemaMarker, (Get-Date).ToString("O"))
    }
    return $process
}

function Apply-DatabaseUpgrade([int]$port) {
    $client = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if (-not $client) {
        throw "mysql.exe was not found; the repeatable database upgrade cannot be applied"
    }
    $env:MYSQL_PWD = $env:RD_DB_PASSWORD
    $upgradeFile = Join-Path $backendRoot "sql\mysql\rd_erp_upgrade_slim.sql"
    Invoke-ProcessWithInput $client.Source @(
        "--host=127.0.0.1",
        "--port=$port",
        "--user=root",
        "--database=rd_erp",
        "--default-character-set=utf8mb4"
    ) $upgradeFile "mysql-upgrade"
}

function Start-LocalRedis([int]$port) {
    $process = Get-ManagedProcess $redisPidFile
    if ($process) {
        Wait-ForPort $port $StartupTimeoutSeconds $process
        return $process
    }

    Assert-PortAvailable $port $redisPidFile
    $garnetExecutable = Join-Path $garnetToolRoot "garnet-server.exe"
    if (-not (Test-Path -LiteralPath $garnetExecutable)) {
        Invoke-Checked "dotnet" @(
            "tool", "install",
            "--tool-path", $garnetToolRoot,
            "garnet-server",
            "--version", "1.1.10",
            "--add-source", "https://api.nuget.org/v3/index.json"
        ) $repoRoot
    }
    New-Item -ItemType Directory -Path $garnetDataRoot -Force | Out-Null
    $process = Start-Process -FilePath $garnetExecutable `
        -ArgumentList @(
            "--port", [string]$port,
            "--bind", "127.0.0.1",
            "--auth", "Password",
            "--password", $env:RD_REDIS_PASSWORD,
            "--memory", "256m",
            "--index", "16m",
            "--protected-mode", "false"
        ) `
        -WorkingDirectory $garnetDataRoot `
        -RedirectStandardOutput (Join-Path $runtimeRoot "redis.log") `
        -RedirectStandardError (Join-Path $runtimeRoot "redis.err.log") `
        -WindowStyle Hidden `
        -PassThru
    [IO.File]::WriteAllText($redisPidFile, [string]$process.Id)
    Wait-ForPort $port $StartupTimeoutSeconds $process
    return $process
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

function Ensure-Docker {
    if (Test-DockerReady) {
        return
    }
    $docker = Get-Command docker.exe -ErrorAction SilentlyContinue
    if (-not $docker) {
        throw "Docker Desktop is not installed"
    }
    Start-Process -FilePath $docker.Source -ArgumentList @("desktop", "start") -WindowStyle Hidden | Out-Null
    $deadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        Start-Sleep -Seconds 3
        if (Test-DockerReady) {
            return
        }
    }
    throw "Docker Desktop did not become ready in time"
}

Initialize-LocalEnvironment

$dbPort = [int]$env:RD_DB_PORT
$redisPort = [int]$env:RD_REDIS_PORT
$backendPort = [int]$env:RD_BACKEND_PORT
$webPort = [int]$env:RD_WEB_PORT

Write-Host "[1/5] Starting MySQL and Redis..."
$resolvedInfrastructureMode = $InfrastructureMode
if ($resolvedInfrastructureMode -eq "Auto") {
    $resolvedInfrastructureMode = if (Test-DockerReady) { "Docker" } else { "Local" }
}
if ($resolvedInfrastructureMode -eq "Docker") {
    Ensure-Docker
    Invoke-Checked "docker" @("compose", "--env-file", $envFile, "-f", $composeFile, "up", "-d", "--wait") $repoRoot
} else {
    Start-LocalMySql $dbPort | Out-Null
    Start-LocalRedis $redisPort | Out-Null
}
Apply-DatabaseUpgrade $dbPort
[IO.File]::WriteAllText($infraModeFile, $resolvedInfrastructureMode)
Write-Host "Infrastructure: $resolvedInfrastructureMode"

$env:RD_MASTER_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:$dbPort/rd_erp?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true"
$env:RD_SLAVE_DATASOURCE_URL = $env:RD_MASTER_DATASOURCE_URL
$env:RD_DB_USERNAME = "root"
$env:RD_REDIS_HOST = "127.0.0.1"
$env:RD_CAPTCHA_ENABLED = "false"
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:LOG_FILE = Join-Path $runtimeRoot "application.log"

Write-Host "[2/5] Starting backend..."
$backendProcess = Get-ManagedProcess $backendPidFile
if (-not $backendProcess) {
    Assert-PortAvailable $backendPort $backendPidFile
    if (-not $SkipBackendBuild) {
        Invoke-Checked (Find-Maven) @("-pl", "rd-erp-server", "-am", "-DskipTests", "package") $backendRoot
    }
    $serverJar = Join-Path $backendRoot "rd-erp-server\target\rd-erp-server.jar"
    if (-not (Test-Path -LiteralPath $serverJar)) {
        throw "Backend JAR is missing; run again without -SkipBackendBuild"
    }
    $backendProcess = Start-Process -FilePath "java" `
        -ArgumentList @("-XX:MaxRAMPercentage=75", "-Dfile.encoding=UTF-8", "-jar", $serverJar, "--server.port=$backendPort") `
        -WorkingDirectory $backendRoot `
        -RedirectStandardOutput (Join-Path $runtimeRoot "backend.log") `
        -RedirectStandardError (Join-Path $runtimeRoot "backend.err.log") `
        -WindowStyle Hidden `
        -PassThru
    [IO.File]::WriteAllText($backendPidFile, [string]$backendProcess.Id)
}
Wait-ForUrl "http://127.0.0.1:$backendPort/admin-api/actuator/health" $StartupTimeoutSeconds $backendProcess

Write-Host "[3/5] Building frontend..."
$frontendProcess = Get-ManagedProcess $frontendPidFile
if (-not $frontendProcess) {
    Assert-PortAvailable $webPort $frontendPidFile
    if (-not $SkipFrontendBuild) {
        $registry = if ($env:RD_PNPM_REGISTRY) { $env:RD_PNPM_REGISTRY } else { "https://registry.npmmirror.com" }
        Invoke-Checked "pnpm" @("install", "--frozen-lockfile", "--registry=$registry") $frontendRoot
        Invoke-Checked "pnpm" @("build:prod") $frontendRoot
    }
    $distRoot = Join-Path $frontendRoot "dist-prod"
    if (-not (Test-Path -LiteralPath (Join-Path $distRoot "index.html"))) {
        throw "Frontend production build is missing; run again without -SkipFrontendBuild"
    }
    $env:FRONTEND_ROOT = $distRoot
    $env:API_TARGET = "http://127.0.0.1:$backendPort"
    $env:WEB_PORT = [string]$webPort
    $env:TENANT_ID = "1"
    $frontendProcess = Start-Process -FilePath "node" `
        -ArgumentList @(Join-Path $PSScriptRoot "Serve-RdErpFrontend.mjs") `
        -WorkingDirectory $repoRoot `
        -RedirectStandardOutput (Join-Path $runtimeRoot "frontend.log") `
        -RedirectStandardError (Join-Path $runtimeRoot "frontend.err.log") `
        -WindowStyle Hidden `
        -PassThru
    [IO.File]::WriteAllText($frontendPidFile, [string]$frontendProcess.Id)
}
Wait-ForUrl "http://127.0.0.1:$webPort/login" $StartupTimeoutSeconds $frontendProcess

Write-Host "[4/5] Verifying login and dashboard..."
$tenantHeaders = @{ "tenant-id" = "1" }
$loginBody = @{ username = "admin"; password = $env:RD_ADMIN_INITIAL_PASSWORD } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:$webPort/admin-api/system/auth/login" -Headers $tenantHeaders -ContentType "application/json" -Body $loginBody -TimeoutSec 15
if ($login.code -ne 0) {
    throw "Admin login verification failed: $($login.msg)"
}
$dashboardHeaders = @{
    "tenant-id" = "1"
    Authorization = "Bearer $($login.data.accessToken)"
}
$dashboard = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$webPort/admin-api/rd/dashboard/overview" -Headers $dashboardHeaders -TimeoutSec 15
if ($dashboard.code -ne 0) {
    throw "Dashboard verification failed: $($dashboard.msg)"
}

Write-Host "[5/5] RD ERP is ready."
Write-Host "Web:      http://127.0.0.1:$webPort/login"
Write-Host "Username: admin"
Write-Host "Password: $env:RD_ADMIN_INITIAL_PASSWORD"
Write-Host "Local credentials: $envFile"
