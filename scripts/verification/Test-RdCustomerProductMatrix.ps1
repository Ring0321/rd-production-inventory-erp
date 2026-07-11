param(
    [string]$ApiBase = "http://127.0.0.1:9001/admin-api",
    [string]$TenantId = "1",
    [string]$Username = "admin",
    [string]$Password = $env:RD_TEST_PASSWORD,
    [switch]$ValidateFixture
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (-not $Password) {
    throw "Set RD_TEST_PASSWORD or pass -Password"
}
$ApiBase = $ApiBase.TrimEnd("/")
$apiUri = [Uri]$ApiBase
if ($apiUri.Host -notin @("127.0.0.1", "localhost")) {
    throw "This test only runs against a local ERP endpoint"
}

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
    Invoke-RestMethod @parameters
}

$tenantHeaders = @{ "tenant-id" = $TenantId }
$login = Invoke-Api -Method Post -Path "/system/auth/login" -Headers $tenantHeaders -Body @{
    username = $Username
    password = $Password
    captchaVerification = ""
}
if ($login.code -ne 0) {
    throw "Login failed: $($login.msg)"
}
$authHeaders = @{
    "tenant-id" = $TenantId
    Authorization = "Bearer " + $login.data.accessToken
}

try {
    $customers = Invoke-Api -Method Get -Path "/rd/customer-product/customer-options" -Headers $authHeaders -Body $null
    if ($customers.code -ne 0 -or @($customers.data).Count -ne 17) {
        throw "Expected 17 customer options"
    }

    $allModels = Invoke-Api -Method Get -Path "/rd/customer-product/model-options" -Headers $authHeaders -Body $null
    if ($allModels.code -ne 0 -or @($allModels.data).Count -ne 46) {
        throw "Expected 46 distinct model options from 66 customer-model pairs"
    }
    $modelNames = @($allModels.data)
    if ($modelNames -notcontains "026") {
        throw "Leading-zero model 026 is missing"
    }

    $matrix = Invoke-Api -Method Get -Path "/rd/stock-matrix/page?startDate=2026-07-01&endDate=2026-07-11&pageNo=1&pageSize=10" -Headers $authHeaders -Body $null
    if ($matrix.code -ne 0 -or @($matrix.data.dates).Count -ne 11) {
        throw "Stock matrix response is invalid"
    }

    $tooLongRejected = $false
    try {
        Invoke-Api -Method Get -Path "/rd/stock-matrix/page?startDate=2026-06-01&endDate=2026-07-11&pageNo=1&pageSize=10" -Headers $authHeaders -Body $null | Out-Null
    } catch {
        $tooLongRejected = $_.Exception.Response.StatusCode.value__ -eq 400
    }
    if (-not $tooLongRejected) {
        throw "Stock matrix accepted a date range longer than 31 days"
    }

    $fixtureValidated = $false
    if ($ValidateFixture) {
        $fixture = Invoke-Api -Method Get -Path "/rd/stock-matrix/page?startDate=2026-07-01&endDate=2026-07-11&customerName=TEST-MATRIX&pageNo=1&pageSize=1" -Headers $authHeaders -Body $null
        $row = @($fixture.data.list)[0]
        $totals = $fixture.data.totals
        if ($fixture.data.total -ne 2 -or
            [decimal]$row.openingQty -ne 100 -or [decimal]$row.inboundQty -ne 24 -or
            [decimal]$row.outboundQty -ne -11 -or [decimal]$row.transferQty -ne 2 -or
            [decimal]$row.returnQty -ne 2 -or [decimal]$row.warehouseQty -ne 117 -or
            [decimal]$row.stockWeight -ne 117000 -or
            [decimal]$totals.inboundQty -ne 34 -or [decimal]$totals.outboundQty -ne -21 -or
            [decimal]$totals.warehouseQty -ne 117) {
            throw "Stock matrix fixture calculation or full-result totals are invalid"
        }
        $fixtureValidated = $true
    }

    [pscustomobject]@{
        customerCount = @($customers.data).Count
        distinctModelCount = @($allModels.data).Count
        preservesLeadingZeroModel = $modelNames -contains "026"
        matrixRows = @($matrix.data.list).Count
        matrixTotal = $matrix.data.total
        matrixDateColumns = @($matrix.data.dates).Count
        rejectsMoreThan31Days = $tooLongRejected
        fixtureValidated = $fixtureValidated
    } | ConvertTo-Json -Depth 5
} finally {
    try {
        Invoke-Api -Method Post -Path "/system/auth/logout" -Headers $authHeaders -Body $null | Out-Null
    } catch {
        Write-Warning "Test token logout failed"
    }
}
