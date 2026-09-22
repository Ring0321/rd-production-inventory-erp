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
$suffix = [Guid]::NewGuid().ToString("N").Substring(0, 12)
$cleanDatabase = "rd_erp_c2_clean_test_$suffix"
$upgradeDatabase = "rd_erp_c2_upgrade_test_$suffix"
$createdDatabases = [System.Collections.Generic.List[string]]::new()
$tables = @(
    "rd_pickup_slip", "rd_pickup_slip_line", "rd_loading_task", "rd_loading_task_line",
    "rd_loading_package_claim", "rd_loading_scan_record", "rd_loading_exception"
)
$tableListSql = ($tables | ForEach-Object { "'$_'" }) -join ","

function Import-LocalEnvironment {
    $envFile = Join-Path $repoRoot "deploy\local-infra\.env.local"
    if (-not (Test-Path -LiteralPath $envFile)) { return }
    foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#") -or -not $line.Contains("=")) { continue }
        $name, $value = $line.Split("=", 2)
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, "Process"))) {
            [Environment]::SetEnvironmentVariable($name, $value, "Process")
        }
    }
}

function Invoke-MySql {
    param([string]$Database, [string]$Sql)
    $arguments = @("--host=$DatabaseHost", "--port=$Port", "--user=$User",
        "--default-character-set=utf8mb4", "--batch", "--skip-column-names")
    if ($Database) { $arguments += "--database=$Database" }
    $arguments += "--execute=$Sql"
    $output = & $MySqlClient @arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "MySQL command failed: $($output -join [Environment]::NewLine)" }
    return @($output)
}

