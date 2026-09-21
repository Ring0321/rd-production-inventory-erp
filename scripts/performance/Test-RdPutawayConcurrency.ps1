param(
    [string]$ApiBase = 'http://127.0.0.1:48080/admin-api',
    [string]$TenantId = '1',
    [string]$Database = 'rd_erp',
    [string]$AdminPassword = $env:RD_TEST_PASSWORD,
    [string]$DatabasePassword = $env:RD_DB_PASSWORD,
    [string]$RedisPassword = $env:RD_REDIS_PASSWORD
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $repoRoot 'scripts\verification\RdLocalTestSupport.ps1')
Import-RdLocalEnvironment -RepoRoot $repoRoot
if (-not $AdminPassword) {
    $AdminPassword = if ($env:RD_TEST_PASSWORD) { $env:RD_TEST_PASSWORD } else { $env:RD_ADMIN_INITIAL_PASSWORD }
}
if (-not $DatabasePassword) { $DatabasePassword = $env:RD_DB_PASSWORD }
if (-not $RedisPassword) { $RedisPassword = $env:RD_REDIS_PASSWORD }
if (-not $AdminPassword -or -not $DatabasePassword -or -not $RedisPassword) {
    throw 'Local test credentials are missing from the environment file'
}
Assert-RdLocalApiEndpoint -ApiBase $ApiBase

$stamp = Get-Date -Format 'yyyyMMddHHmmssfff'
$runId = "B6CONC$stamp"
$packageCode = "B6C$stamp"
$driverUserId = 602121
$driverUsername = 'rdputawayconcurrentdriver'
$locationId = $null
$productionId = $null
$packageId = $null
$taskId = $null
$driverToken = $null
$report = $null
$cleanedUp = $false
$fixtureStarted = $false

function Remove-TestData {
    $parts = @()
    if ($null -ne $taskId) {
        $parts += "DELETE FROM system_operate_log WHERE biz_id = $taskId AND type LIKE 'RD %';"
        $parts += "DELETE FROM rd_putaway_exception WHERE task_id = $taskId;"
        $parts += "DELETE FROM rd_putaway_scan_record WHERE task_id = $taskId;"
        $parts += "DELETE FROM rd_putaway_task_line WHERE task_id = $taskId;"
        $parts += "DELETE FROM rd_putaway_task WHERE id = $taskId;"
    }
    if ($null -ne $packageId) {
        $parts += "DELETE FROM rd_package_unit WHERE id = $packageId;"
    }
    if ($null -ne $productionId) {
        $parts += "DELETE FROM rd_production_output WHERE id = $productionId;"
    }
    if ($null -ne $locationId) {
        $parts += "DELETE FROM rd_location WHERE id = $locationId;"
    }
    $parts += "DELETE FROM system_operate_log WHERE user_id = $driverUserId;"
    $parts += "DELETE FROM infra_api_access_log WHERE user_id = $driverUserId;"
    $parts += "DELETE FROM system_login_log WHERE user_id = $driverUserId OR username = '$driverUsername';"
    $parts += "DELETE FROM system_oauth2_access_token WHERE user_id = $driverUserId;"
    $parts += "DELETE FROM system_oauth2_refresh_token WHERE user_id = $driverUserId;"
    $parts += "DELETE FROM system_user_role WHERE user_id = $driverUserId;"
    $parts += "DELETE FROM system_users WHERE id = $driverUserId OR username = '$driverUsername';"
    Invoke-RdLocalSql -Sql ($parts -join "`n") -Password $DatabasePassword -Database $Database
}

function Invoke-ConcurrentScan {
    param([Parameter(Mandatory = $true)][object[]]$Bodies)

    $client = [Net.Http.HttpClient]::new()
    $client.DefaultRequestHeaders.Authorization =
            [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $driverToken)
    $client.DefaultRequestHeaders.Add('tenant-id', $TenantId)
    $requests = @()
    $tasks = @()
    try {
        foreach ($body in $Bodies) {
            $request = [Net.Http.HttpRequestMessage]::new(
                    [Net.Http.HttpMethod]::Post,
                    ($ApiBase.TrimEnd('/') + '/rd/putaway-task/scan'))
            $json = $body | ConvertTo-Json -Depth 8 -Compress
            $request.Content = [Net.Http.StringContent]::new(
                    $json, [Text.Encoding]::UTF8, 'application/json')
            $requests += $request
            $tasks += $client.SendAsync($request)
        }
        [Threading.Tasks.Task]::WaitAll([Threading.Tasks.Task[]]$tasks)
        $results = @()
        foreach ($task in $tasks) {
            $httpResponse = $task.Result
            try {
                $results += ($httpResponse.Content.ReadAsStringAsync().Result | ConvertFrom-Json)
            } finally {
                $httpResponse.Dispose()
            }
        }
        return $results
    } finally {
        foreach ($request in $requests) {
            $request.Dispose()
        }
        $client.Dispose()
    }
}

