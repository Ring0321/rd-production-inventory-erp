param(
    [string]$ApiBase = "http://127.0.0.1:48080/admin-api",
    [string]$TenantId = "1",
    [string]$MySqlContainer = "rd-erp-mysql",
    [string]$RedisContainer = "rd-erp-redis",
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

$testUsers = @(
    [pscustomobject]@{
        userId = 602001; username = "rdpermwarehouse"; roleId = 601001
        allowedPermission = "rd:stock-lot:query"
        allowedMethod = "Get"; allowedPath = "/rd/stock-lot/page?pageNo=1&pageSize=10"; allowedBody = $null
        deniedPermission = "rd:sale-order:create"
        deniedMethod = "Post"; deniedPath = "/rd/sale-order/create"
        deniedBody = @{ orderNo = "PERM-DENIED-W"; orderDate = "2026-07-10"; customerName = "PERM"; productModel = "PERM"; requiredQty = 1; actualOutputQty = 0 }
    },
    [pscustomobject]@{
        userId = 602002; username = "rdpermproduction"; roleId = 601002
        allowedPermission = "rd:production-output:query"
        allowedMethod = "Get"; allowedPath = "/rd/production-output/page?pageNo=1&pageSize=10"; allowedBody = $null
        deniedPermission = "rd:sale-order:query"
        deniedMethod = "Get"; deniedPath = "/rd/sale-order/page?pageNo=1&pageSize=10"; deniedBody = $null
    },
    [pscustomobject]@{
        userId = 602003; username = "rdpermorder"; roleId = 601003
        allowedPermission = "rd:sale-order:query"
        allowedMethod = "Get"; allowedPath = "/rd/sale-order/page?pageNo=1&pageSize=10"; allowedBody = $null
        deniedPermission = "rd:stock-lot:opening"
        deniedMethod = "Post"; deniedPath = "/rd/stock-lot/opening/create"
        deniedBody = @{ bizDate = "2026-07-10"; locationId = 1; locationCode = "PERM"; customerName = "PERM"; productModel = "PERM"; furnaceNo = "PERM"; furnaceLayer = "PERM"; quantity = 1; packageWeight = 1000 }
    },
    [pscustomobject]@{
        userId = 602004; username = "rdpermauditor"; roleId = 601004
        allowedPermission = "rd:transfer-order:query"
        allowedMethod = "Get"; allowedPath = "/rd/transfer-order/page?pageNo=1&pageSize=10"; allowedBody = $null
        deniedPermission = "rd:production-output:create"
        deniedMethod = "Post"; deniedPath = "/rd/production-output/create"
        deniedBody = @{ productionDate = "2026-07-10"; locationCode = "PERM"; customerName = "PERM"; productModel = "PERM"; furnaceNo = "PERM"; furnaceLayer = "PERM"; outputQty = 1; packageWeight = 1000 }
    },
    [pscustomobject]@{
        userId = 602005; username = "rdpermreadonly"; roleId = 601005
        allowedPermission = "rd:dashboard:query"
        allowedMethod = "Get"; allowedPath = "/rd/dashboard/overview?asOfDate=2026-07-10"; allowedBody = $null
        deniedPermission = "rd:production-output:create"
        deniedMethod = "Post"; deniedPath = "/rd/production-output/create"
        deniedBody = @{ productionDate = "2026-07-10"; locationCode = "PERM"; customerName = "PERM"; productModel = "PERM"; furnaceNo = "PERM"; furnaceLayer = "PERM"; outputQty = 1; packageWeight = 1000 }
    }
)

$userIds = @($testUsers | ForEach-Object { $_.userId })
$usernames = @($testUsers | ForEach-Object { $_.username })
$idList = $userIds -join ","
$usernameList = ($usernames | ForEach-Object { "'$_'" }) -join ","
$accessTokens = @()

function Invoke-LocalSql {
    param([string]$Sql)
    & docker exec -e "MYSQL_PWD=$DatabasePassword" $MySqlContainer mysql -uroot rd_erp -e $Sql | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Local permission test SQL failed"
    }
}

function Remove-TestUsers {
    $cleanupSql = @"
DELETE FROM system_operate_log WHERE user_id IN ($idList);
DELETE FROM infra_api_access_log WHERE user_id IN ($idList);
DELETE FROM system_login_log WHERE user_id IN ($idList) OR username IN ($usernameList);
DELETE FROM system_oauth2_access_token WHERE user_id IN ($idList);
DELETE FROM system_oauth2_refresh_token WHERE user_id IN ($idList);
DELETE FROM system_user_role WHERE user_id IN ($idList);
DELETE FROM system_users WHERE id IN ($idList) OR username IN ($usernameList);
"@
    Invoke-LocalSql -Sql $cleanupSql
}

