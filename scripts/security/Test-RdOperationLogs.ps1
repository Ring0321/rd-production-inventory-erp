param(
    [string]$ApiBase = "http://127.0.0.1:48080/admin-api",
    [string]$TenantId = "1",
    [string]$MySqlContainer = "rd-erp-mysql",
    [string]$AdminPassword = $env:RD_TEST_PASSWORD,
    [string]$DatabasePassword = $env:RD_DB_PASSWORD
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (-not $AdminPassword -or -not $DatabasePassword) { throw "Set RD_TEST_PASSWORD and RD_DB_PASSWORD" }
$ApiBase = $ApiBase.TrimEnd("/")
$apiUri = [Uri]$ApiBase
if ($apiUri.Host -notin @("127.0.0.1", "localhost") -or $apiUri.Port -ne 48080) {
    throw "This test only runs against the local backend on port 48080"
}

$runId = "AUDIT-" + (Get-Date -Format "yyyyMMddHHmmssfff")
$stockLotId = $null
$saleOrderId = $null
$fulfillmentId = $null
$outboundOrderId = $null
$accessToken = $null
$cleanedUp = $false

function Invoke-Api {
    param([string]$Method, [string]$Path, [hashtable]$Headers, [object]$Body)
    $parameters = @{
        Method = $Method
        Uri = $ApiBase + $Path
        Headers = $Headers
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 8 -Compress
    }
    $response = Invoke-RestMethod @parameters
    if ($response.code -ne 0) {
        throw "API $Method $Path failed: code=$($response.code), message=$($response.msg)"
    }
    return $response.data
}

function Invoke-LocalSql {
    param([string]$Sql, [switch]$ReturnOutput)
    $output = @(& docker exec -e "MYSQL_PWD=$DatabasePassword" $MySqlContainer mysql -uroot rd_erp -N -B -e $Sql)
    if ($LASTEXITCODE -ne 0) {
        throw "Local operation-log test SQL failed"
    }
    if ($ReturnOutput) {
        return $output
    }
}

try {
    $tenantHeaders = @{ "tenant-id" = $TenantId }
    $accessToken = Invoke-Api -Method Post -Path "/system/auth/login" -Headers $tenantHeaders -Body @{
        username = "admin"
        password = $AdminPassword
        captchaVerification = ""
    } | Select-Object -ExpandProperty accessToken
    $authHeaders = @{
        "tenant-id" = $TenantId
        Authorization = "Bearer $accessToken"
    }

    $stockLotId = [long](Invoke-Api -Method Post -Path "/rd/stock-lot/opening/create" -Headers $authHeaders -Body @{
        bizDate = "2026-07-10"
        locationId = 990002
        locationCode = "$runId-LOC"
        customerName = "$runId-CUSTOMER"
        productModel = "$runId-MODEL"
        furnaceNo = "$runId-FURNACE"
        furnaceLayer = "LAYER-A"
        quantity = 10
        packageWeight = 1000
        remark = $runId
    })
    Invoke-Api -Method Post -Path "/rd/stock-lot/adjustment/create" -Headers $authHeaders -Body @{
        stockLotId = $stockLotId
        bizDate = "2026-07-10"
        adjustmentType = "ADJUST_IN"
        quantity = 2
        remark = $runId
    } | Out-Null

    $saleOrderId = [long](Invoke-Api -Method Post -Path "/rd/sale-order/create" -Headers $authHeaders -Body @{
        orderNo = "$runId-SO"
        orderDate = "2026-07-10"
        customerName = "$runId-CUSTOMER"
        productModel = "$runId-MODEL"
        requiredQty = 4
        actualOutputQty = 0
        remark = $runId
    })
    $fulfillmentId = [long](Invoke-Api -Method Post -Path "/rd/order-fulfillment/allocate" -Headers $authHeaders -Body @{
        orderId = $saleOrderId
        stockLotId = $stockLotId
        quantity = 4
        remark = $runId
    })
    $outboundOrderId = [long](Invoke-Api -Method Post -Path "/rd/outbound-order/create" -Headers $authHeaders -Body @{
        outboundNo = "$runId-OUT"
        outboundDate = "2026-07-10"
        orderId = $saleOrderId
        fulfillmentId = $fulfillmentId
        quantity = 4
        remark = $runId
    })
    Invoke-Api -Method Put -Path "/rd/outbound-order/confirm?id=$outboundOrderId" `
            -Headers $authHeaders -Body $null | Out-Null

    $deadline = (Get-Date).AddSeconds(8)
    $auditValues = $null
    $operationBizIds = @($stockLotId, $saleOrderId, $fulfillmentId, $outboundOrderId)
    $expectedDistinctBusinessIds = @($operationBizIds | Select-Object -Unique).Count
    $auditWhere = "type LIKE 'RD %' AND biz_id IN ($stockLotId,$saleOrderId,$fulfillmentId,$outboundOrderId)" +
            " AND create_time >= NOW() - INTERVAL 5 MINUTE"
    do {
        Start-Sleep -Milliseconds 300
        $auditLine = @(Invoke-LocalSql -ReturnOutput -Sql "SELECT COUNT(*), COALESCE(SUM(success + 0), 0), COUNT(DISTINCT biz_id), COUNT(DISTINCT type) FROM system_operate_log WHERE $auditWhere;")[0]
        $auditValues = $auditLine -split "`t"
    } while ([int]$auditValues[0] -lt 6 -and (Get-Date) -lt $deadline)

    if ([int]$auditValues[0] -ne 6) {
        throw "Expected 6 RD operation logs but found $($auditValues[0])"
    }
    if ([int]$auditValues[1] -ne 6) {
        throw "Expected all RD operation logs to be successful"
    }
    if ([int]$auditValues[2] -ne $expectedDistinctBusinessIds -or [int]$auditValues[3] -ne 4) {
        throw "Expected $expectedDistinctBusinessIds distinct IDs across four RD operation types"
    }

    [pscustomobject]@{
        runId = $runId
        stockLotId = $stockLotId
        saleOrderId = $saleOrderId
        fulfillmentId = $fulfillmentId
        outboundOrderId = $outboundOrderId
        operationLogCount = [int]$auditValues[0]
        successfulLogCount = [int]$auditValues[1]
        distinctBusinessIds = [int]$auditValues[2]
        distinctOperationTypes = [int]$auditValues[3]
    } | ConvertTo-Json -Depth 4
} finally {
    if ($null -ne $accessToken) {
        try {
            Invoke-RestMethod -Method Post -Uri "$ApiBase/system/auth/logout" -Headers @{
                "tenant-id" = $TenantId
                Authorization = "Bearer $accessToken"
            } | Out-Null
        } catch {
            Write-Warning "Audit test token logout failed; cleanup will continue"
        }
    }
    $operationBizIds = @($stockLotId, $saleOrderId, $fulfillmentId, $outboundOrderId) |
            Where-Object { $null -ne $_ }
    $cleanupParts = @("DELETE FROM system_operate_log WHERE action LIKE '%$runId%';")
    if ($operationBizIds.Count -gt 0) {
        $cleanupParts += "DELETE FROM system_operate_log WHERE type LIKE 'RD %' AND biz_id IN ($($operationBizIds -join ',')) AND create_time >= NOW() - INTERVAL 5 MINUTE;"
    }
    if ($null -ne $outboundOrderId) {
        $cleanupParts += "DELETE FROM rd_outbound_order WHERE id = $outboundOrderId;"
    }
    if ($null -ne $fulfillmentId) {
        $cleanupParts += "DELETE FROM rd_order_fulfillment WHERE id = $fulfillmentId;"
    }
    if ($null -ne $saleOrderId) {
        $cleanupParts += "DELETE FROM rd_sale_order_extend WHERE id = $saleOrderId;"
    }
    if ($null -ne $stockLotId) {
        $cleanupParts += "DELETE FROM rd_daily_stock_summary WHERE stock_lot_id = $stockLotId;"
        $cleanupParts += "DELETE FROM rd_stock_transaction WHERE stock_lot_id = $stockLotId;"
        $cleanupParts += "DELETE FROM rd_stock_lot WHERE id = $stockLotId;"
    }
    Invoke-LocalSql -Sql ($cleanupParts -join " ")
    $cleanedUp = $true
}