try {
    Assert-RdLocalFixtureUsersVacant -UserIds @($driverUserId) `
            -Usernames @($driverUsername) -Password $DatabasePassword -Database $Database
    $fixtureStarted = $true
    $fixtureSql = @"
INSERT INTO system_users
  (id, username, password, nickname, remark, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT $driverUserId, '$driverUsername', password, 'B6 concurrent driver', 'B6 concurrency test', 0,
       'b6-test', NOW(), 'b6-test', NOW(), b'0', $TenantId
FROM system_users WHERE id = 1 AND tenant_id = $TenantId;
INSERT INTO system_user_role
  (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES ($driverUserId, 600008, 'b6-test', NOW(), 'b6-test', NOW(), b'0', $TenantId);
INSERT INTO rd_location
  (tenant_id, location_code, location_name, warehouse_code, warehouse_name, area_code, area_name,
   row_code, column_code, level_code, capacity_packages, capacity_weight, occupied_packages,
   occupied_weight, reserved_packages, reserved_weight, enabled, frozen, location_status,
   version, status, sort, remark, creator, updater, deleted)
VALUES
($TenantId, '$runId', '$runId', 'FINISHED', 'B6', 'B6', 'B6', 'C', '01', '1', 10, 10000,
 0, 0, 1, 1000, b'1', b'0', 'AVAILABLE', 0, 0, 1, '$runId', 'b6-test', 'b6-test', b'0');
INSERT INTO rd_production_output
  (tenant_id, production_date, customer_name, product_model, furnace_no, furnace_layer,
   output_qty, package_weight, output_weight, inbound_qty, status, source_sheet, remark,
   version, creator, updater, deleted)
VALUES
($TenantId, CURRENT_DATE(), '$runId', 'B6-MODEL', 'B6-FURNACE', 'LOWER', 1, 1000, 1000, 0,
 'CONFIRMED', 'B6', '$runId', 1, 'b6-test', 'b6-test', b'0');
"@
    Invoke-RdLocalSql -Sql $fixtureSql -Password $DatabasePassword -Database $Database
    $locationId = [long](Get-RdLocalSqlFirstRow -Sql "SELECT id FROM rd_location WHERE location_code = '$runId' AND deleted = b'0';" -Password $DatabasePassword -Database $Database)
    $productionId = [long](Get-RdLocalSqlFirstRow -Sql "SELECT id FROM rd_production_output WHERE customer_name = '$runId' AND deleted = b'0';" -Password $DatabasePassword -Database $Database)

    $workSql = @"
INSERT INTO rd_package_unit
  (tenant_id, package_code, qr_code, production_output_id, production_date, customer_name,
   product_name, product_model, furnace_no, furnace_layer, package_weight, status, version,
   remark, creator, updater, deleted)
VALUES
($TenantId, '$packageCode', 'https://id.tjldsic.com/pkg/$packageCode', $productionId, CURRENT_DATE(),
 '$runId', 'B6 product', 'B6-MODEL', 'B6-FURNACE', 'LOWER', 1000, 'INBOUND_PENDING', 1,
 '$runId', 'b6-test', 'b6-test', b'0');
INSERT INTO rd_putaway_task
  (tenant_id, task_no, status, planned_location_id, planned_location_code, package_count,
   scanned_package_count, exception_package_count, total_weight, warehouse_keeper_id,
   warehouse_keeper_name, forklift_driver_id, forklift_driver_name, assigned_time,
   version, remark, creator, updater, deleted)
VALUES
($TenantId, '$runId', 'ASSIGNED', $locationId, '$runId', 1, 0, 0, 1000, 1,
 'B6 warehouse', $driverUserId, 'B6 concurrent driver', NOW(), 0, '$runId',
 'b6-test', 'b6-test', b'0');
"@
    Invoke-RdLocalSql -Sql $workSql -Password $DatabasePassword -Database $Database
    $packageId = [long](Get-RdLocalSqlFirstRow -Sql "SELECT id FROM rd_package_unit WHERE package_code = '$packageCode' AND deleted = b'0';" -Password $DatabasePassword -Database $Database)
    $taskId = [long](Get-RdLocalSqlFirstRow -Sql "SELECT id FROM rd_putaway_task WHERE task_no = '$runId' AND deleted = b'0';" -Password $DatabasePassword -Database $Database)
    $lineSql = @"
INSERT INTO rd_putaway_task_line
  (tenant_id, task_id, line_no, package_id, package_code, line_status, customer_name,
   product_model, furnace_no, furnace_layer, package_weight, scan_count, version,
   remark, creator, updater, deleted)
VALUES
($TenantId, $taskId, 1, $packageId, '$packageCode', 'PENDING', '$runId', 'B6-MODEL',
 'B6-FURNACE', 'LOWER', 1000, 0, 0, '$runId', 'b6-test', 'b6-test', b'0');
"@
    Invoke-RdLocalSql -Sql $lineSql -Password $DatabasePassword -Database $Database

    Clear-RdPermissionCache -Password $RedisPassword -Keys @("user_role_ids:$driverUserId")
    $driverToken = Invoke-RdLogin -ApiBase $ApiBase -TenantId $TenantId `
            -Username $driverUsername -Password $AdminPassword
    $responses = @(Invoke-ConcurrentScan -Bodies @(
        @{
            id = $taskId; packageCode = $packageCode
            idempotencyKey = "$runId-A"; deviceCode = 'B6-PDA-A'
        },
        @{
            id = $taskId; packageCode = $packageCode
            idempotencyKey = "$runId-B"; deviceCode = 'B6-PDA-B'
        }
    ))

    if ($responses.Count -ne 2 -or @($responses | Where-Object { $_.code -ne 0 }).Count -ne 0) {
        throw 'Concurrent scan requests did not both return normal business responses'
    }
    $successCount = @($responses | Where-Object { $_.data.success -eq $true }).Count
    $duplicateCount = @($responses | Where-Object { $_.data.scanResult -eq 'DUPLICATE' }).Count
    if ($successCount -ne 1 -or $duplicateCount -ne 1) {
        throw "Expected one SUCCESS and one DUPLICATE response, got success=$successCount duplicate=$duplicateCount"
    }

    $taskState = (Get-RdLocalSqlFirstRow -Sql "SELECT scanned_package_count, status FROM rd_putaway_task WHERE id = $taskId;" -Password $DatabasePassword -Database $Database) -split "`t"
    $lineState = (Get-RdLocalSqlFirstRow -Sql "SELECT scan_count, line_status FROM rd_putaway_task_line WHERE task_id = $taskId;" -Password $DatabasePassword -Database $Database) -split "`t"
    $recordState = (Get-RdLocalSqlFirstRow -Sql "SELECT COUNT(*), COALESCE(SUM(success + 0), 0), COUNT(DISTINCT idempotency_key) FROM rd_putaway_scan_record WHERE task_id = $taskId;" -Password $DatabasePassword -Database $Database) -split "`t"
    if ([int]$taskState[0] -ne 1 -or $taskState[1] -ne 'PENDING_CONFIRMATION') {
        throw "Task progress was counted incorrectly: $($taskState -join ',')"
    }
    if ([int]$lineState[0] -ne 1 -or $lineState[1] -ne 'SCANNED') {
        throw "Task line was counted incorrectly: $($lineState -join ',')"
    }
    if ([int]$recordState[0] -ne 2 -or [int]$recordState[1] -ne 1 -or [int]$recordState[2] -ne 2) {
        throw "Scan records were persisted incorrectly: $($recordState -join ',')"
    }

    $report = [pscustomobject]@{
        runId = $runId
        taskId = $taskId
        responseResults = @($responses | ForEach-Object { $_.data.scanResult })
        successfulScans = $successCount
        duplicateScans = $duplicateCount
        taskScannedPackageCount = [int]$taskState[0]
        taskStatus = $taskState[1]
        lineScanCount = [int]$lineState[0]
        persistedScanRecords = [int]$recordState[0]
        distinctIdempotencyKeys = [int]$recordState[2]
    }
} finally {
    Invoke-RdLogoutQuietly -ApiBase $ApiBase -TenantId $TenantId -AccessToken $driverToken
    if ($fixtureStarted) {
        Remove-TestData
        Clear-RdPermissionCache -Password $RedisPassword -Keys @("user_role_ids:$driverUserId")
        $cleanedUp = $true
    }
}

if ($null -ne $report) {
    $report | Add-Member -NotePropertyName cleanedUp -NotePropertyValue $cleanedUp
    $report | ConvertTo-Json -Depth 8
}
