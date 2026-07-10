param(
    [string]$OutputDirectory,
    [string]$ContainerName = "rd-erp-mysql",
    [string]$Database = "rd_erp",
    [string]$User = "root",
    [string]$Password = "rd_local_2026",
    [int]$RetentionDays = 14
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($Database -notmatch '^[A-Za-z0-9_]+$' -or $User -notmatch '^[A-Za-z0-9_]+$') {
    throw "Database and user names may only contain letters, numbers, and underscores"
}
if ($ContainerName -notmatch '^[A-Za-z0-9_.-]+$') {
    throw "Invalid Docker container name"
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not $OutputDirectory) {
    $OutputDirectory = Join-Path $repoRoot "backups\mysql"
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$outputRoot = (Resolve-Path -LiteralPath $OutputDirectory).Path

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$baseName = "${Database}_${timestamp}"
$fileName = "$baseName.sql"
$backupPath = Join-Path $outputRoot $fileName
$hashPath = Join-Path $outputRoot "$baseName.sha256"
$manifestPath = Join-Path $outputRoot "$baseName.json"
$containerTempPath = "/tmp/$fileName"

try {
    & docker exec -e "MYSQL_PWD=$Password" $ContainerName sh -c `
            "mysqldump --user=$User --single-transaction --quick --routines --events --triggers --hex-blob --set-gtid-purged=OFF --default-character-set=utf8mb4 --no-tablespaces $Database > $containerTempPath"
    if ($LASTEXITCODE -ne 0) {
        throw "mysqldump failed"
    }

    & docker cp "${ContainerName}:$containerTempPath" $backupPath | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to copy backup from the MySQL container"
    }
} finally {
    & docker exec $ContainerName rm -f $containerTempPath 2>$null | Out-Null
}

$backupFile = Get-Item -LiteralPath $backupPath
if ($backupFile.Length -lt 1024) {
    throw "Backup file is unexpectedly small: $($backupFile.Length) bytes"
}
$hash = (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  $fileName" | Set-Content -LiteralPath $hashPath -Encoding ASCII

$manifest = [ordered]@{
    formatVersion = 1
    database = $Database
    container = $ContainerName
    createdAt = (Get-Date).ToString("o")
    fileName = $fileName
    sizeBytes = $backupFile.Length
    sha256 = $hash
    consistentSnapshot = $true
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

if ($RetentionDays -gt 0) {
    $cutoff = (Get-Date).AddDays(-$RetentionDays)
    Get-ChildItem -LiteralPath $outputRoot -File |
            Where-Object { $_.Name -like "${Database}_*" -and $_.LastWriteTime -lt $cutoff } |
            ForEach-Object {
                if ([IO.Path]::GetFullPath($_.DirectoryName) -ne [IO.Path]::GetFullPath($outputRoot)) {
                    throw "Refusing to remove a backup outside $outputRoot"
                }
                Remove-Item -LiteralPath $_.FullName -Force
            }
}

[pscustomobject]@{
    backupFile = $backupPath
    hashFile = $hashPath
    manifestFile = $manifestPath
    sizeBytes = $backupFile.Length
    sha256 = $hash
}
