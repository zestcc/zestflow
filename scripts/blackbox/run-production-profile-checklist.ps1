# 生产 profile checklist — 调用 /system/production-readiness
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [switch]$AllowSkip,
    [switch]$RequireProdProfile
)

$ErrorActionPreference = "Continue"
. "$PSScriptRoot\_acceptance-common.ps1"

$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("production-profile-checklist-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$checks = New-Object System.Collections.Generic.List[object]

function Add-Check($name, $ok, $note) {
    $checks.Add([pscustomobject]@{ name=$name; ok=$ok; note=$note }) | Out-Null
}

Write-Host "=== Production Profile Checklist ===" -ForegroundColor Cyan

$token = Login-AdminToken $BaseAdmin
if (-not $token) {
    Add-Check "login" $false "failed"
    Save-AcceptanceReport $ReportJson @{ baseAdmin=$BaseAdmin } $checks
    if ($AllowSkip) { exit 2 }
    exit 1
}
Add-Check "login" $true "ok"
$h = @{ Authorization = "Bearer $token" }

$resp = Invoke-AcceptanceApi GET "$BaseAdmin/api/zestflow/system/production-readiness" $null $h
if (-not $resp.ok) {
    Add-Check "production-readiness-api" $false "status=$($resp.status)"
    Save-AcceptanceReport $ReportJson @{ baseAdmin=$BaseAdmin } $checks
    if ($AllowSkip) { exit 2 }
    exit 1
}

$data = $null
try {
    $root = ConvertFrom-Json $resp.body
    $data = if ($root.data) { $root.data } else { $root }
} catch {
    Add-Check "production-readiness-parse" $false "invalid-json"
    Save-AcceptanceReport $ReportJson @{ baseAdmin=$BaseAdmin } $checks
    exit 1
}

$prodProfile = [bool]$data.prodProfile
Add-Check "prod-profile" $(if ($RequireProdProfile) { $prodProfile } else { $true }) $(if ($prodProfile) { "active" } else { "not-prod" })

foreach ($item in @($data.items)) {
    $ok = [bool]$item.ok
    if ($RequireProdProfile) {
        Add-Check ([string]$item.name) $ok ([string]$item.note)
    } else {
        # 开发环境仅报告，不因 token 未配失败
        Add-Check ([string]$item.name) $true ("dev-report ok=$ok " + [string]$item.note)
    }
}

$ready = [bool]$data.ready
if ($RequireProdProfile) {
    Add-Check "overall-ready" $ready $("failedCount=$($data.failedCount)")
} else {
    Add-Check "overall-ready" $true $("dev-report ready=$ready")
}

Write-AcceptanceChecks $checks
Save-AcceptanceReport $ReportJson @{ baseAdmin=$BaseAdmin; readiness=$data } $checks
$fail = @($checks | Where-Object { -not $_.ok }).Count
if ($fail -eq 0) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
