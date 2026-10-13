param([Parameter(Mandatory=$true)][string]$Archive)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$checks = 0
function Check([bool]$condition, [string]$message) {
    if (-not $condition) { throw $message }
    $script:checks++
}
function Hash-Stream([IO.Stream]$stream) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace("-", "").ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function Hash-File([string]$file) {
    $stream = [IO.File]::OpenRead($file)
    try { return Hash-Stream $stream } finally { $stream.Dispose() }
}
function Read-Entry($entry) {
    Check ($null -ne $entry) "Required archive entry missing"
    $reader = New-Object IO.StreamReader($entry.Open(), [Text.Encoding]::UTF8)
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}
$commit = (& git -C $root rev-parse HEAD).Trim()
Check (-not [bool](& git -C $root status --porcelain)) "Archive must bind a clean source snapshot"
$hash = Hash-File $Archive
$hashFile = [IO.Path]::ChangeExtension($Archive, ".sha256")
Check ((Get-Content -LiteralPath $hashFile -Raw).Trim().StartsWith($hash + "  ")) "ZIP checksum differs"
$zip = [IO.Compression.ZipFile]::OpenRead($Archive)
try {
    $versionEntry = @($zip.Entries | Where-Object { $_.FullName.EndsWith("/VERSION.txt") })
    Check ($versionEntry.Count -eq 1) "Exactly one VERSION is required"
    $prefix = $versionEntry[0].FullName.Substring(0, $versionEntry[0].FullName.Length - "VERSION.txt".Length)
    $version = Read-Entry $versionEntry[0]
    Check ($version.Contains("Git commit: $commit") -and $version.Contains("Dirty working tree: False")) "VERSION does not bind this clean commit"
    $names = @($zip.Entries | ForEach-Object { $_.FullName })
    Check (($names | Select-Object -Unique).Count -eq $names.Count) "Duplicate ZIP entries"
    foreach ($name in $names) {
        Check ($name.StartsWith($prefix) -and $name -notmatch '(^|/)\.\.(/|$)|^/|\\') "Unsafe archive path"
        Check ($name -notmatch '/(temp|node_modules|\.git)/|\.env\.local|application-local|application-test') "Private/development file in archive"
    }
    $publicDocs = @($zip.Entries | Where-Object { $_.FullName -like ($prefix + "docs/*.md") })
    $expectedDocs = @(Get-ChildItem -LiteralPath (Join-Path $root "docs/product") -Filter "*.md")
    Check ($publicDocs.Count -eq $expectedDocs.Count) "Internal or missing product documentation"
    $mapping = @{
        "backend/rd-erp-server.jar" = "backend/rd-erp-server/target/rd-erp-server.jar"
        "deploy/trial/nginx.conf" = "deploy/trial/nginx.conf"
        "deploy/trial/15-validate-public-tenant.sh" = "deploy/trial/15-validate-public-tenant.sh"
        "deploy/trial/frontend-runtime.Dockerfile" = "deploy/trial/frontend-runtime.Dockerfile"
        "deploy/trial/compose.runtime.yaml" = "deploy/trial/compose.runtime.yaml"
        "deploy/trial/.env.example" = "deploy/trial/.env.example"
    }
    foreach ($file in Get-ChildItem -LiteralPath (Join-Path $root "frontend/dist-prod") -File -Recurse) {
        $relative = $file.FullName.Substring((Join-Path $root "frontend/dist-prod").Length + 1).Replace("\", "/")
        $mapping["frontend/$relative"] = $file.FullName
    }
    foreach ($file in $expectedDocs) { $mapping["docs/" + $file.Name] = $file.FullName }
    foreach ($pair in $mapping.GetEnumerator()) {
        $entry = $zip.GetEntry($prefix + $pair.Key)
        Check ($null -ne $entry) "Artifact missing: $($pair.Key)"
        $stream = $entry.Open()
        try { $actual = Hash-Stream $stream } finally { $stream.Dispose() }
        $source = if ([IO.Path]::IsPathRooted($pair.Value)) { $pair.Value } else { Join-Path $root $pair.Value }
        Check ($actual -eq (Hash-File $source)) "Artifact bytes differ: $($pair.Key)"
    }
    $validator = Read-Entry ($zip.GetEntry($prefix + "deploy/trial/15-validate-public-tenant.sh"))
    Check (-not $validator.Contains("`r")) "Linux entrypoint must use LF"
    $jarStream = New-Object IO.MemoryStream
    $entryStream = $zip.GetEntry($prefix + "backend/rd-erp-server.jar").Open()
    try { $entryStream.CopyTo($jarStream) } finally { $entryStream.Dispose() }
    $jarStream.Position = 0
    $jar = New-Object IO.Compression.ZipArchive($jarStream, [IO.Compression.ZipArchiveMode]::Read)
    try {
        $manifest = Read-Entry ($jar.GetEntry("META-INF/MANIFEST.MF"))
        Check ($manifest -match 'JarLauncher' -and $manifest -match 'Start-Class: com.ruiding.erp.server.RdErpServerApplication') "Not an executable ERP Boot JAR"
        Check (@($jar.Entries | Where-Object { $_.FullName -match '^BOOT-INF/lib/rd-erp-inventory-.+\.jar$' }).Count -eq 1) "Inventory module missing from Boot JAR"
    } finally { $jar.Dispose(); $jarStream.Dispose() }
    [pscustomobject]@{ result="PASS"; checks=$checks; frontendFiles=$mapping.Count-6-$expectedDocs.Count; gitCommit=$commit; sha256=$hash; scope="Local source-bound package, not Docker/HTTPS/field acceptance" } | ConvertTo-Json -Compress
} finally { $zip.Dispose() }
