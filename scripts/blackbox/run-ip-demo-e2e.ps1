# IP 演示租户隔离 E2E（要�?enterprise-e2e：mode=multi + ip-demo-mode=enabled�?
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("ip-demo-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$checks = New-Object System.Collections.Generic.List[object]

function Add-Check($name, $ok, $note) {
    $checks.Add([pscustomobject]@{ name=$name; ok=$ok; note=$note }) | Out-Null
}
function Save-Report {
    $out = @{ timestamp=(Get-Date).ToString("o"); checks=$checks }
    Set-Content -Path $ReportJson -Value ($out | ConvertTo-Json -Depth 6) -Encoding UTF8
    Write-Host "Saved: $ReportJson"
}

function Invoke-Api($method, $url, $body, $headers) {
    try {
        $p = @{ Uri=$url; Method=$method; TimeoutSec=30; UseBasicParsing=$true }
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

function Get-SceneCodes($headers) {
    $r = Invoke-Api GET "$BaseAdmin/api/zestflow/playground/scenes/list-all?appCode=demo-app" $null $headers
    if (-not $r.ok) { return @() }
    try { return @((ConvertFrom-Json $r.body).data | ForEach-Object { $_.sceneCode }) } catch { return @() }
}

Write-Host "=== IP Demo Tenant E2E ===" -ForegroundColor Cyan

$login = Invoke-Api POST "$BaseAdmin/api/zestflow/auth/login" '{"username":"admin","password":"admin123"}' $null
$token = $null
if ($login.ok) { try { $token = (ConvertFrom-Json $login.body).data.token } catch {} }
$hJwt = if ($token) { @{ Authorization = "Bearer $token" } } else { $null }

$mode = "unknown"; $ipDemo = "unknown"
if ($hJwt) {
    $feat = Invoke-Api GET "$BaseAdmin/api/zestflow/system/features" $null $hJwt
    if ($feat.ok) {
        try {
            $fj = ConvertFrom-Json $feat.body
            if ($fj.tenant) {
                $mode = $fj.tenant.mode
                $ipDemo = $fj.tenant.ipDemoMode
            }
        } catch {}
    }
}

if ($mode -ne "multi" -or $ipDemo -ne "enabled") {
    Add-Check "enterprise-profile" $false "mode=$mode ipDemo=$ipDemo; need profile enterprise-e2e + restart"
    Save-Report
    if ($AllowSkip) { Write-Host "SKIP (AllowSkip)" -ForegroundColor Yellow; exit 2 }
    exit 1
}
Add-Check "enterprise-profile" $true "multi+ip-demo"

$hdrB = @{ "X-Forwarded-For" = "10.0.0.101" }
$hdrA = @{ "X-Forwarded-For" = "10.0.0.102" }

$codesIpB = Get-SceneCodes $hdrB
$codesIpA = Get-SceneCodes $hdrA

Add-Check "ip-101-sees-tenant-b-scene" ($codesIpB -contains 'SCN20260602000002') "count=$($codesIpB.Count)"
Add-Check "ip-101-no-tenant1-only-bulk" ($codesIpB.Count -lt 35) "count=$($codesIpB.Count)"
Add-Check "ip-102-no-tenant-b-scene" (-not ($codesIpA -contains 'SCN20260602000002')) "count=$($codesIpA.Count)"

# 未预�?IP �?首次访问应自动建租户并克隆母版场景（V1 provisioner�?
$randomIp = "10.99." + (Get-Random -Minimum 1 -Maximum 250) + "." + (Get-Random -Minimum 1 -Maximum 250)
$hdrNew = @{ "X-Forwarded-For" = $randomIp }
$codesNew = Get-SceneCodes $hdrNew
Add-Check "random-ip-auto-provision" ($codesNew.Count -ge 28) "ip=$randomIp count=$($codesNew.Count)"

$fail = @($checks | Where-Object { -not $_.ok }).Count
Write-Host "Checks: $($checks.Count - $fail)/$($checks.Count) passed" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
Save-Report
if ($fail -gt 0) { exit 1 }
exit 0
