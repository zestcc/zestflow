# playground.enabled=false 验收 �?需 Admin profiles=local,playground-disabled-e2e
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"

function Invoke-Api($method, $url, $body, $headers) {
    try {
        $p = @{ Uri=$url; Method=$method; TimeoutSec=20; UseBasicParsing=$true }
        if ($headers) { $p.Headers = $headers }
        if ($null -ne $body) { $p.Body = $body; $p.ContentType = "application/json" }
        $r = Invoke-WebRequest @p
        return @{ status=[int]$r.StatusCode; ok=$true; body=$r.Content }
    } catch {
        $st = 0
        if ($_.Exception.Response) { $st = [int]$_.Exception.Response.StatusCode.value__ }
        return @{ status=$st; ok=$false; body="" }
    }
}

Write-Host "=== Playground Disabled E2E ===" -ForegroundColor Cyan

$login = Invoke-Api POST "$BaseAdmin/api/zestflow/auth/login" '{"username":"admin","password":"admin123"}' $null
if (-not $login.ok) {
    Write-Host "Login failed" -ForegroundColor Red
    if ($AllowSkip) { exit 2 }
    exit 1
}
$token = (ConvertFrom-Json $login.body).data.token
$h = @{ Authorization = "Bearer $token" }

$feat = Invoke-Api GET "$BaseAdmin/api/zestflow/system/features" $null $h
$pgEnabled = $true
if ($feat.ok) {
    try {
        $fj = ConvertFrom-Json $feat.body
        $pgEnabled = [bool]$fj.playground.enabled
    } catch {}
}

if ($pgEnabled) {
    Write-Host "playground 仍为 enabled，请重启 Admin: profiles=local,playground-disabled-e2e" -ForegroundColor Yellow
    if ($AllowSkip) { exit 2 }
    exit 1
}

$list = Invoke-Api GET "$BaseAdmin/api/zestflow/playground/scenes/list-all?appCode=demo-app" $null $h
$exec = Invoke-Api POST "$BaseAdmin/api/zestflow/playground/execute/SCN20260601000001" '{}' $h

$list404 = ($list.status -eq 404)
$exec404 = ($exec.status -eq 404)
$allOk = (-not $pgEnabled) -and $list404 -and $exec404

Write-Host "features.playground.enabled=$pgEnabled list=$($list.status) execute=$($exec.status)" -ForegroundColor $(if ($allOk) { 'Green' } else { 'Red' })
if ($allOk) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