function Remove-RedisPattern {
    param([string]$Pattern)
    $keys = @(& docker exec $RedisContainer redis-cli --scan --pattern $Pattern)
    if ($LASTEXITCODE -ne 0) {
        throw "Redis scan failed for $Pattern"
    }
    if ($keys.Count -gt 0) {
        & docker exec $RedisContainer redis-cli DEL $keys | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Redis cleanup failed for $Pattern"
        }
    }
}

function Clear-PermissionCaches {
    Remove-RedisPattern -Pattern "menu_role_ids:${TenantId}:*"
    Remove-RedisPattern -Pattern "role:${TenantId}:6010*"
    foreach ($userId in $userIds) {
        Remove-RedisPattern -Pattern "user_role_ids:$userId"
    }
}

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers,
        [object]$Body
    )
    $parameters = @{
        Method = $Method
        Uri = $ApiBase + $Path
        Headers = $Headers
    }
    if ($null -ne $Body) {
        $parameters.ContentType = "application/json"
        $parameters.Body = $Body | ConvertTo-Json -Depth 8 -Compress
    }
    return Invoke-RestMethod @parameters
}

$reports = @()
$cleanedUp = $false
try {
    Remove-TestUsers
    $insertStatements = @()
    foreach ($user in $testUsers) {
        $insertStatements += @"
INSERT INTO system_users
  (id, username, password, nickname, remark, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT $($user.userId), '$($user.username)', password, '$($user.username)', 'Local RD permission test', 0,
       'admin', NOW(), 'admin', NOW(), b'0', $TenantId
FROM system_users WHERE id = 1 AND tenant_id = $TenantId;
INSERT INTO system_user_role
  (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES ($($user.userId), $($user.roleId), 'admin', NOW(), 'admin', NOW(), b'0', $TenantId);
"@
    }
    Invoke-LocalSql -Sql ($insertStatements -join "`n")
    Clear-PermissionCaches

    foreach ($user in $testUsers) {
        $tenantHeaders = @{ "tenant-id" = $TenantId }
        $login = Invoke-Api -Method Post -Path "/system/auth/login" -Headers $tenantHeaders -Body @{
            username = $user.username
            password = $AdminPassword
            captchaVerification = ""
        }
        if ($login.code -ne 0) {
            throw "Login failed for $($user.username): $($login.msg)"
        }
        $accessTokens += $login.data.accessToken
        $authHeaders = @{
            "tenant-id" = $TenantId
            Authorization = "Bearer " + $login.data.accessToken
        }
        $permissionInfo = Invoke-Api -Method Get -Path "/system/auth/get-permission-info" -Headers $authHeaders -Body $null
        if ($permissionInfo.code -ne 0) {
            throw "Permission info failed for $($user.username)"
        }
        $permissions = @($permissionInfo.data.permissions)
        if ($permissions -notcontains $user.allowedPermission) {
            throw "$($user.username) is missing $($user.allowedPermission)"
        }
        if ($permissions -contains $user.deniedPermission) {
            throw "$($user.username) unexpectedly has $($user.deniedPermission)"
        }

        $allowed = Invoke-Api -Method $user.allowedMethod -Path $user.allowedPath -Headers $authHeaders -Body $user.allowedBody
        if ($allowed.code -ne 0) {
            throw "Allowed API failed for $($user.username): code=$($allowed.code)"
        }
        $denied = Invoke-Api -Method $user.deniedMethod -Path $user.deniedPath -Headers $authHeaders -Body $user.deniedBody
        if ($denied.code -ne 403) {
            throw "Denied API returned $($denied.code) for $($user.username), expected 403"
        }

        $reports += [pscustomobject]@{
            username = $user.username
            roleId = $user.roleId
            permissionCount = $permissions.Count
            allowedCode = $allowed.code
            deniedCode = $denied.code
        }
    }
} finally {
    foreach ($accessToken in $accessTokens) {
        try {
            Invoke-RestMethod -Method Post -Uri "$ApiBase/system/auth/logout" -Headers @{
                "tenant-id" = $TenantId
                Authorization = "Bearer $accessToken"
            } | Out-Null
        } catch {
            Write-Warning "Test token logout failed; database cleanup will continue"
        }
    }
    Remove-TestUsers
    Clear-PermissionCaches
    $cleanedUp = $true
}

[pscustomobject]@{
    roles = $reports
    cleanedUp = $cleanedUp
} | ConvertTo-Json -Depth 8
