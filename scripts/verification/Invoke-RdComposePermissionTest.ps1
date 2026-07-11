param(
    [string]$ApiBase = "http://127.0.0.1:9001/admin-api",
    [string]$MySqlContainer = "rd-erp-slim-verify-mysql-1",
    [string]$RedisContainer = "rd-erp-slim-verify-redis-1",
    [string]$Database = "rd_erp_verify",
    [string]$AdminPassword = $env:RD_TEST_PASSWORD,
    [string]$DatabasePassword = $env:RD_DB_PASSWORD
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
if (-not $AdminPassword -or -not $DatabasePassword) {
    throw "Set RD_TEST_PASSWORD and RD_DB_PASSWORD or pass both parameters"
}

$container = & docker inspect $RedisContainer | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or @($container).Count -ne 1) {
    throw "Unable to inspect the local Redis container"
}
$redisPasswordEntry = @($container[0].Config.Env) | Where-Object { $_ -like "RD_REDIS_PASSWORD=*" }
if (@($redisPasswordEntry).Count -ne 1) {
    throw "The local Redis container does not contain RD_REDIS_PASSWORD"
}
$redisPassword = $redisPasswordEntry.Substring("RD_REDIS_PASSWORD=".Length)

& "$PSScriptRoot/../security/Test-RdPermissions.ps1" `
    -ApiBase $ApiBase `
    -MySqlContainer $MySqlContainer `
    -RedisContainer $RedisContainer `
    -Database $Database `
    -AdminPassword $AdminPassword `
    -DatabasePassword $DatabasePassword `
    -RedisPassword $redisPassword
if ($LASTEXITCODE -ne 0) {
    throw "RD permission verification failed"
}
