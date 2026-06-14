# 全 profile E2E 编排：local → enterprise → security → playground-disabled → perf
param(
    [string]$JavaHome = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "D:\IT\JDK17\jdk-17.0.19+10" }),
    [switch]$SkipMavenTest,
    [int]$SceneTimeoutSec = 300
)

$ErrorActionPreference = "Continue"
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
. "$PSScriptRoot\_acceptance-stack.ps1"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("all-profiles-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$phases = New-Object System.Collections.Generic.List[object]
$script:exitCode = 0

function Add-Phase($name, $ok, $note) {
    $script:phases.Add([pscustomobject]@{ phase = $name; ok = $ok; note = $note }) | Out-Null
    if (-not $ok) { $script:exitCode = 1 }
    $color = if ($ok) { 'Green' } else { 'Red' }
    Write-Host ("[{0}] {1} — {2}" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $name, $note) -ForegroundColor $color
}

function With-StrictV1Profile([string]$Profiles) {
    if ($Profiles -like '*strictv1-e2e*') { return $Profiles }
    if ([string]::IsNullOrWhiteSpace($Profiles)) { return "local,strictv1-e2e" }
    return "$Profiles,strictv1-e2e"
}

function Boot-Stack([string]$AdminProfiles, [string]$DemoProfiles) {
    return Boot-AcceptanceProfileStack $Root $JavaHome `
        (With-StrictV1Profile $AdminProfiles) (With-StrictV1Profile $DemoProfiles)
}

function Stop-Services { Stop-AcceptanceStack }

function Wait-Admin([int]$TimeoutSec = 180) { return Wait-AcceptanceAdmin "http://127.0.0.1:8080" $TimeoutSec }

function Run-Script($name, [hashtable]$NamedArgs = @{}) {
    $path = Join-Path $PSScriptRoot $name
    if ($NamedArgs.Count -gt 0) {
        & $path @NamedArgs 2>&1 | Out-Host
    } else {
        & $path 2>&1 | Out-Host
    }
    return [int]$LASTEXITCODE
}

function Wait-AdminReady([int]$Retries = 5) {
    for ($i = 0; $i -lt $Retries; $i++) {
        if (Wait-Admin -TimeoutSec 30) { return $true }
        Start-Sleep -Seconds 3
    }
    return $false
}

Write-Host "========== All Profiles E2E ==========" -ForegroundColor Cyan
Write-Host "Root: $Root"

if (-not $SkipMavenTest) {
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;" + $env:Path
    Push-Location $Root
    foreach ($m in @("zestflow-common", "zestflow-executor", "zestflow-demo", "zestflow-collector/collector-jdbc", "zestflow-admin")) {
        Write-Host "mvn test -pl $m -am ..." -ForegroundColor DarkGray
        & mvn -q test -pl $m -am 2>&1 | Out-Null
        Add-Phase "mvn-test-$($m -replace '/','-')" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"
        if ($LASTEXITCODE -ne 0) { Pop-Location; break }
    }
    Pop-Location
} else {
    Add-Phase "mvn-test-all" $true "skipped"
}

# --- local: matrix + fullGreen + partialGreen ---
$ok = Boot-Stack "local" "local"
if ($ok) {
    $ec = Run-Script "run-chain-matrix-e2e.ps1" @{}
    Add-Phase "chain-matrix-e2e" ($ec -eq 0) "exit=$ec"
    Start-Sleep -Seconds 5
    if (-not (Wait-AdminReady)) { Add-Phase "admin-recover-after-matrix" $false "login not ready"; }
    $ec = Run-Script "run-full-e2e.ps1" @{ E2eProfile = "fullGreen"; SceneTimeoutSec = $SceneTimeoutSec }
    Add-Phase "full-e2e-fullGreen" ($ec -eq 0) "exit=$ec"
    $ec = Run-Script "run-full-e2e.ps1" @{ E2eProfile = "partialGreen"; SceneTimeoutSec = $SceneTimeoutSec }
    Add-Phase "full-e2e-partialGreen" ($ec -eq 0) "exit=$ec"
    $ec = Run-Script "run-rbac-horizontal-e2e.ps1" @{}
    Add-Phase "rbac-horizontal-e2e" ($ec -eq 0) "exit=$ec"
} else {
    Add-Phase "boot-local" $false "admin/demo not ready"
}

# --- enterprise-e2e ---
$ok = Boot-Stack "local,enterprise-e2e" "local"
if ($ok) {
    $ec = Run-Script "run-tenant-multi-e2e.ps1" @{}
    Add-Phase "tenant-multi-e2e" ($ec -eq 0) "exit=$ec"
    $ec = Run-Script "run-ip-demo-e2e.ps1" @{}
    Add-Phase "ip-demo-e2e" ($ec -eq 0) "exit=$ec"
} else {
    Add-Phase "boot-enterprise" $false "admin not ready"
}

# --- security-e2e ---
$ok = Boot-Stack "local,security-e2e" "local,security-e2e"
if ($ok) {
    $ec = Run-Script "run-security-token-e2e.ps1" @{}
    Add-Phase "security-token-e2e" ($ec -eq 0) "exit=$ec"
} else {
    Add-Phase "boot-security" $false "admin/demo not ready"
}

# --- playground-disabled (Admin only) ---
$ok = Boot-Stack "local,playground-disabled-e2e" ""
if ($ok) {
    $ec = Run-Script "run-playground-disabled-e2e.ps1" @{}
    Add-Phase "playground-disabled-e2e" ($ec -eq 0) "exit=$ec"
} else {
    Add-Phase "boot-playground-disabled" $false "admin not ready"
}

# --- perf (restore local stack) ---
$ok = Boot-Stack "local" "local"
if ($ok) {
    Write-Host "Cooling down 45s before perf gate (avoid post-E2E CPU contention) ..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 45
    $ec = Run-Script "run-perf-gate.ps1" @{ JavaHome = $JavaHome }
    Add-Phase "perf-gate-phase2c" ($ec -eq 0) "exit=$ec"
    $ec = Run-Script "run-full-perf.ps1" @{ JavaHome = $JavaHome }
    Add-Phase "full-perf" ($ec -eq 0) "exit=$ec"
} else {
    Add-Phase "boot-perf" $false "admin/demo not ready"
}

Stop-Services

$report = @{
    timestamp = (Get-Date).ToString("o")
    exitCode = $script:exitCode
    phases = $phases
}
Set-Content -Path $ReportJson -Value ($report | ConvertTo-Json -Depth 6) -Encoding UTF8
Write-Host "Report: $ReportJson" -ForegroundColor Cyan
Write-Host "========== Exit $($script:exitCode) ==========" -ForegroundColor $(if ($script:exitCode -eq 0) { 'Green' } else { 'Red' })
exit $script:exitCode
