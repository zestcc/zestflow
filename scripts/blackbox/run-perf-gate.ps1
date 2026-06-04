# Phase 2c — 执行引擎性能门禁（JMH 编排层 + 并发 HTTP 压测 + 可选运行时黑盒）
param(
    [string]$JavaHome = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "D:\IT\JDK17\jdk-17.0.19+10" }),
    [switch]$SkipHttpPerf,
    [switch]$SkipRuntimeBlackbox,
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$BaseNetty = "http://127.0.0.1:20550",
    [string]$PolicyFile = (Join-Path $PSScriptRoot "perf-gate-policy.json")
)

$ErrorActionPreference = "Continue"
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("perf-gate-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$phases = New-Object System.Collections.Generic.List[object]
$script:exitCode = 0

function Add-Phase($name, $ok, $note, $metrics) {
    $script:phases.Add([pscustomobject]@{
        phase = $name; ok = $ok; note = $note; metrics = $metrics
    }) | Out-Null
    if (-not $ok) { $script:exitCode = 1 }
    $color = if ($ok) { 'Green' } else { 'Red' }
    Write-Host ("[{0}] {1} — {2}" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $name, $note) -ForegroundColor $color
}

function Write-PerfReport {
    $report = @{
        timestamp = (Get-Date).ToString("o")
        exitCode = $script:exitCode
        policyFile = $PolicyFile
        phases = $phases
        hint = "mvn test -pl zestflow-executor,zestflow-demo -am -Pperf"
    }
    Set-Content -Path $ReportJson -Value ($report | ConvertTo-Json -Depth 8) -Encoding UTF8
    Write-Host "Perf gate report: $ReportJson" -ForegroundColor Cyan
    Write-Host "========== Exit $($script:exitCode) ==========" -ForegroundColor $(if ($script:exitCode -eq 0) { 'Green' } else { 'Red' })
    exit $script:exitCode
}

Write-Host "========== ZestFlow Perf Gate (Phase 2c) ==========" -ForegroundColor Cyan
Write-Host "JAVA_HOME=$JavaHome"

if (-not (Test-Path $JavaHome)) {
    Add-Phase "java-home" $false "JAVA_HOME invalid: $JavaHome" $null
    Write-PerfReport
}

$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;" + $env:Path

Push-Location $Root
Write-Host "mvn test -pl zestflow-common,zestflow-executor -am -Pperf (JMH gate) ..." -ForegroundColor DarkGray
& mvn -q test -pl zestflow-common,zestflow-executor -am -Pperf
$engineExit = $LASTEXITCODE
Add-Phase "engine-jmh-perf" ($engineExit -eq 0) "exit=$engineExit" $null
if ($engineExit -ne 0) {
    Pop-Location
    Write-PerfReport
}

if (-not $SkipHttpPerf) {
    Write-Host "mvn test -pl zestflow-demo -am -Pperf (ConcurrentStressTest) ..." -ForegroundColor DarkGray
    & mvn -q test -pl zestflow-demo -am -Pperf
    $httpExit = $LASTEXITCODE
    Add-Phase "http-concurrent-perf" ($httpExit -eq 0) "exit=$httpExit" $null
    if ($httpExit -ne 0) {
        Pop-Location
        Write-PerfReport
    }
} else {
    Add-Phase "http-concurrent-perf" $true "skipped" $null
}
Pop-Location

if (-not $SkipRuntimeBlackbox) {
    $nettyUp = $false
    $collectorUp = $false
    try {
        $ping = Invoke-WebRequest -Uri "$BaseNetty/health" -UseBasicParsing -TimeoutSec 5
        $nettyUp = ($ping.StatusCode -eq 200)
    } catch {}
    try {
        $cp = Invoke-WebRequest -Uri "http://127.0.0.1:20650/collector/health" -UseBasicParsing -TimeoutSec 5
        $collectorUp = ($cp.StatusCode -eq 200)
    } catch {}

    if (-not $nettyUp) {
        Add-Phase "runtime-blackbox-perf" $true "skipped-netty-down collector=$collectorUp" $null
    } else {
        & "$PSScriptRoot\run-blackbox.ps1" -BaseAdmin $BaseAdmin -BaseNetty $BaseNetty -PerfGateOnly
        Add-Phase "runtime-blackbox-perf" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE" $null
    }
} else {
    Add-Phase "runtime-blackbox-perf" $true "skipped" $null
}

Write-PerfReport
