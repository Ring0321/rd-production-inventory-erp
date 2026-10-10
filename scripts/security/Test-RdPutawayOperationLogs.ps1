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
$runId = "B6AUDIT$stamp"
$packageCode = "B6A$stamp"
$putawayTypeHex = [BitConverter]::ToString(
        [Text.Encoding]::UTF8.GetBytes(
                ('RD ' + [char]0x5165 + [char]0x5E93 + [char]0x4E0A + [char]0x67B6))).Replace('-', '')
$warehouseUserId = 602111
$driverUserId = 602112
$userIds = "$warehouseUserId,$driverUserId"
$usernames = "'rdputawayauditwarehouse','rdputawayauditdriver'"
$locationAId = $null
$locationBId = $null
$productionId = $null
$packageId = $null
$taskId = $null
$inboundOrderId = $null
$stockLotId = $null
$warehouseToken = $null
$driverToken = $null
$report = $null
$cleanedUp = $false
$fixtureStarted = $false

function Remove-TestData {
    $parts = @()
    if ($null -ne $taskId) {
        $parts += "DELETE FROM system_operate_log WHERE biz_id = $taskId AND HEX(type) = '$putawayTypeHex';"
        $parts += "DELETE FROM rd_putaway_exception WHERE task_id = $taskId;"
        $parts += "DELETE FROM rd_putaway_scan_record WHERE task_id = $taskId;"
    }
    if ($null -ne $packageId) {
        $parts += "DELETE FROM rd_daily_stock_summary WHERE stock_lot_id IN (SELECT id FROM rd_stock_lot WHERE customer_name = '$runId');"
        $parts += "DELETE FROM rd_stock_transaction WHERE package_id = $packageId;"
        $parts += "DELETE FROM rd_inbound_order_line WHERE package_id = $packageId;"
    }
    if ($null -ne $inboundOrderId) {
        $parts += "DELETE FROM rd_inbound_order WHERE id = $inboundOrderId;"
    }
    if ($null -ne $stockLotId) {
        $parts += "DELETE FROM rd_stock_lot WHERE id = $stockLotId;"
    } else {
        $parts += "DELETE FROM rd_stock_lot WHERE customer_name = '$runId';"
    }
    if ($null -ne $taskId) {
        $parts += "DELETE FROM rd_putaway_task_line WHERE task_id = $taskId;"
        $parts += "DELETE FROM rd_putaway_task WHERE id = $taskId;"
    }
    if ($null -ne $packageId) {
        $parts += "DELETE FROM rd_package_unit WHERE id = $packageId;"
    }
    if ($null -ne $productionId) {
        $parts += "DELETE FROM rd_production_output WHERE id = $productionId;"
    }
    if ($null -ne $locationAId -or $null -ne $locationBId) {
        $locationIds = @($locationAId, $locationBId) | Where-Object { $null -ne $_ }
        $parts += "DELETE FROM rd_location WHERE id IN ($($locationIds -join ','));"
    }
    $parts += "DELETE FROM system_operate_log WHERE user_id IN ($userIds);"
    $parts += "DELETE FROM infra_api_access_log WHERE user_id IN ($userIds);"
    $parts += "DELETE FROM system_login_log WHERE user_id IN ($userIds) OR username IN ($usernames);"
    $parts += "DELETE FROM system_oauth2_access_token WHERE user_id IN ($userIds);"
    $parts += "DELETE FROM system_oauth2_refresh_token WHERE user_id IN ($userIds);"
    $parts += "DELETE FROM system_user_role WHERE user_id IN ($userIds);"
    $parts += "DELETE FROM system_users WHERE id IN ($userIds) OR username IN ($usernames);"
    Invoke-RdLocalSql -Sql ($parts -join "`n") -Password $DatabasePassword -Database $Database
}

