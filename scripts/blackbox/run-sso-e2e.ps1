# SSO 黑盒 E2E — Discovery + Admin SSO 端点 + JSON 报告
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$SsoBase = "http://127.0.0.1:9000",
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("sso-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$checks = New-Object System.Collections.Generic.List[object]
$api = "$BaseAdmin/api/zestflow"

function Add-Check($name, $ok, $note) {
    $checks.Add([pscustomobject]@{ name=$name; ok=$ok; note=$note }) | Out-Null
}
function Save-Report {
    $out = @{
        timestamp = (Get-Date).ToString("o")
        baseAdmin = $BaseAdmin
        ssoBase = $SsoBase
        checks = $checks
    }
    Set-Content -Path $ReportJson -Value ($out | ConvertTo-Json -Depth 6) -Encoding UTF8
    Write-Host "Saved: $ReportJson"
}

function Invoke-Api($method, $url, $body, $headers, [int]$TimeoutSec = 20) {
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

Write-Host "=== SSO E2E ===" -ForegroundColor Cyan

$adminReachable = $false
$ping = Invoke-Api GET "$api/auth/sso/config" $null $null 5
if ($ping.ok) { $adminReachable = $true }
if (-not $adminReachable) {
    Add-Check "admin-reachable" $false "GET /auth/sso/config failed status=$($ping.status)"
    Save-Report
    if ($AllowSkip) { exit 2 }
    exit 1
}
Add-Check "admin-reachable" $true "status=$($ping.status)"

$configOk = $false
$configEnabled = $false
try {
    $cfgJson = ConvertFrom-Json $ping.body
    $configOk = ($null -ne $cfgJson.data)
    if ($configOk) {
        $configEnabled = [bool]$cfgJson.data.enabled
        Add-Check "sso-config" $true "enabled=$configEnabled provider=$($cfgJson.data.provider)"
    } else {
        Add-Check "sso-config" $false "empty data"
    }
} catch {
    Add-Check "sso-config" $false $_.Exception.Message
}

if ($configEnabled) {
    $discoveryOk = $false
    try {
        $discovery = Invoke-RestMethod -Uri "$SsoBase/api/public/.well-known/openid-configuration" -Method Get -TimeoutSec 10
        $discoveryOk = [bool]$discovery.authorization_endpoint
        Add-Check "oidc-discovery" $discoveryOk $(if ($discoveryOk) { "authorization_endpoint ok" } else { "missing authorization_endpoint" })
    } catch {
        Add-Check "oidc-discovery" $false $_.Exception.Message
    }
} else {
    Add-Check "oidc-discovery" $true "skipped-sso-disabled"
}

if ($configEnabled) {
    $auth = Invoke-Api GET "$api/auth/sso/authorize" $null $null
    $authOk = $false
    if ($auth.ok) {
        try {
            $authJson = ConvertFrom-Json $auth.body
            $authOk = ($authJson.data.authorizationUrl -and $authJson.data.state)
            Add-Check "sso-authorize" $authOk $(if ($authOk) { "state=$($authJson.data.state)" } else { "incomplete response" })
        } catch {
            Add-Check "sso-authorize" $false $_.Exception.Message
        }
    } else {
        Add-Check "sso-authorize" $false "status=$($auth.status)"
    }

    $logout = Invoke-Api GET "$api/auth/sso/logout-url" $null $null
    $logoutOk = $logout.ok
    Add-Check "sso-logout-url" $logoutOk $(if ($logoutOk) { "status=$($logout.status)" } else { "status=$($logout.status)" })
} else {
    Add-Check "sso-authorize" $true "skipped-sso-disabled"
    Add-Check "sso-logout-url" $true "skipped-sso-disabled"
}

$fail = @($checks | Where-Object { -not $_.ok }).Count
foreach ($c in $checks) {
    $color = if ($c.ok) { 'Green' } else { 'Red' }
    Write-Host ("  [{0}] {1} — {2}" -f $(if ($c.ok) { 'PASS' } else { 'FAIL' }), $c.name, $c.note) -ForegroundColor $color
}
Save-Report
if ($fail -eq 0) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
