# Executor 离线读快照黑盒 E2E — 在线/离线两种模式均可 PASS
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$AppCode = "demo-app",
    [switch]$RequireStaleCache,
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("executor-read-cache-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$checks = New-Object System.Collections.Generic.List[object]
$api = "$BaseAdmin/api/zestflow"

function Add-Check($name, $ok, $note) {
    $checks.Add([pscustomobject]@{ name=$name; ok=$ok; note=$note }) | Out-Null
}
function Save-Report {
    $out = @{
        timestamp = (Get-Date).ToString("o")
        baseAdmin = $BaseAdmin
        appCode = $AppCode
        requireStaleCache = [bool]$RequireStaleCache
        checks = $checks
    }
    Set-Content -Path $ReportJson -Value ($out | ConvertTo-Json -Depth 6) -Encoding UTF8
    Write-Host "Saved: $ReportJson"
}

function Invoke-Api($method, $url, $body, $headers, [int]$TimeoutSec = 30) {
    try {
        $p = @{ Uri=$url; Method=$method; TimeoutSec=$TimeoutSec; UseBasicParsing=$true }
        if ($headers) { $p.Headers = $headers }
        if ($null -ne $body) { $p.Body = $body; $p.ContentType = "application/json" }
        $r = Invoke-WebRequest @p
        return @{ ok=$true; status=[int]$r.StatusCode; body=$r.Content }
    } catch {
        $st = 0; $b = ""
        if ($_.Exception.Response) {
            $st = [int]$_.Exception.Response.StatusCode.value__
            try { $b = (New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch {}
        }
        return @{ ok=$false; status=$st; body=$b }
    }
}

Write-Host "=== Executor Read Cache E2E ===" -ForegroundColor Cyan

$login = Invoke-Api POST "$api/auth/login" '{"username":"admin","password":"admin123"}' $null
if (-not $login.ok) {
    Add-Check "login" $false "status=$($login.status)"
    Save-Report
    if ($AllowSkip) { exit 2 }
    exit 1
}
$token = (ConvertFrom-Json $login.body).data.token
$h = @{ Authorization = "Bearer $token" }
Add-Check "login" $true "ok"

$chains = Invoke-Api GET "$api/chains?appCode=$AppCode&page=1&size=5" $null $h
$chainsOk = $chains.ok -and ($chains.body -match '"records"')
Add-Check "chains-proxy" $chainsOk $(if ($chainsOk) { "status=$($chains.status)" } else { "status=$($chains.status)" })

$hasStaleMeta = $false
if ($chainsOk -and $chains.body -match '"_readCache"') {
    $hasStaleMeta = $chains.body -match '"stale"\s*:\s*true'
}

if ($RequireStaleCache) {
    Add-Check "read-cache-stale" $hasStaleMeta $(if ($hasStaleMeta) { "offline snapshot active" } else { "stop demo-app executor then rerun with -RequireStaleCache" })
} else {
    if ($hasStaleMeta) {
        Add-Check "read-cache-stale" $true "offline mode detected (_readCache.stale=true)"
    } else {
        Add-Check "read-cache-online" $true "no stale meta (executor online or cache warm)"
    }
}

$designs = Invoke-Api GET "$api/designs?appCode=$AppCode&page=1&size=5" $null $h
Add-Check "designs-proxy" ($designs.ok) "status=$($designs.status)"

$fail = @($checks | Where-Object { -not $_.ok }).Count
foreach ($c in $checks) {
    $color = if ($c.ok) { 'Green' } else { 'Red' }
    Write-Host ("  [{0}] {1} — {2}" -f $(if ($c.ok) { 'PASS' } else { 'FAIL' }), $c.name, $c.note) -ForegroundColor $color
}
Save-Report
if ($fail -eq 0) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