try {
    Assert-RdLocalFixtureUsersVacant -UserIds @($warehouseUserId, $driverUserId) `
            -Usernames @('rdputawayauditwarehouse', 'rdputawayauditdriver') `
            -Password $DatabasePassword -Database $Database
    $fixtureStarted = $true
    $fixtureSql = @"
INSERT INTO system_users
  (id, username, password, nickname, remark, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT $warehouseUserId, 'rdputawayauditwarehouse', password, 'B6 audit warehouse', 'B6 audit test', 0,
       'b6-test', NOW(), 'b6-test', NOW(), b'0', $TenantId
FROM system_users WHERE id = 1 AND tenant_id = $TenantId;
INSERT INTO system_users
  (id, username, password, nickname, remark, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT $driverUserId, 'rdputawayauditdriver', password, 'B6 audit driver', 'B6 audit test', 0,
       'b6-test', NOW(), 'b6-test', NOW(), b'0', $TenantId
FROM system_users WHERE id = 1 AND tenant_id = $TenantId;
INSERT INTO system_user_role (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id) VALUES
($warehouseUserId, 600002, 'b6-test', NOW(), 'b6-test', NOW(), b'0', $TenantId),
($driverUserId, 600008, 'b6-test', NOW(), 'b6-test', NOW(), b'0', $TenantId);
INSERT INTO rd_location
  (tenant_id, location_code, location_name, warehouse_code, warehouse_name, area_code, area_name,
   row_code, column_code, level_code, capacity_packages, capacity_weight, enabled, frozen,
   location_status, version, status, sort, remark, creator, updater, deleted)
VALUES
($TenantId, '${runId}A', '${runId}A', 'FINISHED', 'B6', 'B6', 'B6', 'A', '01', '1', 10, 10000,
 b'1', b'0', 'AVAILABLE', 0, 0, 1, '$runId', 'b6-test', 'b6-test', b'0'),
($TenantId, '${runId}B', '${runId}B', 'FINISHED', 'B6', 'B6', 'B6', 'B', '01', '1', 10, 10000,
 b'1', b'0', 'AVAILABLE', 0, 0, 2, '$runId', 'b6-test', 'b6-test', b'0');
INSERT INTO rd_production_output
  (tenant_id, production_date, customer_name, product_model, furnace_no, furnace_layer,
   output_qty, package_weight, output_weight, inbound_qty, status, source_sheet, remark,
   version, creator, updater, deleted)
VALUES
($TenantId, CURRENT_DATE(), '$runId', 'B6-MODEL', 'B6-FURNACE', 'UPPER', 1, 1000, 1000, 0,
 'CONFIRMED', 'B6', '$runId', 1, 'b6-test', 'b6-test', b'0');
"@
    Invoke-RdLocalSql -Sql $fixtureSql -Password $DatabasePassword -Database $Database
    $locationAId = [long](Get-RdLocalSqlFirstRow -Sql "SELECT id FROM rd_location WHERE location_code = '${runId}A' AND deleted = b'0';" -Password $DatabasePassword -Database $Database)
    $locationBId = [long](Get-RdLocalSqlFirstRow -Sql "SELECT id FROM rd_location WHERE location_code = '${runId}B' AND deleted = b'0';" -Password $DatabasePassword -Database $Database)
    $productionId = [long](Get-RdLocalSqlFirstRow -Sql "SELECT id FROM rd_production_output WHERE customer_name = '$runId' AND deleted = b'0';" -Password $DatabasePassword -Database $Database)
    $packageSql = @"
INSERT INTO rd_package_unit
  (tenant_id, package_code, qr_code, production_output_id, production_date, customer_name,
   product_name, product_model, furnace_no, furnace_layer, package_weight, status, version,
   remark, creator, updater, deleted)
VALUES
($TenantId, '$packageCode', 'https://id.tjldsic.com/pkg/$packageCode', $productionId, CURRENT_DATE(),
 '$runId', 'B6 product', 'B6-MODEL', 'B6-FURNACE', 'UPPER', 1000, 'QC_PASSED', 0,
 '$runId', 'b6-test', 'b6-test', b'0');
"@
    Invoke-RdLocalSql -Sql $packageSql -Password $DatabasePassword -Database $Database
    $packageId = [long](Get-RdLocalSqlFirstRow -Sql "SELECT id FROM rd_package_unit WHERE package_code = '$packageCode' AND deleted = b'0';" -Password $DatabasePassword -Database $Database)

    Clear-RdPermissionCache -Password $RedisPassword -Keys @(
        "user_role_ids:$warehouseUserId", "user_role_ids:$driverUserId"
    )
    $warehouseToken = Invoke-RdLogin -ApiBase $ApiBase -TenantId $TenantId `
            -Username 'rdputawayauditwarehouse' -Password $AdminPassword
    $driverToken = Invoke-RdLogin -ApiBase $ApiBase -TenantId $TenantId `
            -Username 'rdputawayauditdriver' -Password $AdminPassword
    $warehouseHeaders = New-RdAuthHeaders -TenantId $TenantId -AccessToken $warehouseToken
    $driverHeaders = New-RdAuthHeaders -TenantId $TenantId -AccessToken $driverToken

    $create = Invoke-RdApiResponse -ApiBase $ApiBase -Method Post -Path '/rd/putaway-task/create' `
            -Headers $warehouseHeaders -Body @{
        plannedLocationId = $locationAId
        forkliftDriverId = $driverUserId
        packageIds = @($packageId)
        remark = $runId
    }
    if ($create.code -ne 0) { throw "Create task failed: $($create.msg)" }
    $taskId = [long]$create.data

    $exception = Invoke-RdApiResponse -ApiBase $ApiBase -Method Post -Path '/rd/putaway-task/exception' `
            -Headers $driverHeaders -Body @{
        id = $taskId
        exceptionType = 'LOCATION_OCCUPIED'
        packageCode = $packageCode
        description = $runId
    }
    if ($exception.code -ne 0) { throw "Report exception failed: $($exception.msg)" }

    $detail = Invoke-RdApiResponse -ApiBase $ApiBase -Method Get `
            -Path "/rd/putaway-task/get?id=$taskId" -Headers $warehouseHeaders -Body $null
    $reassign = Invoke-RdApiResponse -ApiBase $ApiBase -Method Put -Path '/rd/putaway-task/reassign' `
            -Headers $warehouseHeaders -Body @{
        id = $taskId
        plannedLocationId = $locationBId
        forkliftDriverId = $driverUserId
        version = [int]$detail.data.task.version
        handlingResult = $runId
    }
    if ($reassign.code -ne 0) { throw "Reassign task failed: $($reassign.msg)" }

    $scan = Invoke-RdApiResponse -ApiBase $ApiBase -Method Post -Path '/rd/putaway-task/scan' `
            -Headers $warehouseHeaders -Body @{
        id = $taskId
        packageCode = $packageCode
        idempotencyKey = "$runId-SCAN"
        deviceCode = 'B6-AUDIT-PDA'
    }
    if ($scan.code -ne 0 -or -not $scan.data.success) {
        throw "Scan failed: code=$($scan.code), result=$($scan.data.scanResult)"
    }

    $detail = Invoke-RdApiResponse -ApiBase $ApiBase -Method Get `
            -Path "/rd/putaway-task/get?id=$taskId" -Headers $warehouseHeaders -Body $null
    $confirm = Invoke-RdApiResponse -ApiBase $ApiBase -Method Put -Path '/rd/putaway-task/confirm' `
            -Headers $warehouseHeaders -Body @{
        id = $taskId
        finalLocationId = $locationBId
        version = [int]$detail.data.task.version
    }
    if ($confirm.code -ne 0) { throw "Confirm task failed: $($confirm.msg)" }
    $inboundOrderId = [long]$confirm.data

    $postingIds = (Get-RdLocalSqlFirstRow -Sql "SELECT inbound_order_id, stock_lot_id FROM rd_putaway_task_line WHERE task_id = $taskId AND deleted = b'0';" -Password $DatabasePassword -Database $Database) -split "`t"
    if ([long]$postingIds[0] -ne $inboundOrderId) {
        throw 'Putaway line is not linked to the posted inbound order'
    }
    $stockLotId = [long]$postingIds[1]
    $taskState = (Get-RdLocalSqlFirstRow -Sql "SELECT status, planned_location_id, final_location_id FROM rd_putaway_task WHERE id = $taskId;" -Password $DatabasePassword -Database $Database) -split "`t"
    $packageState = (Get-RdLocalSqlFirstRow -Sql "SELECT status, location_id, stock_lot_id FROM rd_package_unit WHERE id = $packageId;" -Password $DatabasePassword -Database $Database) -split "`t"
    $stockState = (Get-RdLocalSqlFirstRow -Sql "SELECT location_id, warehouse_qty, stock_weight FROM rd_stock_lot WHERE id = $stockLotId;" -Password $DatabasePassword -Database $Database) -split "`t"
    $previousLocationState = (Get-RdLocalSqlFirstRow -Sql "SELECT occupied_packages, reserved_packages FROM rd_location WHERE id = $locationAId;" -Password $DatabasePassword -Database $Database) -split "`t"
    $finalLocationState = (Get-RdLocalSqlFirstRow -Sql "SELECT occupied_packages, reserved_packages FROM rd_location WHERE id = $locationBId;" -Password $DatabasePassword -Database $Database) -split "`t"
    if ($taskState[0] -ne 'PUTAWAY_COMPLETED' -or [long]$taskState[1] -ne $locationBId -or
            [long]$taskState[2] -ne $locationBId -or $packageState[0] -ne 'IN_STOCK' -or
            [long]$packageState[1] -ne $locationBId -or [long]$packageState[2] -ne $stockLotId -or
            [long]$stockState[0] -ne $locationBId -or [decimal]$stockState[1] -ne 1 -or
            [decimal]$stockState[2] -ne 1000 -or [decimal]$previousLocationState[0] -ne 0 -or
            [decimal]$previousLocationState[1] -ne 0 -or [decimal]$finalLocationState[0] -ne 1 -or
            [decimal]$finalLocationState[1] -ne 0) {
        throw 'Task, package, location occupancy, and stock lot diverged after putaway confirmation'
    }

    $deadline = (Get-Date).AddSeconds(10)
    do {
        Start-Sleep -Milliseconds 300
        $auditSql = @"
SELECT COUNT(*), COALESCE(SUM(success + 0), 0), COUNT(DISTINCT sub_type),
       COUNT(DISTINCT user_id), SUM(user_id = $warehouseUserId), SUM(user_id = $driverUserId)
FROM system_operate_log
WHERE biz_id = $taskId AND HEX(type) = '$putawayTypeHex'
  AND user_id IN ($warehouseUserId, $driverUserId)
  AND create_time >= NOW() - INTERVAL 5 MINUTE;
"@
        $auditLine = Get-RdLocalSqlFirstRow -Sql $auditSql `
                -Password $DatabasePassword -Database $Database
        $audit = $auditLine -split "`t"
    } while ([int]$audit[0] -lt 5 -and (Get-Date) -lt $deadline)

    if ([int]$audit[0] -ne 5 -or [int]$audit[1] -ne 5 -or [int]$audit[2] -ne 5) {
        throw "Expected five successful putaway audit actions, got $auditLine"
    }
    if ([int]$audit[3] -ne 2 -or [int]$audit[4] -ne 4 -or [int]$audit[5] -ne 1) {
        throw "Putaway audit operator ownership is incorrect: $auditLine"
    }

    $report = [pscustomobject]@{
        runId = $runId
        taskId = $taskId
        operationLogCount = [int]$audit[0]
        successfulLogCount = [int]$audit[1]
        distinctActions = [int]$audit[2]
        distinctOperators = [int]$audit[3]
        warehouseActions = [int]$audit[4]
        driverActions = [int]$audit[5]
        postedLocationId = $locationBId
        stockLotQuantity = [decimal]$stockState[1]
    }
} finally {
    Invoke-RdLogoutQuietly -ApiBase $ApiBase -TenantId $TenantId -AccessToken $warehouseToken
    Invoke-RdLogoutQuietly -ApiBase $ApiBase -TenantId $TenantId -AccessToken $driverToken
    if ($fixtureStarted) {
        Remove-TestData
        Clear-RdPermissionCache -Password $RedisPassword -Keys @(
            "user_role_ids:$warehouseUserId", "user_role_ids:$driverUserId"
        )
        $cleanedUp = $true
    }
}

if ($null -ne $report) {
    $report | Add-Member -NotePropertyName cleanedUp -NotePropertyValue $cleanedUp
    $report | ConvertTo-Json -Depth 6
}