function Invoke-MySqlFile {
    param([string]$Database, [string]$Path)
    $arguments = @("--host=$DatabaseHost", "--port=$Port", "--user=$User",
        "--database=$Database", "--default-character-set=utf8mb4")
    $token = [Guid]::NewGuid().ToString("N")
    $stdout = Join-Path $env:TEMP "rd-loading-schema-$token.out"
    $stderr = Join-Path $env:TEMP "rd-loading-schema-$token.err"
    try {
        $process = Start-Process -FilePath $MySqlClient -ArgumentList $arguments `
            -RedirectStandardInput (Resolve-Path -LiteralPath $Path).Path `
            -RedirectStandardOutput $stdout -RedirectStandardError $stderr `
            -NoNewWindow -Wait -PassThru
        if ($process.ExitCode -ne 0) {
            $errorText = Get-Content -Raw -ErrorAction SilentlyContinue $stderr
            throw "MySQL script failed: $errorText"
        }
    }
    finally {
        Remove-Item -LiteralPath $stdout, $stderr -Force -ErrorAction SilentlyContinue
    }
}

function Get-SchemaFingerprint {
    param([string]$Database)
    $count = @((Invoke-MySql $Database "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ($tableListSql)"))[0]
    if ([int]$count -ne $tables.Count) { throw "Expected $($tables.Count) loading tables, found $count" }
    $columns = Invoke-MySql $Database @"
SELECT table_name, column_name, column_type, is_nullable, COALESCE(column_default, '<NULL>'), extra
FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ($tableListSql)
ORDER BY table_name, ordinal_position
"@
    $indexes = Invoke-MySql $Database @"
SELECT table_name, index_name, non_unique, seq_in_index, column_name
FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name IN ($tableListSql)
ORDER BY table_name, index_name, seq_in_index
"@
    return (($columns -join "`n") + "`n--INDEXES--`n" + ($indexes -join "`n"))
}

function Assert-Equal {
    param([string]$Name, [string]$Expected, [string]$Actual)
    if ($Expected -cne $Actual) { throw "$Name differs from the expected C2 schema" }
}

Import-LocalEnvironment
if (-not $Password) { $Password = $env:RD_DB_PASSWORD }
if (-not $Password) { throw "Database password is required through -Password or deploy/local-infra/.env.local" }
if (-not $MySqlClient) { $MySqlClient = (Get-Command mysql -ErrorAction Stop).Source }
if (-not (Test-Path -LiteralPath $MySqlClient)) { throw "mysql client not found: $MySqlClient" }

$previousMySqlPassword = $env:MYSQL_PWD
$env:MYSQL_PWD = $Password
try {
    foreach ($database in @($cleanDatabase, $upgradeDatabase)) {
        if ($database -notmatch '^rd_erp_c2_(clean|upgrade)_test_[0-9a-f]{12}$') {
            throw "Refusing unexpected database name: $database"
        }
        $exists = @((Invoke-MySql "" "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$database'"))[0]
        if ([int]$exists -ne 0) { throw "Refusing to replace existing database: $database" }
        [void](Invoke-MySql "" "CREATE DATABASE ``$database`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        $createdDatabases.Add($database)
    }

    Invoke-MySqlFile $cleanDatabase $cleanSql
    $cleanFingerprint = Get-SchemaFingerprint $cleanDatabase

    Invoke-MySqlFile $upgradeDatabase $cleanSql
    [void](Invoke-MySql $upgradeDatabase @"
DROP TABLE IF EXISTS rd_loading_package_claim, rd_loading_exception, rd_loading_scan_record,
  rd_loading_task_line, rd_loading_task, rd_pickup_slip_line, rd_pickup_slip;
"@)
    Invoke-MySqlFile $upgradeDatabase $upgradeSql
    $firstUpgradeFingerprint = Get-SchemaFingerprint $upgradeDatabase
    Invoke-MySqlFile $upgradeDatabase $upgradeSql
    $secondUpgradeFingerprint = Get-SchemaFingerprint $upgradeDatabase
    Assert-Equal "clean versus upgraded" $cleanFingerprint $firstUpgradeFingerprint
    Assert-Equal "first versus repeated upgrade" $firstUpgradeFingerprint $secondUpgradeFingerprint

    [void](Invoke-MySql $cleanDatabase @"
INSERT INTO rd_pickup_slip (id,tenant_id,slip_no,customer_name)
VALUES (11,1,'C2-SLIP','C2-CUSTOMER');
INSERT INTO rd_pickup_slip_line (id,tenant_id,slip_id,line_no,product_model,requested_weight)
VALUES (21,1,11,1,'MODEL-A',1000), (22,1,11,2,'MODEL-B',1000);
INSERT INTO rd_loading_task (id,tenant_id,task_no,pickup_slip_id,pickup_slip_no,customer_name)
VALUES (1001,1,'C2-TASK',11,'C2-SLIP','C2-CUSTOMER');
INSERT INTO rd_loading_task_line
  (id,tenant_id,task_id,line_no,pickup_slip_line_id,package_id,package_code,stock_lot_id,
   planned_location_id,planned_location_code,customer_name,product_model,furnace_no,furnace_layer,
   package_weight,outbound_order_id)
VALUES
  (2001,1,1001,1,21,900001,'C2-PKG-1',301,401,'A-01','C2-CUSTOMER','MODEL-A','F-1','UPPER',1000,501),
  (2002,1,1001,2,22,900002,'C2-PKG-2',302,402,'B-02','C2-CUSTOMER','MODEL-B','F-2','LOWER',1000,502);
"@)
    $mixedTask = @((Invoke-MySql $cleanDatabase @"
SELECT CONCAT(COUNT(*),'|',COUNT(DISTINCT planned_location_id),'|',
              COUNT(DISTINCT product_model),'|',COUNT(DISTINCT furnace_layer),'|',
              COUNT(DISTINCT outbound_order_id))
FROM rd_loading_task_line WHERE task_id=1001 AND tenant_id=1
"@))[0]
    Assert-Equal "one task with mixed goods and outbound orders" "2|2|2|2|2" $mixedTask

    [void](Invoke-MySql $cleanDatabase @"
INSERT INTO rd_loading_package_claim (tenant_id,package_id,task_id,task_line_id)
VALUES (1,900001,1001,2001);
INSERT IGNORE INTO rd_loading_package_claim (tenant_id,package_id,task_id,task_line_id)
VALUES (1,900001,1002,2002);
"@)
    $claimCount = @((Invoke-MySql $cleanDatabase "SELECT COUNT(*) FROM rd_loading_package_claim WHERE tenant_id=1 AND package_id=900001"))[0]
    Assert-Equal "one active claim per package" "1" $claimCount
    [void](Invoke-MySql $cleanDatabase "DELETE FROM rd_loading_package_claim WHERE tenant_id=1 AND package_id=900001")
    [void](Invoke-MySql $cleanDatabase "INSERT INTO rd_loading_package_claim (tenant_id,package_id,task_id,task_line_id) VALUES (1,900001,1002,2002)")
    $reassignedTask = @((Invoke-MySql $cleanDatabase "SELECT task_id FROM rd_loading_package_claim WHERE tenant_id=1 AND package_id=900001"))[0]
    Assert-Equal "released claim can be reassigned" "1002" $reassignedTask

    [void](Invoke-MySql $cleanDatabase @"
INSERT INTO rd_loading_scan_record
  (tenant_id,task_id,task_line_id,package_id,package_code,plan_version,idempotency_key,scan_result,success,scan_time)
VALUES (1,1001,2001,900001,'C2-PKG-1',1,'retry-key','ACCEPTED',b'1',NOW());
INSERT IGNORE INTO rd_loading_scan_record
  (tenant_id,task_id,task_line_id,package_id,package_code,plan_version,idempotency_key,scan_result,success,scan_time)
VALUES (1,1001,2001,900001,'C2-PKG-1',1,'retry-key','ACCEPTED',b'1',NOW());
"@)
    $scanCount = @((Invoke-MySql $cleanDatabase "SELECT COUNT(*) FROM rd_loading_scan_record WHERE tenant_id=1 AND task_id=1001 AND task_line_id=2001 AND package_id=900001"))[0]
    Assert-Equal "one result per idempotency key" "1" $scanCount

    Write-Host "PASS: clean initialization and pre-C2 upgrade have identical loading schema."
    Write-Host "PASS: repeated upgrade leaves the loading schema unchanged."
    Write-Host "PASS: one loading task supports mixed locations, models, layers, and outbound orders."
    Write-Host "PASS: active package claim and scan idempotency unique constraints work."
}
finally {
    if (-not $KeepDatabases) {
        foreach ($database in $createdDatabases) {
            [void](Invoke-MySql "" "DROP DATABASE ``$database``")
        }
    }
    $env:MYSQL_PWD = $previousMySqlPassword
}
