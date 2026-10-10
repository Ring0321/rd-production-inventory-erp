[CmdletBinding()]
param(
    [string]$DatabaseHost = "127.0.0.1",
    [int]$Port = 3307,
    [string]$User = "root",
    [string]$Password = "",
    [string]$MySqlClient = "",
    [switch]$KeepDatabases
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$cleanSql = Join-Path $repoRoot "backend\sql\mysql\rd_erp_clean.sql"
$upgradeSql = Join-Path $repoRoot "backend\sql\mysql\rd_erp_upgrade_slim.sql"
$cleanDatabase = "rd_erp_b2_clean_test_$PID"
$upgradeDatabase = "rd_erp_b2_upgrade_test_$PID"
$createdDatabases = [System.Collections.Generic.List[string]]::new()

function Import-LocalEnvironment {
    $envFile = Join-Path $repoRoot "deploy\local-infra\.env.local"
    if (-not (Test-Path -LiteralPath $envFile)) {
        return
    }
    foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#") -or -not $line.Contains("=")) {
            continue
        }
        $name, $value = $line.Split("=", 2)
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, "Process"))) {
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

function Invoke-MySql {
    param(
        [string]$Database,
        [string]$Sql
    )
    $arguments = @(
        "--host=$DatabaseHost",
        "--port=$Port",
        "--user=$User",
        "--default-character-set=utf8mb4",
        "--batch",
        "--skip-column-names"
    )
    if (-not [string]::IsNullOrWhiteSpace($Database)) {
        $arguments += "--database=$Database"
    }
    $arguments += "--execute=$Sql"
    $output = & $MySqlClient @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "MySQL command failed: $($output -join [Environment]::NewLine)"
    }
    return @($output)
}

function Invoke-MySqlFile {
    param(
        [string]$Database,
        [string]$Path
    )
    $resolvedPath = (Resolve-Path -LiteralPath $Path).Path
    $arguments = @(
        "--host=$DatabaseHost",
        "--port=$Port",
        "--user=$User",
        "--database=$Database",
        "--default-character-set=utf8mb4"
    )
    $token = [Guid]::NewGuid().ToString("N")
    $standardOutputPath = Join-Path $env:TEMP "rd-putaway-schema-$token.out"
    $standardErrorPath = Join-Path $env:TEMP "rd-putaway-schema-$token.err"
    try {
        $process = Start-Process -FilePath $MySqlClient -ArgumentList $arguments `
            -RedirectStandardInput $resolvedPath -RedirectStandardOutput $standardOutputPath `
            -RedirectStandardError $standardErrorPath -NoNewWindow -Wait -PassThru
        if ($process.ExitCode -ne 0) {
            $errorText = Get-Content -Raw -ErrorAction SilentlyContinue $standardErrorPath
            throw "MySQL script failed for '$resolvedPath': $errorText"
        }
    }
    finally {
        Remove-Item -LiteralPath $standardOutputPath, $standardErrorPath -Force -ErrorAction SilentlyContinue
    }
}

function Invoke-Scalar {
    param(
        [string]$Database,
        [string]$Sql
    )
    $result = @(Invoke-MySql -Database $Database -Sql $Sql)
    if ($result.Count -ne 1) {
        throw "Expected one scalar result but received $($result.Count): $($result -join ', ')"
    }
    return [string]$result[0]
}

function Assert-Equal {
    param(
        [string]$Name,
        [string]$Expected,
        [string]$Actual
    )
    if ($Expected -ne $Actual) {
        throw "$Name expected '$Expected' but received '$Actual'"
    }
}

