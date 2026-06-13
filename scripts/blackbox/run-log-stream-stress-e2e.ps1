# 日志 SSE 并发压测 — 同一 executionId 多连接直到 connected
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
$ReportJson = Join-Path $OutDir ("log-stream-stress-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$checks = New-Object System.Collections.Generic.List[object]
$api = "$BaseAdmin/api/zestflow"

function Add-Check($name, $ok, $note, $metrics) {
    $checks.Add([pscustomobject]@{ name=$name; ok=$ok; note=$note; metrics=$metrics }) | Out-Null
}

$policy = @{}
if (Test-Path $PolicyFile) {
    try { $policy = Get-Content $PolicyFile -Raw | ConvertFrom-Json } catch {}
}
$concurrency = if ($policy.sseStress.concurrency) { [int]$policy.sseStress.concurrency } else { 8 }
$p99Limit = if ($policy.sseStress.connectedP99Ms) { [int]$policy.sseStress.connectedP99Ms } else { 4000 }
$maxLimit = if ($policy.sseStress.connectedMaxMs) { [int]$policy.sseStress.connectedMaxMs } else { 8000 }

Write-Host "=== Log Stream Stress E2E (concurrency=$concurrency) ===" -ForegroundColor Cyan

$token = Login-AdminToken $BaseAdmin
if (-not $token) {
    Add-Check "login" $false "failed" $null
    Save-AcceptanceReport $ReportJson @{ concurrency=$concurrency } $checks
    if ($AllowSkip) { exit 2 }
    exit 1
}
Add-Check "login" $true "ok" $null

$exec = Invoke-PlaygroundScene $BaseAdmin $token $SceneCode '{"userId":"U10086"}' 120
$executionId = Extract-ExecutionId $exec.body
if (-not $executionId) {
    Add-Check "prepare-execution" $false "no executionId" $null
    Save-AcceptanceReport $ReportJson @{ concurrency=$concurrency } $checks
    if ($AllowSkip) { exit 2 }
    exit 1
}
Add-Check "prepare-execution" $true $executionId $null
Wait-ExecutionInLogs $BaseAdmin $token $AppCode $executionId 20 | Out-Null

$streamUrl = "$api/logs/executions/$executionId/stream?appCode=$AppCode"
$latencies = New-Object System.Collections.Generic.List[int]
$jobs = @()
for ($i = 0; $i -lt $concurrency; $i++) {
    $jobs += Start-Job -ScriptBlock {
        param($url, $tok)
        . "$using:PSScriptRoot\_acceptance-common.ps1"
        Read-SseUntilEvent $url $tok "connected" 15
    } -ArgumentList $streamUrl, $token
}
$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job -Force

foreach ($r in $results) {
    if ($r.ok) { $latencies.Add([int]$r.ms) }
}
$okCount = $latencies.Count
$sorted = $latencies | Sort-Object
$p99 = if ($sorted.Count -gt 0) { $sorted[[Math]::Min($sorted.Count - 1, [Math]::Ceiling($sorted.Count * 0.99) - 1])] } else { 0 }
$max = if ($sorted.Count -gt 0) { $sorted[-1] } else { 0 }
$metrics = @{ concurrency=$concurrency; success=$okCount; p99Ms=$p99; maxMs=$max }

$stressOk = ($okCount -eq $concurrency) -and ($p99 -le $p99Limit) -and ($max -le $maxLimit)
Add-Check "sse-concurrent-connected" $stressOk "success=$okCount/$concurrency p99=${p99}ms max=${max}ms" $metrics

Write-AcceptanceChecks $checks
Save-AcceptanceReport $ReportJson @{ baseAdmin=$BaseAdmin; appCode=$AppCode; executionId=$executionId } $checks
$fail = @($checks | Where-Object { -not $_.ok }).Count
if ($fail -eq 0) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
