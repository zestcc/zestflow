# 日志 SSE 实时流黑盒 E2E — 先触发试验场再验证 SSE
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$AppCode = "demo-app",
    [string]$SceneCode = "SCN20260531000001",
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"
. "$PSScriptRoot\_acceptance-common.ps1"

$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("log-live-stream-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$checks = New-Object System.Collections.Generic.List[object]
$api = "$BaseAdmin/api/zestflow"

function Add-Check($name, $ok, $note) {
    $checks.Add([pscustomobject]@{ name=$name; ok=$ok; note=$note }) | Out-Null
}

Write-Host "=== Log Live Stream E2E ===" -ForegroundColor Cyan

$token = Login-AdminToken $BaseAdmin
if (-not $token) {
    Add-Check "login" $false "status=failed"
    Save-AcceptanceReport $ReportJson @{ baseAdmin=$BaseAdmin } $checks
    if ($AllowSkip) { exit 2 }
    exit 1
}
Add-Check "login" $true "ok"
$h = @{ Authorization = "Bearer $token" }

$noJwt = Invoke-AcceptanceApi GET "$api/logs/executions/fake-exec/stream?appCode=$AppCode" $null $null 10
$denied = ($noJwt.status -in 401, 403)
Add-Check "stream-no-jwt" $denied $(if ($denied) { "status=$($noJwt.status)" } else { "expected 401/403 got $($noJwt.status)" })

$exec = Invoke-PlaygroundScene $BaseAdmin $token $SceneCode '{"userId":"U10086"}' 120
$executionId = Extract-ExecutionId $exec.body
Add-Check "playground-trigger" ($exec.ok -and $executionId) $(if ($executionId) { "executionId=$executionId" } else { "status=$($exec.status)" })

if (-not $executionId) {
    $listBody = @{ appCode = $AppCode; page = 1; pageSize = 5 } | ConvertTo-Json -Compress
    $list = Invoke-AcceptanceApi POST "$api/logs/executions" $listBody $h
    if ($list.ok) {
        try {
            $records = (ConvertFrom-Json $list.body).data.records
            if ($records -and $records.Count -gt 0) { $executionId = $records[0].executionId }
        } catch {}
    }
}

if ($executionId) {
    Wait-ExecutionInLogs $BaseAdmin $token $AppCode $executionId 25 | Out-Null
    $streamUrl = "$api/logs/executions/$executionId/stream?appCode=$AppCode"
    $connected = Read-SseUntilEvent $streamUrl $token "connected" 12
    Add-Check "sse-connected" $connected.ok $(if ($connected.ok) { "ms=$($connected.ms)" } else { "timeout" })
    $done = Read-SseUntilEvent $streamUrl $token "done" 15
    Add-Check "sse-done" $done.ok $(if ($done.ok) { "ms=$($done.ms)" } else { "timeout" })
} else {
    Add-Check "sse-connected" $false "no executionId"
    Add-Check "sse-done" $false "no executionId"
}

Write-AcceptanceChecks $checks
Save-AcceptanceReport $ReportJson @{ baseAdmin=$BaseAdmin; appCode=$AppCode } $checks
$fail = @($checks | Where-Object { -not $_.ok }).Count
if ($fail -eq 0) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
