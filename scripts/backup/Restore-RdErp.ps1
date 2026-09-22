param(
    [Parameter(Mandatory = $true)][string]$BackupFile,
    [string]$TargetDatabase = "rd_erp_restore_verify",
    [string]$ContainerName = "rd-erp-mysql",
    [string]$User = "root",
    [string]$Password = $env:RD_DB_PASSWORD,
    [switch]$ReplaceTarget,
    [switch]$AllowPrimaryDatabase
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (-not $Password) { throw "Set RD_DB_PASSWORD or pass -Password" }

if ($TargetDatabase -notmatch '^[A-Za-z0-9_]+$' -or $User -notmatch '^[A-Za-z0-9_]+$') {
    throw "Database and user names may only contain letters, numbers, and underscores"
}
if ($ContainerName -notmatch '^[A-Za-z0-9_.-]+$') {
    throw "Invalid Docker container name"
}
if ($TargetDatabase -eq "rd_erp" -and -not $AllowPrimaryDatabase) {
    throw "Restoring over rd_erp requires the explicit -AllowPrimaryDatabase switch"
}

$resolvedBackup = (Resolve-Path -LiteralPath $BackupFile).Path
$hashFile = [IO.Path]::ChangeExtension($resolvedBackup, ".sha256")
$stream = [System.IO.File]::OpenRead($resolvedBackup)
try {
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        $actualHash = [System.BitConverter]::ToString($sha256.ComputeHash($stream)).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
} finally {
    $stream.Dispose()
}
if (Test-Path -LiteralPath $hashFile) {
    $expectedHash = ((Get-Content -LiteralPath $hashFile -Raw).Trim() -split '\s+')[0].ToLowerInvariant()
    if ($actualHash -ne $expectedHash) {
        throw "Backup SHA-256 verification failed"
    }
}

$exists = @(& docker exec -e "MYSQL_PWD=$Password" $ContainerName mysql --user=$User -N -B `
        -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '$TargetDatabase';")
if ($LASTEXITCODE -ne 0) {
    throw "Failed to inspect the target database"
}
if ($exists.Count -gt 0 -and -not $ReplaceTarget) {
    throw "Target database $TargetDatabase already exists; use -ReplaceTarget to replace it"
}

$containerTempPath = "/tmp/rd_restore_$([Guid]::NewGuid().ToString('N')).sql"
try {
    & docker cp $resolvedBackup "${ContainerName}:$containerTempPath" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to copy backup into the MySQL container"
    }

    if ($exists.Count -gt 0) {
        & docker exec -e "MYSQL_PWD=$Password" $ContainerName mysql --user=$User `
                -e "DROP DATABASE IF EXISTS ``$TargetDatabase``;"
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to remove the existing target database"
        }
    }
    & docker exec -e "MYSQL_PWD=$Password" $ContainerName mysql --user=$User `
            -e "CREATE DATABASE ``$TargetDatabase`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to create the target database"
    }

    & docker exec -e "MYSQL_PWD=$Password" $ContainerName sh -c `
            "mysql --user=$User $TargetDatabase < $containerTempPath"
    if ($LASTEXITCODE -ne 0) {
        throw "Database restore failed"
    }

    $tableCount = [int](@(& docker exec -e "MYSQL_PWD=$Password" $ContainerName mysql --user=$User -N -B `
            -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = '$TargetDatabase';")[0])
    if ($LASTEXITCODE -ne 0 -or $tableCount -eq 0) {
        throw "Restored database contains no tables"
    }
} finally {
    & docker exec $ContainerName rm -f $containerTempPath 2>$null | Out-Null
}

[pscustomobject]@{
    backupFile = $resolvedBackup
    targetDatabase = $TargetDatabase
    tableCount = $tableCount
    sha256 = $actualHash
}
