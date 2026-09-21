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

$users = @(
    [pscustomobject]@{
        userId = 602101; username = 'rdputawaywarehouse'; roleId = 600002
        expected = @('rd:putaway-task:query', 'rd:putaway-task:create',
                'rd:putaway-task:assign', 'rd:putaway-task:confirm')
        forbidden = @('rd:putaway-task:execute')
        allowedMethod = 'Get'; allowedPath = '/rd/putaway-task/page?pageNo=1&pageSize=10'; allowedBody = $null
        deniedMethod = 'Get'; deniedPath = '/rd/putaway-task/my-page?pageNo=1&pageSize=10'; deniedBody = $null
    },
    [pscustomobject]@{
        userId = 602102; username = 'rdputawaydriver'; roleId = 600008
        expected = @('rd:putaway-task:execute')
        forbidden = @('rd:putaway-task:query', 'rd:putaway-task:create',
                'rd:putaway-task:assign', 'rd:putaway-task:confirm')
        allowedMethod = 'Get'; allowedPath = '/rd/putaway-task/my-page?pageNo=1&pageSize=10'; allowedBody = $null
        deniedMethod = 'Get'; deniedPath = '/rd/putaway-task/page?pageNo=1&pageSize=10'; deniedBody = $null
    },
    [pscustomobject]@{
        userId = 602103; username = 'rdputawayauditor'; roleId = 600004
        expected = @('rd:putaway-task:query')
        forbidden = @('rd:putaway-task:create', 'rd:putaway-task:assign',
                'rd:putaway-task:execute', 'rd:putaway-task:confirm')
        allowedMethod = 'Get'; allowedPath = '/rd/putaway-task/page?pageNo=1&pageSize=10'; allowedBody = $null
        deniedMethod = 'Post'; deniedPath = '/rd/putaway-task/create'
        deniedBody = @{ plannedLocationId = 1; forkliftDriverId = 1; packageIds = @(1); remark = 'permission probe' }
    },
    [pscustomobject]@{
        userId = 602104; username = 'rdputawayreadonly'; roleId = 600005
        expected = @('rd:putaway-task:query')
        forbidden = @('rd:putaway-task:create', 'rd:putaway-task:assign',
                'rd:putaway-task:execute', 'rd:putaway-task:confirm')
        allowedMethod = 'Get'; allowedPath = '/rd/putaway-task/page?pageNo=1&pageSize=10'; allowedBody = $null
        deniedMethod = 'Post'; deniedPath = '/rd/putaway-task/create'
        deniedBody = @{ plannedLocationId = 1; forkliftDriverId = 1; packageIds = @(1); remark = 'permission probe' }
    }
)

$userIds = @($users | ForEach-Object { $_.userId })
$usernames = @($users | ForEach-Object { $_.username })
$idList = $userIds -join ','
$usernameList = ($usernames | ForEach-Object { "'$_'" }) -join ','
$tokens = @()
$reports = @()
$cleanedUp = $false
$fixtureStarted = $false

function Remove-TestUsers {
    $sql = @"
DELETE FROM system_operate_log WHERE user_id IN ($idList);
DELETE FROM infra_api_access_log WHERE user_id IN ($idList);
DELETE FROM system_login_log WHERE user_id IN ($idList) OR username IN ($usernameList);
DELETE FROM system_oauth2_access_token WHERE user_id IN ($idList);
DELETE FROM system_oauth2_refresh_token WHERE user_id IN ($idList);
DELETE FROM system_user_role WHERE user_id IN ($idList);
DELETE FROM system_users WHERE id IN ($idList) OR username IN ($usernameList);
"@
    Invoke-RdLocalSql -Sql $sql -Password $DatabasePassword -Database $Database
}

try {
    Assert-RdLocalFixtureUsersVacant -UserIds $userIds -Usernames $usernames `
            -Password $DatabasePassword -Database $Database
    $fixtureStarted = $true
    $statements = @()
    foreach ($user in $users) {
        $statements += @"
INSERT INTO system_users
  (id, username, password, nickname, remark, status, creator, create_time, updater, update_time, deleted, tenant_id)
SELECT $($user.userId), '$($user.username)', password, '$($user.username)', 'B6 putaway permission test', 0,
       'b6-test', NOW(), 'b6-test', NOW(), b'0', $TenantId
FROM system_users WHERE id = 1 AND tenant_id = $TenantId;
INSERT INTO system_user_role
  (user_id, role_id, creator, create_time, updater, update_time, deleted, tenant_id)
VALUES ($($user.userId), $($user.roleId), 'b6-test', NOW(), 'b6-test', NOW(), b'0', $TenantId);
"@
    }
    Invoke-RdLocalSql -Sql ($statements -join "`n") -Password $DatabasePassword -Database $Database
    $cacheKeys = @("menu_role_ids:${TenantId}:600215")
    $cacheKeys += $userIds | ForEach-Object { "user_role_ids:$_" }
    Clear-RdPermissionCache -Password $RedisPassword -Keys $cacheKeys

    foreach ($user in $users) {
        $token = Invoke-RdLogin -ApiBase $ApiBase -TenantId $TenantId `
                -Username $user.username -Password $AdminPassword
        $tokens += $token
        $headers = New-RdAuthHeaders -TenantId $TenantId -AccessToken $token
        $permissionInfo = Invoke-RdApiResponse -ApiBase $ApiBase -Method Get `
                -Path '/system/auth/get-permission-info' -Headers $headers -Body $null
        if ($permissionInfo.code -ne 0) {
            throw "Permission info failed for $($user.username)"
        }
        $permissions = @($permissionInfo.data.permissions)
        foreach ($permission in $user.expected) {
            if ($permissions -notcontains $permission) {
                throw "$($user.username) is missing $permission"
            }
        }
        foreach ($permission in $user.forbidden) {
            if ($permissions -contains $permission) {
                throw "$($user.username) unexpectedly has $permission"
            }
        }

        $allowed = Invoke-RdApiResponse -ApiBase $ApiBase -Method $user.allowedMethod `
                -Path $user.allowedPath -Headers $headers -Body $user.allowedBody
        if ($allowed.code -ne 0) {
            throw "Allowed API failed for $($user.username): $($allowed.code)"
        }
        $denied = Invoke-RdApiResponse -ApiBase $ApiBase -Method $user.deniedMethod `
                -Path $user.deniedPath -Headers $headers -Body $user.deniedBody
        if ($denied.code -ne 403) {
            throw "Denied API returned $($denied.code) for $($user.username), expected 403"
        }

        $reports += [pscustomobject]@{
            roleId = $user.roleId
            username = $user.username
            expectedPermissions = $user.expected
            forbiddenPermissions = $user.forbidden
            allowedCode = [int]$allowed.code
            deniedCode = [int]$denied.code
        }
    }
} finally {
    foreach ($token in $tokens) {
        Invoke-RdLogoutQuietly -ApiBase $ApiBase -TenantId $TenantId -AccessToken $token
    }
    if ($fixtureStarted) {
        Remove-TestUsers
        Clear-RdPermissionCache -Password $RedisPassword -Keys ($userIds | ForEach-Object { "user_role_ids:$_" })
        $cleanedUp = $true
    }
}

[pscustomobject]@{
    scope = 'B6 putaway role matrix'
    roles = $reports
    cleanedUp = $cleanedUp
} | ConvertTo-Json -Depth 8
