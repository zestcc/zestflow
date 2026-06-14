# ZestFlow v1.0.0 StrictV1 全量验收门禁
param(
    [string]$JavaHome = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "D:\IT\JDK17\jdk-17.0.19+10" }),
    [switch]$SkipMavenTest,
    [switch]$SkipNpmBuild,
    [switch]$SkipClusterTest,
    [switch]$SkipProfilesE2e,
    [switch]$SkipProductionAcceptance,
    [int]$SceneTimeoutSec = 300
)

$ErrorActionPreference = "Continue"
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("v1-acceptance-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$phases = New-Object System.Collections.Generic.List[object]
$script:exitCode = 0

function Add-Phase($name, $ok, $note) {
    $script:phases.Add([pscustomobject]@{ phase = $name; ok = $ok; note = $note }) | Out-Null
    if (-not $ok) { $script:exitCode = 1 }
    $color = if ($ok) { 'Green' } else { 'Red' }
    Write-Host ("[{0}] {1} — {2}" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $name, $note) -ForegroundColor $color
}

function Write-V1Report {
    $report = @{
        timestamp = (Get-Date).ToString("o")
        exitCode = $script:exitCode
        strictV1 = $true
        phases = $phases
        hint = @{
            prereq = "Admin :8080 + demo Netty :20550 + Collector :20650; MySQL + Flyway"
            command = ".\scripts\blackbox\run-v1-acceptance.ps1"
        }
    }
    Set-Content -Path $ReportJson -Value ($report | ConvertTo-Json -Depth 8) -Encoding UTF8
    Write-Host "StrictV1 report: $ReportJson" -ForegroundColor Cyan
    Write-Host "========== Exit $($script:exitCode) ==========" -ForegroundColor $(if ($script:exitCode -eq 0) { 'Green' } else { 'Red' })
    exit $script:exitCode
}

Write-Host "========== ZestFlow StrictV1 Acceptance (v1.0.0) ==========" -ForegroundColor Cyan

if (-not (Test-Path $JavaHome)) {
    Add-Phase "java-home" $false "invalid JAVA_HOME=$JavaHome"
    Write-V1Report
}
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;" + $env:Path

# --- 1. 全仓库 mvn test ---
if (-not $SkipMavenTest) {
    Push-Location $Root
    Write-Host "mvn -B test (full repository) ..." -ForegroundColor DarkGray
    & mvn -B test 2>&1 | Out-Host
    Add-Phase "mvn-test-full" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"
    Pop-Location
    if ($LASTEXITCODE -ne 0) { Write-V1Report }
} else {
    Add-Phase "mvn-test-full" $true "skipped"
}

# --- 2. Admin cluster profile 编译 + 测试 ---
if (-not $SkipClusterTest) {
    Push-Location $Root
    Write-Host "mvn -B test -pl zestflow-admin -Pcluster -am ..." -ForegroundColor DarkGray
    & mvn -B test -pl zestflow-admin -Pcluster -am 2>&1 | Out-Host
    Add-Phase "mvn-test-admin-cluster" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"
    Pop-Location
    if ($LASTEXITCODE -ne 0) { Write-V1Report }
} else {
    Add-Phase "mvn-test-admin-cluster" $true "skipped"
}

# --- 3. 前端 build → Admin static ---
if (-not $SkipNpmBuild) {
    $uiDir = Join-Path $Root "zestflow-admin-ui"
    Push-Location $uiDir
    Write-Host "npm run build ..." -ForegroundColor DarkGray
    & npm run build 2>&1 | Out-Host
    Add-Phase "npm-run-build" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"
    Pop-Location
    if ($LASTEXITCODE -ne 0) { Write-V1Report }
} else {
    Add-Phase "npm-run-build" $true "skipped"
}

# --- 4. 全 profile E2E ---
if (-not $SkipProfilesE2e) {
    $profilesScript = Join-Path $PSScriptRoot "run-all-profiles-e2e.ps1"
    & $profilesScript -SkipMavenTest -JavaHome $JavaHome -SceneTimeoutSec $SceneTimeoutSec 2>&1 | Out-Host
    $profilesExit = [int]$LASTEXITCODE
    Add-Phase "all-profiles-e2e" ($profilesExit -eq 0) "exit=$profilesExit"
    if ($profilesExit -ne 0) { Write-V1Report }
} else {
    Add-Phase "all-profiles-e2e" $true "skipped"
}

# --- 5. 严格 production-acceptance（perf + offline） ---
if (-not $SkipProductionAcceptance) {
    $prodScript = Join-Path $PSScriptRoot "run-production-acceptance.ps1"
    & $prodScript -SkipMavenTest -StrictV1 -JavaHome $JavaHome -SceneTimeoutSec $SceneTimeoutSec 2>&1 | Out-Host
    $prodExit = [int]$LASTEXITCODE
    Add-Phase "production-acceptance-strictV1" ($prodExit -eq 0) "exit=$prodExit"
} else {
    Add-Phase "production-acceptance-strictV1" $true "skipped"
}

Write-V1Report
