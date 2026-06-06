# 多租户数据隔�?E2E（要�?Admin 已启�?enterprise-e2e profile：tenant.mode=multi�?
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("tenant-multi-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
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

Write-Host "=== Tenant Multi E2E ===" -ForegroundColor Cyan

$login = Invoke-Api POST "$BaseAdmin/api/zestflow/auth/login" '{"username":"admin","password":"admin123"}' $null
$token = $null
if ($login.ok) { try { $token = (ConvertFrom-Json $login.body).data.token } catch {} }
if (-not $token) {
    Add-Check "login" $false "cannot login"
    Save-Report; if (-not $AllowSkip) { exit 1 }; exit 0
}
$h = @{ Authorization = "Bearer $token" }

$feat = Invoke-Api GET "$BaseAdmin/api/zestflow/system/features" $null $h
$mode = "unknown"
if ($feat.ok) {
    try {
        $fj = ConvertFrom-Json $feat.body
        if ($fj.tenant) { $mode = $fj.tenant.mode }
    } catch {}
}
if ($mode -ne "multi") {
    Add-Check "tenant-mode-multi" $false "runtime mode=$mode; restart Admin with profile enterprise-e2e"
    Save-Report
    if ($AllowSkip) { Write-Host "SKIP (AllowSkip): not in multi mode" -ForegroundColor Yellow; exit 2 }
    exit 1
}
Add-Check "tenant-mode-multi" $true "mode=multi"

$codesT1 = Get-SceneCodes $h
$hasBOnT1 = $codesT1 -contains 'SCN20260602000002'
Add-Check "tenant1-no-tenant-b-scene" (-not $hasBOnT1) "tenant1 scenes=$($codesT1.Count) hasB=$hasBOnT1"

$sw = Invoke-Api POST "$BaseAdmin/api/zestflow/auth/switch-tenant/2" $null $h
$token2 = $null
if ($sw.ok) { try { $token2 = (ConvertFrom-Json $sw.body).data.token } catch {} }
if (-not $token2) {
    Add-Check "switch-tenant-2" $false "switch failed status=$($sw.status)"
    Save-Report; exit 1
}
Add-Check "switch-tenant-2" $true ""
$h2 = @{ Authorization = "Bearer $token2" }

$codesT2 = Get-SceneCodes $h2
$hasBOnT2 = $codesT2 -contains 'SCN20260602000002'
Add-Check "tenant2-has-tenant-b-scene" $hasBOnT2 "tenant2 scenes=$($codesT2.Count)"

$fail = @($checks | Where-Object { -not $_.ok }).Count
Write-Host "Checks: $($checks.Count - $fail)/$($checks.Count) passed" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
Save-Report
if ($fail -gt 0) { exit 1 }
exit 0
