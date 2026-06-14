# Executor 离线写操作拦截 E2E — 需停止 demo-app 后验证
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$AppCode = "demo-app",
    [switch]$RequireOffline,
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"
. "$PSScriptRoot\_acceptance-common.ps1"

$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("executor-offline-write-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$checks = New-Object System.Collections.Generic.List[object]
$api = "$BaseAdmin/api/zestflow"

function Add-Check($name, $ok, $note) {
    $checks.Add([pscustomobject]@{ name=$name; ok=$ok; note=$note }) | Out-Null
}

Write-Host "=== Executor Offline Write E2E ===" -ForegroundColor Cyan

$token = Login-AdminToken $BaseAdmin
if (-not $token) {
    Add-Check "login" $false "failed"
    Save-AcceptanceReport $ReportJson @{} $checks
    if ($AllowSkip) { exit 2 }
    exit 1
}
Add-Check "login" $true "ok"
$h = @{ Authorization = "Bearer $token" }

$chains = Invoke-AcceptanceApi GET "$api/chains?appCode=$AppCode&page=1&size=1" $null $h
$online = $chains.ok -and ($chains.body -notmatch '"stale"\s*:\s*true')
Add-Check "executor-online-detect" $true $(if ($online) { "online" } else { "offline-or-stale" })

$body = (@{ name = "OfflineWriteTest"; appCode = $AppCode; chainKey = "offline.write.test" } | ConvertTo-Json -Compress)
$post = Invoke-AcceptanceApi POST "$api/chains" $body $h

if ($RequireOffline) {
    $blocked = ($post.body -match 'EXECUTOR_OFFLINE') `
        -or ($post.body -match '"errorCode"\s*:\s*"EXECUTOR_OFFLINE"') `
        -or ($post.status -ge 400) `
        -or (($post.body -match '"code"\s*:\s*400') -and ($post.body -match 'EXECUTOR_OFFLINE|离线'))
    Add-Check "post-blocked-offline" $blocked $(if ($blocked) { "status=$($post.status)" } else { "expected block when executor offline body=$($post.body.Substring(0,[Math]::Min(120,$post.body.Length)))" })
} elseif ($online) {
    Add-Check "post-when-online" ($post.ok -or ($post.body -match '"code"')) "status=$($post.status) skipped-offline-check"
    Add-Check "post-blocked-offline" $true "skipped-executor-online"
} else {
    $blocked = ($post.body -match 'EXECUTOR_OFFLINE') -or ($post.status -ge 400)
    Add-Check "post-blocked-offline" $blocked $(if ($blocked) { "auto-detected offline" } else { "status=$($post.status)" })
}

Write-AcceptanceChecks $checks
Save-AcceptanceReport $ReportJson @{ requireOffline = [bool]$RequireOffline; online = $online } $checks
$fail = @($checks | Where-Object { -not $_.ok }).Count
if ($fail -eq 0) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
