function Import-RdLocalEnvironment {
    param([Parameter(Mandatory = $true)][string]$RepoRoot)

    $envFile = Join-Path $RepoRoot "deploy\local-infra\.env.local"
    if (-not (Test-Path -LiteralPath $envFile)) {
        return
    }
    foreach ($line in Get-Content -LiteralPath $envFile -Encoding UTF8) {
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') {
            continue
        }
        $name = $matches[1]
        $value = $matches[2].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
                ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

function Assert-RdLocalApiEndpoint {
    param([Parameter(Mandatory = $true)][string]$ApiBase)

    $uri = [Uri]$ApiBase
    if ($uri.Host -notin @('127.0.0.1', 'localhost') -or $uri.Port -ne 48080) {
        throw 'This verification only runs against the local backend on port 48080'
    }
}

function Invoke-RdLocalSql {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [Parameter(Mandatory = $true)][string]$Password,
        [string]$Database = 'rd_erp',
        [string]$HostName = '127.0.0.1',
        [int]$Port = 3307,
        [switch]$ReturnOutput
    )

    if ($HostName -notin @('127.0.0.1', 'localhost')) {
        throw 'This verification only runs against local MySQL'
    }
    if ($Database -notmatch '^[A-Za-z0-9_]+$') {
        throw 'Invalid database name'
    }
    $mysql = (Get-Command mysql.exe -ErrorAction Stop).Source
    $previousPassword = $env:MYSQL_PWD
    try {
        $env:MYSQL_PWD = $Password
        $arguments = @(
            "--host=$HostName",
            "--port=$Port",
            '--user=root',
            "--database=$Database",
            '--default-character-set=utf8mb4',
            '--batch',
            '--skip-column-names',
            '--connect-timeout=5',
            '-e',
            $Sql
        )
        $output = @(& $mysql @arguments 2>&1)
        if ($LASTEXITCODE -ne 0) {
            throw "Local SQL failed: $($output -join [Environment]::NewLine)"
        }
        if ($ReturnOutput) {
            return @($output | ForEach-Object { [string]$_ })
        }
    } finally {
        $env:MYSQL_PWD = $previousPassword
    }
}

function Get-RdLocalSqlFirstRow {
    param(
        [Parameter(Mandatory = $true)][string]$Sql,
        [Parameter(Mandatory = $true)][string]$Password,
        [string]$Database = 'rd_erp'
    )

    $rows = @(Invoke-RdLocalSql -Sql $Sql -Password $Password -Database $Database -ReturnOutput)
    if ($rows.Count -eq 0) {
        throw 'Local SQL query returned no rows'
    }
    return [string]$rows[0]
}

function Assert-RdLocalFixtureUsersVacant {
    param(
        [Parameter(Mandatory = $true)][long[]]$UserIds,
        [Parameter(Mandatory = $true)][string[]]$Usernames,
        [Parameter(Mandatory = $true)][string]$Password,
        [string]$Database = 'rd_erp'
    )

    if ($UserIds.Count -eq 0 -or $Usernames.Count -eq 0) {
        throw 'Fixture users must be specified'
    }
    foreach ($username in $Usernames) {
        if ($username -notmatch '^[a-z0-9_]+$') {
            throw 'Invalid fixture username'
        }
    }
    $ids = $UserIds -join ','
    $names = ($Usernames | ForEach-Object { "'$_'" }) -join ','
    $sql = @"
SELECT (SELECT COUNT(*) FROM system_users WHERE id IN ($ids) OR username IN ($names))
     + (SELECT COUNT(*) FROM system_user_role WHERE user_id IN ($ids))
     + (SELECT COUNT(*) FROM system_operate_log WHERE user_id IN ($ids))
     + (SELECT COUNT(*) FROM infra_api_access_log WHERE user_id IN ($ids))
     + (SELECT COUNT(*) FROM system_login_log WHERE user_id IN ($ids) OR username IN ($names))
     + (SELECT COUNT(*) FROM system_oauth2_access_token WHERE user_id IN ($ids))
     + (SELECT COUNT(*) FROM system_oauth2_refresh_token WHERE user_id IN ($ids));
"@
    $count = Get-RdLocalSqlFirstRow -Sql $sql -Password $Password -Database $Database
    if ([long]$count -ne 0) {
        throw 'Fixture user IDs or usernames already have data; refusing to delete or overwrite it'
    }
}