function Get-SchemaFingerprint {
    param([string]$Database)
    $tableCount = Invoke-Scalar $Database @"
SELECT COUNT(*) FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND table_name IN ('rd_putaway_task','rd_putaway_task_line','rd_putaway_scan_record','rd_putaway_exception','rd_putaway_batch_scan_record');
"@
    $locationColumnCount = Invoke-Scalar $Database @"
SELECT COUNT(*) FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'rd_location'
  AND column_name IN ('row_code','column_code','level_code','capacity_weight','occupied_packages',
                      'occupied_weight','reserved_packages','reserved_weight','location_status','version');
"@
    $putawayColumnCount = Invoke-Scalar $Database @"
SELECT COUNT(*) FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name IN ('rd_putaway_task','rd_putaway_task_line','rd_putaway_scan_record','rd_putaway_exception','rd_putaway_batch_scan_record');
"@
    $indexCount = Invoke-Scalar $Database @"
SELECT COUNT(DISTINCT CONCAT(table_name, ':', index_name)) FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND index_name IN ('idx_rd_location_map','idx_rd_location_availability','uk_rd_putaway_task_no',
                     'idx_rd_putaway_task_status','idx_rd_putaway_task_driver','idx_rd_putaway_task_keeper',
                     'idx_rd_putaway_task_plan_location','uk_rd_putaway_line_no','uk_rd_putaway_line_package',
                     'idx_rd_putaway_line_task','idx_rd_putaway_line_package','uk_rd_putaway_scan_idempotency',
                     'idx_rd_putaway_scan_task','idx_rd_putaway_scan_package','idx_rd_putaway_scan_operator',
                     'uk_rd_putaway_exception_no','idx_rd_putaway_exception_task',
                     'idx_rd_putaway_exception_handler','idx_rd_putaway_exception_package',
                     'uk_rd_putaway_batch_key','idx_rd_putaway_batch_task');
"@
    $rowCount = Invoke-Scalar $Database @"
SELECT (SELECT COUNT(*) FROM rd_putaway_task)
     + (SELECT COUNT(*) FROM rd_putaway_task_line)
     + (SELECT COUNT(*) FROM rd_putaway_scan_record)
     + (SELECT COUNT(*) FROM rd_putaway_exception)
     + (SELECT COUNT(*) FROM rd_putaway_batch_scan_record);
"@
    $driverMenuCount = Invoke-Scalar $Database @"
SELECT COUNT(*) FROM system_menu
WHERE id = 600216 AND permission = 'rd:putaway-task:execute'
  AND component = 'rd/pda/putaway/index' AND deleted = b'0';
"@
    $driverGrantCount = Invoke-Scalar $Database @"
SELECT COUNT(*) FROM system_role_menu
WHERE tenant_id = 1 AND role_id = 600008 AND menu_id IN (600000,600200,600216,600272)
  AND deleted = b'0';
"@
    $warehouseConfirmGrantCount = Invoke-Scalar $Database @"
SELECT COUNT(*) FROM system_role_menu rm
JOIN system_menu m ON m.id = rm.menu_id AND m.deleted = b'0'
WHERE rm.tenant_id = 1 AND rm.role_id = 600002 AND rm.deleted = b'0'
  AND m.id = 600273 AND m.permission = 'rd:putaway-task:confirm';
"@
    $readOnlyQueryGrantCount = Invoke-Scalar $Database @"
SELECT COUNT(*) FROM system_role_menu
WHERE tenant_id = 1 AND role_id IN (600004,600005) AND menu_id = 600215
  AND deleted = b'0';
"@
    $readOnlyMutationGrantCount = Invoke-Scalar $Database @"
SELECT COUNT(*) FROM system_role_menu
WHERE tenant_id = 1 AND role_id IN (600004,600005)
  AND menu_id IN (600270,600271,600272,600273) AND deleted = b'0';
"@

    Assert-Equal "putaway table count" "5" $tableCount
    Assert-Equal "location extension column count" "10" $locationColumnCount
    Assert-Equal "putaway column count" "127" $putawayColumnCount
    Assert-Equal "putaway index count" "21" $indexCount
    Assert-Equal "putaway seed row count" "0" $rowCount
    Assert-Equal "forklift driver PDA menu count" "1" $driverMenuCount
    Assert-Equal "forklift driver minimum grant count" "4" $driverGrantCount
    Assert-Equal "warehouse putaway confirm grant count" "1" $warehouseConfirmGrantCount
    Assert-Equal "audit and readonly putaway query grant count" "2" $readOnlyQueryGrantCount
    Assert-Equal "audit and readonly putaway mutation grant count" "0" $readOnlyMutationGrantCount
    return "$tableCount|$locationColumnCount|$putawayColumnCount|$indexCount|$rowCount|$driverMenuCount|$driverGrantCount|$warehouseConfirmGrantCount|$readOnlyQueryGrantCount|$readOnlyMutationGrantCount"
}

