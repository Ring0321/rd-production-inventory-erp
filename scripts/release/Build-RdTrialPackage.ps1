param(
    [string]$OutputDirectory,
    [switch]$SkipBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$backendRoot = Join-Path $repoRoot "backend"
$frontendRoot = Join-Path $repoRoot "frontend"
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $repoRoot "releases"
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$outputRoot = (Resolve-Path -LiteralPath $OutputDirectory).Path

if (-not $SkipBuild) {
    $mavenCommand = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    $maven = if ($mavenCommand) { $mavenCommand.Source } else { $null }
    if (-not $maven) {
        $bundledMaven = "D:\Develop\IntelliJ IDEA Community Edition 2025.2.2\plugins\maven\lib\maven3\bin\mvn.cmd"
        if (Test-Path -LiteralPath $bundledMaven) {
            $maven = $bundledMaven
        } else {
            throw "mvn.cmd was not found"
        }
    }
    Push-Location $backendRoot
    try {
        & $maven -pl rd-erp-server -am -DskipTests clean package
        if ($LASTEXITCODE -ne 0) {
            throw "Backend package build failed"
        }
    } finally {
        Pop-Location
    }

    $pnpmCommand = Get-Command pnpm.cmd -ErrorAction SilentlyContinue
    $pnpm = if ($pnpmCommand) { $pnpmCommand.Source } else { $null }
    if (-not $pnpm) {
        $bundledPnpm = "C:\Users\Ring\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\fallback\pnpm.cmd"
        if (Test-Path -LiteralPath $bundledPnpm) {
            $pnpm = $bundledPnpm
        } else {
            throw "pnpm.cmd was not found"
        }
    }
    Push-Location $frontendRoot
    try {
        & $pnpm build:prod
        if ($LASTEXITCODE -ne 0) {
            throw "Frontend production build failed"
        }
    } finally {
        Pop-Location
    }
}

$serverJar = Join-Path $backendRoot "rd-erp-server\target\rd-erp-server.jar"
$frontendDist = Join-Path $frontendRoot "dist-prod"
if (-not (Test-Path -LiteralPath $serverJar) -or -not (Test-Path -LiteralPath $frontendDist)) {
    throw "Build outputs are missing; run again without -SkipBuild"
}

$gitCommit = (& git -C $repoRoot rev-parse HEAD).Trim()
$gitShort = (& git -C $repoRoot rev-parse --short HEAD).Trim()
$dirty = [bool](& git -C $repoRoot status --porcelain)
$builtAt = [TimeZoneInfo]::ConvertTime([DateTimeOffset]::UtcNow, [TimeZoneInfo]::FindSystemTimeZoneById("China Standard Time"))
$timestamp = $builtAt.ToString("yyyyMMdd_HHmmss")
$packageBase = "duoxu-erp-trial-${gitShort}_${timestamp}"
$tempRoot = Join-Path $repoRoot "temp"
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
$stageRoot = Join-Path $tempRoot $packageBase
$zipPath = Join-Path $outputRoot "$packageBase.zip"
$hashPath = Join-Path $outputRoot "$packageBase.sha256"

try {
    New-Item -ItemType Directory -Path $stageRoot -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $stageRoot "backend") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $stageRoot "frontend") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $stageRoot "database") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $stageRoot "deploy") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $stageRoot "docs") -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $stageRoot "scripts") -Force | Out-Null

    Copy-Item -LiteralPath $serverJar -Destination (Join-Path $stageRoot "backend\rd-erp-server.jar")
    Copy-Item -Path (Join-Path $frontendDist "*") -Destination (Join-Path $stageRoot "frontend") -Recurse
    $trialTarget = Join-Path $stageRoot "deploy\trial"
    New-Item -ItemType Directory -Path $trialTarget -Force | Out-Null
    foreach ($name in @(".env.example", "application-trial.yaml", "backend-runtime.Dockerfile", "backend.Dockerfile", "compose.runtime.yaml", "compose.yaml", "frontend-runtime.Dockerfile", "frontend.Dockerfile", "nginx.conf", "README.md")) {
        Copy-Item -LiteralPath (Join-Path $repoRoot "deploy\trial\$name") -Destination (Join-Path $trialTarget $name)
    }
    Copy-Item -LiteralPath (Join-Path $repoRoot "scripts\backup") -Destination (Join-Path $stageRoot "scripts") -Recurse

    @(
        "rd_erp_clean.sql",
        "rd_erp_upgrade_slim.sql"
    ) | ForEach-Object {
        Copy-Item -LiteralPath (Join-Path $backendRoot "sql\mysql\$_") `
                -Destination (Join-Path $stageRoot "database\$_")
    }
    Copy-Item -Path (Join-Path $repoRoot "docs\product\*.md") -Destination (Join-Path $stageRoot "docs")

    @(
        "DUOXU ERP trial package",
        "Git commit: $gitCommit",
        "Dirty working tree: $dirty",
        "Built at: $($builtAt.ToString('o'))",
        "Web port: 9000"
    ) | Set-Content -LiteralPath (Join-Path $stageRoot "VERSION.txt") -Encoding UTF8

    Compress-Archive -LiteralPath $stageRoot -DestinationPath $zipPath -CompressionLevel Optimal -Force
    $stream = [IO.File]::OpenRead($zipPath)
    try { $hash = ([BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash($stream))).Replace("-", "").ToLowerInvariant() }
    finally { $stream.Dispose() }
    "$hash  $([IO.Path]::GetFileName($zipPath))" | Set-Content -LiteralPath $hashPath -Encoding ASCII
} finally {
    $resolvedTempRoot = [IO.Path]::GetFullPath($tempRoot).TrimEnd('\') + '\'
    $resolvedStage = [IO.Path]::GetFullPath($stageRoot)
    if ($resolvedStage.StartsWith($resolvedTempRoot, [StringComparison]::OrdinalIgnoreCase) -and
            (Test-Path -LiteralPath $resolvedStage)) {
        Remove-Item -LiteralPath $resolvedStage -Recurse -Force
    }
}

$zip = Get-Item -LiteralPath $zipPath
[pscustomobject]@{
    packageFile = $zipPath
    hashFile = $hashPath
    sizeBytes = $zip.Length
    sha256 = $hash
    gitCommit = $gitCommit
    dirtyWorkingTree = $dirty
}
