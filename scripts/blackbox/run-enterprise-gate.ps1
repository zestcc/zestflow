# ZestFlow 开源发布前企业级质量门禁（单元测试 + 黑盒 + 多租�?IP 可选层�?
param(
    [string]$JavaHome = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "D:\IT\JDK17\jdk-17.0.19+10" }),
    [switch]$SkipMavenTest,
    [switch]$SkipRuntimeE2e,
    [switch]$RequireEnterpriseProfile,
    [switch]$RequireSecurityProfile,
    [switch]$RequirePlaygroundDisabledProfile,
    [switch]$RequirePerfProfile,
    [int]$SceneTimeoutSec = 300
)

$ErrorActionPreference = "Continue"
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$GateJson = Join-Path $OutDir ("enterprise-gate-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$phases = New-Object System.Collections.Generic.List[object]
$script:exitCode = 0

function Add-Phase($name, $ok, $note) {
    $script:phases.Add([pscustomobject]@{ phase=$name; ok=$ok; note=$note }) | Out-Null
    if (-not $ok) { $script:exitCode = 1 }
    $color = if ($ok) { 'Green' } else { 'Red' }
    Write-Host ("[{0}] {1} �?{2}" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $name, $note) -ForegroundColor $color
}

function Write-GateReport {
    $report = @{
        timestamp = (Get-Date).ToString("o")
        exitCode = $script:exitCode
        phases = $phases
        hint = @{
            fullGreen = ".\scripts\blackbox\run-full-e2e.ps1 -E2eProfile fullGreen"
            enterprise = "Admin: -Dspring-boot.run.profiles=local,enterprise-e2e then gate with -RequireEnterpriseProfile"
            security = "Admin+Executor+Collector: profiles=local,security-e2e then gate with -RequireSecurityProfile"
            playgroundDisabled = "Admin: profiles=local,playground-disabled-e2e then gate with -RequirePlaygroundDisabledProfile"
            perf = ".\scripts\blackbox\run-perf-gate.ps1 or run-enterprise-gate.ps1 -RequirePerfProfile"
        }
    }
    Set-Content -Path $GateJson -Value ($report | ConvertTo-Json -Depth 6) -Encoding UTF8
    Write-Host "Gate report: $GateJson" -ForegroundColor Cyan
    Write-Host "========== Exit $($script:exitCode) ==========" -ForegroundColor $(if ($script:exitCode -eq 0) { 'Green' } else { 'Red' })
    exit $script:exitCode
}

Write-Host "========== ZestFlow Enterprise Gate ==========" -ForegroundColor Cyan
Write-Host "Root: $Root"

if (-not $SkipMavenTest) {
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;" + $env:Path
    Push-Location $Root
    $modules = @("zestflow-common", "zestflow-executor", "zestflow-demo", "zestflow-collector/collector-jdbc", "zestflow-admin")
    foreach ($m in $modules) {
        Write-Host "mvn test -pl $m -am ..." -ForegroundColor DarkGray
        & mvn -q test -pl $m -am 2>&1 | Out-Host
        if ($LASTEXITCODE -ne 0) {
            Add-Phase "mvn-test-$($m -replace '/','-')" $false "exit=$LASTEXITCODE"
            Pop-Location
            Write-GateReport
        }
        Add-Phase "mvn-test-$($m -replace '/','-')" $true "ok"
    }
    Pop-Location
} else {
    Add-Phase "mvn-test" $true "skipped"
}

if ($SkipRuntimeE2e) {
    Add-Phase "runtime-e2e" $true "skipped"
    Add-Phase "tenant-multi-e2e" $true "skipped"
    Add-Phase "ip-demo-e2e" $true "skipped"
    Add-Phase "security-token-e2e" $true "skipped"
    Add-Phase "playground-disabled-e2e" $true "skipped"
    Add-Phase "rbac-horizontal-e2e" $true "skipped"
    Add-Phase "perf-gate-phase2c" $true "skipped"
    Write-GateReport
}

$adminUp = $false
try {
    $ping = Invoke-WebRequest -Uri "http://127.0.0.1:8080/api/zestflow/auth/login" -Method POST `
        -Body '{"username":"admin","password":"admin123"}' -ContentType "application/json" `
        -UseBasicParsing -TimeoutSec 5
    $adminUp = ($ping.StatusCode -eq 200)
} catch {}

if (-not $adminUp) {
    Add-Phase "runtime-e2e" $false "Admin :8080 not reachable"
    Add-Phase "tenant-multi-e2e" $false "skipped-no-admin"
    Add-Phase "ip-demo-e2e" $false "skipped-no-admin"
    Add-Phase "security-token-e2e" $false "skipped-no-admin"
    Add-Phase "playground-disabled-e2e" $false "skipped-no-admin"
    Add-Phase "rbac-horizontal-e2e" $false "skipped-no-admin"
    Add-Phase "perf-gate-phase2c" $false "skipped-no-admin"
    Write-GateReport
}

& "$PSScriptRoot\run-full-e2e.ps1" -E2eProfile fullGreen -SceneTimeoutSec $SceneTimeoutSec
Add-Phase "full-e2e-fullGreen" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"

$allowSkip = -not $RequireEnterpriseProfile
& "$PSScriptRoot\run-tenant-multi-e2e.ps1" -AllowSkip:$allowSkip
switch ($LASTEXITCODE) {
    0 { Add-Phase "tenant-multi-e2e" $true "passed" }
    2 { Add-Phase "tenant-multi-e2e" $(-not $RequireEnterpriseProfile) $(if ($RequireEnterpriseProfile) { "required-but-skipped" } else { "skipped-not-multi-profile" }) }
    default { Add-Phase "tenant-multi-e2e" $false "exit=$LASTEXITCODE" }
}

& "$PSScriptRoot\run-ip-demo-e2e.ps1" -AllowSkip:$allowSkip
switch ($LASTEXITCODE) {
    0 { Add-Phase "ip-demo-e2e" $true "passed" }
    2 { Add-Phase "ip-demo-e2e" $(-not $RequireEnterpriseProfile) $(if ($RequireEnterpriseProfile) { "required-but-skipped" } else { "skipped-not-enterprise-profile" }) }
    default { Add-Phase "ip-demo-e2e" $false "exit=$LASTEXITCODE" }
}

$allowSecuritySkip = -not $RequireSecurityProfile
& "$PSScriptRoot\run-security-token-e2e.ps1" -AllowSkip:$allowSecuritySkip
switch ($LASTEXITCODE) {
    0 { Add-Phase "security-token-e2e" $true "passed" }
    2 { Add-Phase "security-token-e2e" $(-not $RequireSecurityProfile) $(if ($RequireSecurityProfile) { "required-but-skipped" } else { "skipped-not-security-profile" }) }
    default { Add-Phase "security-token-e2e" $false "exit=$LASTEXITCODE" }
}

& "$PSScriptRoot\run-rbac-horizontal-e2e.ps1"
Add-Phase "rbac-horizontal-e2e" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"

$allowPlaygroundSkip = -not $RequirePlaygroundDisabledProfile
& "$PSScriptRoot\run-playground-disabled-e2e.ps1" -AllowSkip:$allowPlaygroundSkip
switch ($LASTEXITCODE) {
    0 { Add-Phase "playground-disabled-e2e" $true "passed" }
    2 { Add-Phase "playground-disabled-e2e" $(-not $RequirePlaygroundDisabledProfile) $(if ($RequirePlaygroundDisabledProfile) { "required-but-skipped" } else { "skipped-not-playground-disabled-profile" }) }
    default { Add-Phase "playground-disabled-e2e" $false "exit=$LASTEXITCODE" }
}

if ($RequirePerfProfile) {
    & "$PSScriptRoot\run-perf-gate.ps1" -JavaHome $JavaHome -SkipRuntimeBlackbox
    Add-Phase "perf-gate-phase2c" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"
} else {
    Add-Phase "perf-gate-phase2c" $true "skipped-use-RequirePerfProfile"
}

Write-GateReport