Import-LocalEnvironment
if ([string]::IsNullOrWhiteSpace($Password)) {
    $Password = $env:RD_DB_PASSWORD
}
if ([string]::IsNullOrWhiteSpace($Password)) {
    throw "Database password is required through -Password or deploy/local-infra/.env.local"
}
if ([string]::IsNullOrWhiteSpace($MySqlClient)) {
    $MySqlClient = (Get-Command mysql -ErrorAction Stop).Source
}
if (-not (Test-Path -LiteralPath $MySqlClient)) {
    throw "mysql client not found: $MySqlClient"
}

$previousMySqlPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $Password
try {
    foreach ($database in @($cleanDatabase, $upgradeDatabase)) {
        if ($database -notmatch '^rd_erp_b2_(clean|upgrade)_test_\d+$') {
            throw "Refusing to manage unexpected database name: $database"
        }
        [void](Invoke-MySql -Database "" -Sql "DROP DATABASE IF EXISTS ``$database``; CREATE DATABASE ``$database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;")
        $createdDatabases.Add($database)
    }

    Invoke-MySqlFile -Database $cleanDatabase -Path $cleanSql
    $cleanFingerprint = Get-SchemaFingerprint $cleanDatabase

    Invoke-MySqlFile -Database $upgradeDatabase -Path $cleanSql
    [void](Invoke-MySql -Database $upgradeDatabase -Sql @"
DROP TABLE IF EXISTS rd_putaway_exception, rd_putaway_batch_scan_record, rd_putaway_scan_record, rd_putaway_task_line, rd_putaway_task;
ALTER TABLE rd_location
  DROP INDEX idx_rd_location_map,
  DROP INDEX idx_rd_location_availability,
  DROP COLUMN version,
  DROP COLUMN location_status,
  DROP COLUMN reserved_weight,
  DROP COLUMN reserved_packages,
  DROP COLUMN occupied_weight,
  DROP COLUMN occupied_packages,
  DROP COLUMN capacity_weight,
  DROP COLUMN level_code,
  DROP COLUMN column_code,
  DROP COLUMN row_code;
"@)

    Invoke-MySqlFile -Database $upgradeDatabase -Path $upgradeSql
    $firstUpgradeFingerprint = Get-SchemaFingerprint $upgradeDatabase
    Invoke-MySqlFile -Database $upgradeDatabase -Path $upgradeSql
    $secondUpgradeFingerprint = Get-SchemaFingerprint $upgradeDatabase

    Assert-Equal "clean and upgraded schema" $cleanFingerprint $firstUpgradeFingerprint
    Assert-Equal "repeated upgrade schema" $firstUpgradeFingerprint $secondUpgradeFingerprint
    Write-Host "PASS: clean initialization created the putaway schema."
    Write-Host "PASS: upgrade added the schema to a pre-B2 database."
    Write-Host "PASS: the second upgrade produced no duplicate tables, columns, indexes, or seed rows."
}
finally {
    if (-not $KeepDatabases) {
        foreach ($database in $createdDatabases) {
            [void](Invoke-MySql -Database "" -Sql "DROP DATABASE IF EXISTS ``$database``;")
        }
    }
    $env:MYSQL_PWD = $previousMySqlPassword
}
