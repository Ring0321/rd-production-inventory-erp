param(
    [string]$ApiBase = "http://127.0.0.1:48080/admin-api",
    [string]$TenantId = "1",
    [string]$Username = "admin",
    [string]$Password = "admin123",
    [string]$MySqlContainer = "rd-erp-mysql",
    [switch]$KeepData
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ApiBase = $ApiBase.TrimEnd("/")
$apiUri = [Uri]$ApiBase
if ($apiUri.Host -notin @("127.0.0.1", "localhost") -or $apiUri.Port -ne 48080) {
    throw "This test only runs against the local backend on port 48080"
}

Add-Type -AssemblyName System.Net.Http

$headers = @{ "tenant-id" = $TenantId }
$runId = "PERF-" + (Get-Date -Format "yyyyMMddHHmmssfff")
$stockLotIds = @()
$orderIds = @()
$fulfillmentIds = @()
$outboundIds = @()
$cleanedUp = $false
$report = $null

function Invoke-RdApi {
    param(
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [object]$Body
    )

    $parameters = @{
        Method = $Method
        Uri = $ApiBase + $Path
        Headers = $headers
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

function New-StockLot {
    param([string]$Suffix, [decimal]$Quantity)
    return Invoke-RdApi -Method Post -Path "/rd/stock-lot/opening/create" -Body @{
        bizDate = "2026-07-10"
        locationId = 990001
        locationCode = "$runId-LOC-$Suffix"
        customerName = "$runId-CUSTOMER"
        productModel = "$runId-MODEL"
        furnaceNo = "$runId-FURNACE-$Suffix"
        furnaceLayer = "LAYER-$Suffix"
        quantity = $Quantity
        packageWeight = 1000
        remark = "$runId concurrency test"
    }
}

function New-SaleOrder {
    param([string]$Suffix, [decimal]$Quantity)
    return Invoke-RdApi -Method Post -Path "/rd/sale-order/create" -Body @{
        orderNo = "$runId-SO-$Suffix"
        orderDate = "2026-07-10"
        customerName = "$runId-CUSTOMER"
        productModel = "$runId-MODEL"
        requiredQty = $Quantity
        actualOutputQty = 0
        remark = "$runId concurrency test"
    }
}

function New-Fulfillment {
    param([long]$OrderId, [long]$StockLotId, [decimal]$Quantity)
    return Invoke-RdApi -Method Post -Path "/rd/order-fulfillment/allocate" -Body @{
        orderId = $OrderId
        stockLotId = $StockLotId
        quantity = $Quantity
        remark = "$runId concurrency test"
    }
}

function New-OutboundOrder {
    param([string]$Suffix, [long]$OrderId, [long]$FulfillmentId, [decimal]$Quantity)
    return Invoke-RdApi -Method Post -Path "/rd/outbound-order/create" -Body @{
        outboundNo = "$runId-OUT-$Suffix"
        outboundDate = "2026-07-10"
        orderId = $OrderId
        fulfillmentId = $FulfillmentId
        quantity = $Quantity
        remark = "$runId concurrency test"
    }
}

function Invoke-ConcurrentConfirm {
    param([long[]]$Ids)

    $client = New-Object System.Net.Http.HttpClient
    $client.DefaultRequestHeaders.Authorization =
        New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $headers.Authorization.Substring(7))
    $client.DefaultRequestHeaders.Add("tenant-id", $TenantId)
    $requests = @()
    $tasks = @()
    try {
        foreach ($id in $Ids) {
            $request = New-Object System.Net.Http.HttpRequestMessage(
                [System.Net.Http.HttpMethod]::Put,
                "$ApiBase/rd/outbound-order/confirm?id=$id"
            )
            $requests += $request
            $tasks += $client.SendAsync($request)
        }
        [System.Threading.Tasks.Task]::WaitAll([System.Threading.Tasks.Task[]]$tasks)

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

function Invoke-ConcurrentAllocation {
    param([object[]]$Bodies)

    $client = New-Object System.Net.Http.HttpClient
    $client.DefaultRequestHeaders.Authorization =
        New-Object System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", $headers.Authorization.Substring(7))
    $client.DefaultRequestHeaders.Add("tenant-id", $TenantId)
    $requests = @()
    $tasks = @()
    try {
        foreach ($body in $Bodies) {
            $request = New-Object System.Net.Http.HttpRequestMessage(
                [System.Net.Http.HttpMethod]::Post,
                "$ApiBase/rd/order-fulfillment/allocate"
            )
            $json = $body | ConvertTo-Json -Depth 8 -Compress
            $request.Content = New-Object System.Net.Http.StringContent(
                $json, [System.Text.Encoding]::UTF8, "application/json"
            )
            $requests += $request
            $tasks += $client.SendAsync($request)
        }
        [System.Threading.Tasks.Task]::WaitAll([System.Threading.Tasks.Task[]]$tasks)

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

function Assert-Quantity {
    param([object]$Actual, [decimal]$Expected, [string]$Name)
    if ([decimal]$Actual -ne $Expected) {
        throw "$Name expected $Expected but was $Actual"
    }
}

try {
    $loginParameters = @{
        Method = "Post"
        Uri = "$ApiBase/system/auth/login"
        Headers = $headers
        ContentType = "application/json"
        Body = (@{
            username = $Username
            password = $Password
            captchaVerification = ""
        } | ConvertTo-Json -Compress)
    }
    $login = Invoke-RestMethod @loginParameters
    if ($login.code -ne 0) {
        throw "Login failed: $($login.msg)"
    }
    $headers.Authorization = "Bearer " + $login.data.accessToken

    $matchingStockLotId = [long](New-StockLot -Suffix "MATCH" -Quantity 100)
    $stockLotIds += $matchingStockLotId
    $matchingOrderAId = [long](New-SaleOrder -Suffix "MATCH-A" -Quantity 80)
    $matchingOrderBId = [long](New-SaleOrder -Suffix "MATCH-B" -Quantity 80)
    $orderIds += @($matchingOrderAId, $matchingOrderBId)
    $matchingResults = @(Invoke-ConcurrentAllocation -Bodies @(
        @{
            orderId = $matchingOrderAId
            stockLotId = $matchingStockLotId
            quantity = 80
            remark = "$runId concurrent matching test"
        },
        @{
            orderId = $matchingOrderBId
            stockLotId = $matchingStockLotId
            quantity = 80
            remark = "$runId concurrent matching test"
        }
    ))
    $matchingSuccesses = @($matchingResults | Where-Object { $_.code -eq 0 })
    if ($matchingSuccesses.Count -ne 1) {
        throw "Concurrent matching did not produce exactly one successful response"
    }
    $fulfillmentIds += [long]$matchingSuccesses[0].data
    $matchingStock = Invoke-RdApi -Method Get -Path "/rd/stock-lot/get?id=$matchingStockLotId"
    Assert-Quantity -Actual $matchingStock.warehouseQty -Expected 100 -Name "matching warehouseQty"
    Assert-Quantity -Actual $matchingStock.lockedQty -Expected 80 -Name "matching lockedQty"
    Assert-Quantity -Actual $matchingStock.availableQty -Expected 20 -Name "matching availableQty"

    $sharedStockLotId = [long](New-StockLot -Suffix "SHARED" -Quantity 100)
    $stockLotIds += $sharedStockLotId
    $orderAId = [long](New-SaleOrder -Suffix "A" -Quantity 50)
    $orderBId = [long](New-SaleOrder -Suffix "B" -Quantity 50)
    $orderIds += @($orderAId, $orderBId)
    $fulfillmentAId = [long](New-Fulfillment -OrderId $orderAId -StockLotId $sharedStockLotId -Quantity 50)
    $fulfillmentBId = [long](New-Fulfillment -OrderId $orderBId -StockLotId $sharedStockLotId -Quantity 50)
    $fulfillmentIds += @($fulfillmentAId, $fulfillmentBId)
    $outboundAId = [long](New-OutboundOrder -Suffix "A" -OrderId $orderAId -FulfillmentId $fulfillmentAId -Quantity 50)
    $outboundBId = [long](New-OutboundOrder -Suffix "B" -OrderId $orderBId -FulfillmentId $fulfillmentBId -Quantity 50)
    $outboundIds += @($outboundAId, $outboundBId)

    $parallelResults = @(Invoke-ConcurrentConfirm -Ids @($outboundAId, $outboundBId))
    if (@($parallelResults | Where-Object { $_.code -eq 0 }).Count -ne 2) {
        throw "Parallel outbound confirmation did not produce two successful responses"
    }
    $sharedStock = Invoke-RdApi -Method Get -Path "/rd/stock-lot/get?id=$sharedStockLotId"
    Assert-Quantity -Actual $sharedStock.warehouseQty -Expected 0 -Name "parallel warehouseQty"
    Assert-Quantity -Actual $sharedStock.lockedQty -Expected 0 -Name "parallel lockedQty"
    Assert-Quantity -Actual $sharedStock.outboundQty -Expected 100 -Name "parallel outboundQty"

    $duplicateStockLotId = [long](New-StockLot -Suffix "DUP" -Quantity 10)
    $stockLotIds += $duplicateStockLotId
    $duplicateOrderId = [long](New-SaleOrder -Suffix "DUP" -Quantity 10)
    $orderIds += $duplicateOrderId
    $duplicateFulfillmentId = [long](New-Fulfillment -OrderId $duplicateOrderId -StockLotId $duplicateStockLotId -Quantity 10)
    $fulfillmentIds += $duplicateFulfillmentId
    $duplicateOutboundId = [long](New-OutboundOrder -Suffix "DUP" -OrderId $duplicateOrderId -FulfillmentId $duplicateFulfillmentId -Quantity 10)
    $outboundIds += $duplicateOutboundId

    $duplicateResults = @(Invoke-ConcurrentConfirm -Ids @($duplicateOutboundId, $duplicateOutboundId))
    if (@($duplicateResults | Where-Object { $_.code -eq 0 }).Count -ne 1) {
        throw "Duplicate confirmation did not produce exactly one successful response"
    }
    $duplicateStock = Invoke-RdApi -Method Get -Path "/rd/stock-lot/get?id=$duplicateStockLotId"
    Assert-Quantity -Actual $duplicateStock.warehouseQty -Expected 0 -Name "duplicate warehouseQty"
    Assert-Quantity -Actual $duplicateStock.lockedQty -Expected 0 -Name "duplicate lockedQty"
    Assert-Quantity -Actual $duplicateStock.outboundQty -Expected 10 -Name "duplicate outboundQty"

    $report = [pscustomobject]@{
        runId = $runId
        concurrentMatching = [pscustomobject]@{
            responseCodes = @($matchingResults | ForEach-Object { $_.code })
            warehouseQty = $matchingStock.warehouseQty
            lockedQty = $matchingStock.lockedQty
            availableQty = $matchingStock.availableQty
        }
        parallelOutbound = [pscustomobject]@{
            responseCodes = @($parallelResults | ForEach-Object { $_.code })
            warehouseQty = $sharedStock.warehouseQty
            lockedQty = $sharedStock.lockedQty
            outboundQty = $sharedStock.outboundQty
        }
        duplicateConfirm = [pscustomobject]@{
            responseCodes = @($duplicateResults | ForEach-Object { $_.code })
            warehouseQty = $duplicateStock.warehouseQty
            lockedQty = $duplicateStock.lockedQty
            outboundQty = $duplicateStock.outboundQty
        }
    }
} finally {
    if (-not $KeepData -and ($stockLotIds.Count + $orderIds.Count + $fulfillmentIds.Count + $outboundIds.Count) -gt 0) {
        $sqlParts = @()
        if ($stockLotIds.Count -gt 0) {
            $sqlParts += "DELETE FROM rd_daily_stock_summary WHERE stock_lot_id IN ($($stockLotIds -join ','));"
            $sqlParts += "DELETE FROM rd_stock_transaction WHERE stock_lot_id IN ($($stockLotIds -join ','));"
        }
        if ($outboundIds.Count -gt 0) {
            $sqlParts += "DELETE FROM rd_outbound_order WHERE id IN ($($outboundIds -join ','));"
        }
        if ($fulfillmentIds.Count -gt 0) {
            $sqlParts += "DELETE FROM rd_order_fulfillment WHERE id IN ($($fulfillmentIds -join ','));"
        }
        if ($orderIds.Count -gt 0) {
            $sqlParts += "DELETE FROM rd_sale_order_extend WHERE id IN ($($orderIds -join ','));"
        }
        if ($stockLotIds.Count -gt 0) {
            $sqlParts += "DELETE FROM rd_stock_lot WHERE id IN ($($stockLotIds -join ','));"
        }
        & docker exec $MySqlContainer mysql -uroot -prd_local_2026 rd_erp -e ($sqlParts -join " ") | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Local performance data cleanup failed"
        }
        $cleanedUp = $true
    }
}

if ($null -ne $report) {
    $report | Add-Member -NotePropertyName cleanedUp -NotePropertyValue $cleanedUp
    $report | ConvertTo-Json -Depth 8
}
