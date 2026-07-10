param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repoRoot "backend"
$composeFile = Join-Path $repoRoot "deploy\local-infra\compose.yaml"

docker compose -f $composeFile up -d
if ($LASTEXITCODE -ne 0) {
    throw "Failed to start local Docker infrastructure"
}

$maven = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source
if (-not $maven) {
    $bundledMaven = "D:\Develop\IntelliJ IDEA Community Edition 2025.2.2\plugins\maven\lib\maven3\bin\mvn.cmd"
    if (Test-Path $bundledMaven) {
        $maven = $bundledMaven
    } else {
        throw "mvn.cmd was not found"
    }
}

if (-not $SkipBuild) {
    Push-Location $backendRoot
    try {
        & $maven -pl yudao-dependencies,yudao-server -am -DskipTests install
        if ($LASTEXITCODE -ne 0) {
            throw "Backend build failed"
        }
    } finally {
        Pop-Location
    }
}

$env:RD_MASTER_DATASOURCE_URL = "jdbc:mysql://127.0.0.1:3307/rd_erp?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&nullCatalogMeansCurrent=true&rewriteBatchedStatements=true"
$env:RD_SLAVE_DATASOURCE_URL = $env:RD_MASTER_DATASOURCE_URL
$env:RD_DB_USERNAME = "root"
$env:RD_DB_PASSWORD = "rd_local_2026"
$env:RD_REDIS_HOST = "127.0.0.1"
$env:RD_REDIS_PORT = "6379"

$java = (Get-Command java.exe -ErrorAction SilentlyContinue).Source
if (-not $java) {
    throw "java.exe was not found"
}

$serverJar = Join-Path $backendRoot "yudao-server\target\yudao-server.jar"
if (-not (Test-Path $serverJar)) {
    throw "Backend executable was not found; retry without -SkipBuild"
}

& $java -jar $serverJar "--spring.profiles.active=local"
if ($LASTEXITCODE -ne 0) {
    throw "Backend startup failed"
}
