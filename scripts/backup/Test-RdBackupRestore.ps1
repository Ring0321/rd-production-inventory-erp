param(
    [string]$ContainerName = "rd-erp-mysql",
    [string]$SourceDatabase = "rd_erp",
    [string]$User = "root",
    [string]$Password = "rd_local_2026",
    [string]$OutputDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$targetDatabase = "rd_erp_restore_" + (Get-Date -Format "yyyyMMddHHmmss")
$scriptRoot = $PSScriptRoot
$targetCreated = $false
$tables = @(
    "rd_stock_lot",
    "rd_stock_transaction",
    "rd_sale_order_extend",
    "rd_order_fulfillment",
    "rd_transfer_order",
    "rd_outbound_order",
    "rd_excel_import_task",
    "rd_daily_stock_summary",
    "system_role",
    "system_role_menu"
)

function Get-TableCounts {
    param([string]$Database)
    $selects = @($tables | ForEach-Object { "SELECT '$_', COUNT(*) FROM ``$_``" })
    $sql = $selects -join " UNION ALL "
    $lines = @(& docker exec -e "MYSQL_PWD=$Password" $ContainerName mysql --user=$User `
            --database=$Database -N -B -e $sql)
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to read verification counts from $Database"
    }
    $counts = @{}
    foreach ($line in $lines) {
        $parts = $line -split "`t"
        $counts[$parts[0]] = [long]$parts[1]
    }
    return $counts
}

try {
    $backupParams = @{
        ContainerName = $ContainerName
        Database = $SourceDatabase
        User = $User
        Password = $Password
    }
    if ($OutputDirectory) {
        $backupParams.OutputDirectory = $OutputDirectory
    }
    $backup = & (Join-Path $scriptRoot "Backup-RdErp.ps1") @backupParams
    $restore = & (Join-Path $scriptRoot "Restore-RdErp.ps1") `
            -BackupFile $backup.backupFile `
            -TargetDatabase $targetDatabase `
            -ContainerName $ContainerName `
            -User $User `
            -Password $Password
    $targetCreated = $true

    $sourceCounts = Get-TableCounts -Database $SourceDatabase
    $targetCounts = Get-TableCounts -Database $targetDatabase
    $mismatches = @()
    foreach ($table in $tables) {
        if ($sourceCounts[$table] -ne $targetCounts[$table]) {
            $mismatches += "$table source=$($sourceCounts[$table]) target=$($targetCounts[$table])"
        }
    }
    if ($mismatches.Count -gt 0) {
        throw "Backup restore row-count mismatch: $($mismatches -join '; ')"
    }

    [pscustomobject]@{
        backupFile = $backup.backupFile
        sha256 = $backup.sha256
        restoredDatabase = $targetDatabase
        restoredTableCount = $restore.tableCount
        verifiedTables = $tables.Count
        rowCountComparison = "matched"
    }
} finally {
    if ($targetCreated) {
        & docker exec -e "MYSQL_PWD=$Password" $ContainerName mysql --user=$User `
                -e "DROP DATABASE IF EXISTS ``$targetDatabase``;" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            Write-Warning "Failed to remove temporary restore database $targetDatabase"
        }
    }
}
