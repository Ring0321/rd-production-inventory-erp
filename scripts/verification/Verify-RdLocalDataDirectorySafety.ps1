$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$source = Join-Path $repoRoot "scripts/local/Start-RdErpLocal.ps1"
$tokens = $null
$errors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$tokens, [ref]$errors)
if ($errors.Count) { throw "Startup script has parse errors" }
$definition = $ast.Find({ param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq "Start-LocalMySql" }, $true)
Invoke-Expression $definition.Extent.Text
$testRoot = Join-Path ([IO.Path]::GetTempPath()) ("duoxu-start-guard-" + [guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $testRoot | Out-Null
$runtimeRoot = $testRoot
$mysqlPidFile = Join-Path $testRoot "mysql.pid"
$StartupTimeoutSeconds = 1
$InstanceName = "guard-test"
$backendRoot = Join-Path $repoRoot "backend"
$script:imports = 0
function Get-ManagedProcess { return $null }
function Assert-PortAvailable {}
function Find-MySqlTools { return @{ Server = "not-executed"; Client = "not-executed"; BaseDir = $testRoot } }
function Invoke-Checked {}
function Start-Process { return [pscustomobject]@{ Id = 8000; HasExited = $false } }
function Wait-ForPort {}
function Test-MySqlLogin { return $true }
function Invoke-ProcessWithInput { $script:imports++ }

$mysqlDataRoot = Join-Path $testRoot "existing"
New-Item -ItemType Directory -Path (Join-Path $mysqlDataRoot "mysql") -Force | Out-Null
$refused = $false
try { Start-LocalMySql 3310 | Out-Null } catch { $refused = $_.Exception.Message -match "refusing clean initialization" }
if (-not $refused -or $script:imports -ne 0) { throw "Existing unverified directory was not refused before schema import" }
[IO.File]::WriteAllText((Join-Path $mysqlDataRoot ".rd-erp-schema-ready"), "verified-test-marker")
Start-LocalMySql 3310 | Out-Null
if ($script:imports -ne 0) { throw "Verified existing data was imported over" }
$mysqlDataRoot = Join-Path $testRoot "fresh"
Start-LocalMySql 3310 | Out-Null
if ($script:imports -ne 1 -or -not (Test-Path -LiteralPath (Join-Path $mysqlDataRoot ".rd-erp-schema-ready"))) { throw "Fresh initialization contract failed" }

$text = Get-Content -Raw -Encoding UTF8 -LiteralPath $source
$start = $text.IndexOf('$mysqlDataRoot =')
$end = $text.IndexOf('$garnetToolRoot =', $start)
$resolver = $text.Substring($start, $end - $start)
$MySqlDataDirectory = "relative-path"
$refused = $false
try { Invoke-Expression $resolver } catch { $refused = $_.Exception.Message -match "absolute path" }
if (-not $refused) { throw "Relative override was accepted" }
$MySqlDataDirectory = $testRoot
Invoke-Expression $resolver
if ($mysqlDataRoot -ne $testRoot) { throw "Explicit absolute override failed" }
[IO.File]::WriteAllText((Join-Path $runtimeRoot "mysql-data-directory.txt"), $testRoot)
$MySqlDataDirectory = ""
Invoke-Expression $resolver
if ($mysqlDataRoot -ne $testRoot) { throw "Private runtime pointer was not restored" }
Write-Output "PASS: startup parser, existing-data refusal, verified-data preservation, fresh initialization, absolute override and private pointer recovery; no real process or database invoked."