function Invoke-RdApiResponse {
    param(
        [Parameter(Mandatory = $true)][string]$ApiBase,
        [Parameter(Mandatory = $true)][string]$Method,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][hashtable]$Headers,
        [object]$Body
    )

    $parameters = @{
        Method = $Method
        Uri = $ApiBase.TrimEnd('/') + $Path
        Headers = $Headers
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }
    return Invoke-RestMethod @parameters
}

function Invoke-RdLogin {
    param(
        [Parameter(Mandatory = $true)][string]$ApiBase,
        [Parameter(Mandatory = $true)][string]$TenantId,
        [Parameter(Mandatory = $true)][string]$Username,
        [Parameter(Mandatory = $true)][string]$Password
    )

    $response = Invoke-RdApiResponse -ApiBase $ApiBase -Method Post -Path '/system/auth/login' `
            -Headers @{ 'tenant-id' = $TenantId } -Body @{
        username = $Username
        password = $Password
        captchaVerification = ''
    }
    if ($response.code -ne 0) {
        throw "Login failed for ${Username}: $($response.msg)"
    }
    return [string]$response.data.accessToken
}

function New-RdAuthHeaders {
    param(
        [Parameter(Mandatory = $true)][string]$TenantId,
        [Parameter(Mandatory = $true)][string]$AccessToken
    )

    return @{
        'tenant-id' = $TenantId
        Authorization = "Bearer $AccessToken"
    }
}

function Invoke-RdLogoutQuietly {
    param(
        [Parameter(Mandatory = $true)][string]$ApiBase,
        [Parameter(Mandatory = $true)][string]$TenantId,
        [string]$AccessToken
    )

    if ([string]::IsNullOrWhiteSpace($AccessToken)) {
        return
    }
    try {
        Invoke-RestMethod -Method Post -Uri ($ApiBase.TrimEnd('/') + '/system/auth/logout') `
                -Headers (New-RdAuthHeaders -TenantId $TenantId -AccessToken $AccessToken) | Out-Null
    } catch {
        Write-Warning 'Test token logout failed; database cleanup will continue'
    }
}

function Send-RdRedisCommand {
    param(
        [Parameter(Mandatory = $true)][System.IO.Stream]$Stream,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    $builder = [Text.StringBuilder]::new()
    [void]$builder.Append("*$($Arguments.Count)`r`n")
    foreach ($argument in $Arguments) {
        $length = [Text.Encoding]::UTF8.GetByteCount($argument)
        [void]$builder.Append("`$$length`r`n$argument`r`n")
    }
    $bytes = [Text.Encoding]::UTF8.GetBytes($builder.ToString())
    $Stream.Write($bytes, 0, $bytes.Length)
    $Stream.Flush()
}

function Clear-RdPermissionCache {
    param(
        [Parameter(Mandatory = $true)][string]$Password,
        [Parameter(Mandatory = $true)][string[]]$Keys,
        [string]$HostName = '127.0.0.1',
        [int]$Port = 6379
    )

    if ($HostName -notin @('127.0.0.1', 'localhost')) {
        throw 'This verification only clears a local Redis-compatible cache'
    }
    $client = [Net.Sockets.TcpClient]::new()
    try {
        $client.Connect($HostName, $Port)
        $stream = $client.GetStream()
        $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $false, 1024, $true)
        Send-RdRedisCommand -Stream $stream -Arguments @('AUTH', $Password)
        $authResponse = $reader.ReadLine()
        if ($authResponse -notlike '+OK*') {
            throw 'Local cache authentication failed'
        }
        if ($Keys.Count -gt 0) {
            Send-RdRedisCommand -Stream $stream -Arguments (@('DEL') + $Keys)
            $deleteResponse = $reader.ReadLine()
            if ($deleteResponse -notmatch '^:\d+$') {
                throw 'Local permission cache cleanup failed'
            }
        }
        $reader.Dispose()
    } finally {
        $client.Dispose()
    }
}
