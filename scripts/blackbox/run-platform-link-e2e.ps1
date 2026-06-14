# 平台主链路 E2E — 试验场执行 → 日志入库 → SSE 实时流 → 读代理 → SSO 探测
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$AppCode = "demo-app",
    [string]$SceneCode = "SCN20260531000001",
    [string]$PolicyFile = (Join-Path $PSScriptRoot "production-acceptance-policy.json"),
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"
. "$PSScriptRoot\_acceptance-common.ps1"

$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("platform-link-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$checks = New-Object System.Collections.Generic.List[object]
$api = "$BaseAdmin/api/zestflow"

function Add-Check($name, $ok, $note) {
    $checks.Add([pscustomobject]@{ name=$name; ok=$ok; note=$note }) | Out-Null
}

$policy = @{}
if (Test-Path $PolicyFile) {
    try { $policy = Get-Content $PolicyFile -Raw | ConvertFrom-Json } catch {}
}
$logWaitSec = if ($policy.linkE2e.logWaitSec) { [int]$policy.linkE2e.logWaitSec } else { 30 }
$sseConnectedMax = if ($policy.linkE2e.sseConnectedMaxMs) { [int]$policy.linkE2e.sseConnectedMaxMs } else { 5000 }
$sseDoneMax = if ($policy.linkE2e.sseDoneMaxMs) { [int]$policy.linkE2e.sseDoneMaxMs } else { 15000 }

Write-Host "=== Platform Link E2E ===" -ForegroundColor Cyan

$token = Login-AdminToken $BaseAdmin
if (-not $token) {
    Add-Check "login" $false "admin login failed"
    Save-AcceptanceReport $ReportJson @{ baseAdmin=$BaseAdmin; appCode=$AppCode } $checks
    if ($AllowSkip) { exit 2 }
    exit 1
}
Add-Check "login" $true "ok"
$h = @{ Authorization = "Bearer $token" }

$sceneBody = '{"userId":"U10086"}'
$exec = Invoke-PlaygroundScene $BaseAdmin $token $SceneCode $sceneBody 120
$execOk = $exec.ok
$executionId = Extract-ExecutionId $exec.body
Add-Check "playground-execute" ($execOk -and $executionId) $(if ($executionId) { "executionId=$executionId ms=$($exec.ms)" } else { "status=$($exec.status)" })

if ($executionId) {
    $inLogs = Wait-ExecutionInLogs $BaseAdmin $token $AppCode $executionId $logWaitSec
    Add-Check "logs-indexed" $inLogs $(if ($inLogs) { "found within ${logWaitSec}s" } else { "not indexed" })

    $streamUrl = "$api/logs/executions/$executionId/stream?appCode=$AppCode"
    $done = Read-SseUntilEvent $streamUrl $token "done" 15
    $doneOk = $done.ok -and ($done.ms -le $sseDoneMax)
    $connectedOk = ($done.snippet -match 'event:\s*connected') -and ($done.ms -le $sseDoneMax)
    if (-not $connectedOk -and $doneOk) { $connectedOk = $true }
    Add-Check "sse-connected" $connectedOk $("ms=$($done.ms) max=$sseDoneMax")
    Add-Check "sse-done" $doneOk $("ms=$($done.ms) max=$sseDoneMax")
} else {
    Add-Check "logs-indexed" $false "skipped-no-executionId"
    Add-Check "sse-connected" $false "skipped"
    Add-Check "sse-done" $false "skipped"
}

$chains = Invoke-AcceptanceApi GET "$api/chains?appCode=$AppCode&page=1&size=5" $null $h
$chainsOk = $chains.ok -and ($chains.body -match '"records"')
$stale = ($chains.body -match '"stale"\s*:\s*true')
Add-Check "chains-proxy-online" ($chainsOk -and -not $stale) $(if ($stale) { "offline snapshot" } else { "status=$($chains.status)" })

$sso = Invoke-AcceptanceApi GET "$api/auth/sso/config" $null $null 10
Add-Check "sso-config" $sso.ok "status=$($sso.status)"

Write-AcceptanceChecks $checks
Save-AcceptanceReport $ReportJson @{ baseAdmin=$BaseAdmin; appCode=$AppCode; sceneCode=$SceneCode } $checks
$fail = @($checks | Where-Object { -not $_.ok }).Count
if ($fail -eq 0) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
