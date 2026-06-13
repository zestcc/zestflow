# ZestFlow 生产级验收门禁 — 白盒 + 黑盒 + 主链路 + 压力（四层）
param(
    [string]$JavaHome = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "D:\IT\JDK17\jdk-17.0.19+10" }),
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$PolicyFile = (Join-Path $PSScriptRoot "production-acceptance-policy.json"),
    [switch]$SkipMavenTest,
    [switch]$SkipRuntimeBlackbox,
    [switch]$SkipLink,
    [switch]$SkipStress,
    [switch]$RequirePerf,
    [switch]$RequireEnterpriseProfile,
    [switch]$RequireSecurityProfile,
    [switch]$AllowSkipRuntime,
    [switch]$IncludeOfflineChecks,
    [switch]$RequireProdProfile,
    [int]$SceneTimeoutSec = 300
)

$ErrorActionPreference = "Continue"
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("production-acceptance-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$phases = New-Object System.Collections.Generic.List[object]
$script:exitCode = 0

function Add-Phase($layer, $name, $ok, $note) {
    $script:phases.Add([pscustomobject]@{ layer=$layer; phase=$name; ok=$ok; note=$note }) | Out-Null
    if (-not $ok) { $script:exitCode = 1 }
    $color = if ($ok) { 'Green' } else { 'Red' }
    Write-Host ("[{0}] [{1}] {2} — {3}" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $layer, $name, $note) -ForegroundColor $color
}

function Write-AcceptanceGateReport {
    $report = @{
        timestamp = (Get-Date).ToString("o")
        exitCode = $script:exitCode
        policyFile = $PolicyFile
        layers = @{
            whitebox = -not $SkipMavenTest
            blackbox = -not $SkipRuntimeBlackbox
            link = -not $SkipLink
            stress = -not $SkipStress
            perf = [bool]$RequirePerf
        }
        phases = $phases
        hint = @{
            prereq = "Admin :8080 + demo Netty :20550 + Collector :20650"
            quick = ".\scripts\blackbox\run-production-acceptance.ps1 -AllowSkipRuntime"
            strict = ".\scripts\blackbox\run-production-acceptance.ps1 -RequirePerf"
        }
    }
    Set-Content -Path $ReportJson -Value ($report | ConvertTo-Json -Depth 8) -Encoding UTF8
    Write-Host "Production acceptance report: $ReportJson" -ForegroundColor Cyan
    Write-Host "========== Exit $($script:exitCode) ==========" -ForegroundColor $(if ($script:exitCode -eq 0) { 'Green' } else { 'Red' })
    exit $script:exitCode
}

Write-Host "========== ZestFlow Production Acceptance ==========" -ForegroundColor Cyan

# --- Layer A: Whitebox (mvn test) ---
if (-not $SkipMavenTest) {
    if (-not (Test-Path $JavaHome)) {
        Add-Phase "whitebox" "java-home" $false "invalid JAVA_HOME=$JavaHome"
        Write-AcceptanceGateReport
    }
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;" + $env:Path
    Push-Location $Root
    Write-Host "mvn -B test (full repository) ..." -ForegroundColor DarkGray
    & mvn -B test 2>&1 | Out-Host
    Add-Phase "whitebox" "mvn-test-full" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"
    Pop-Location
    if ($LASTEXITCODE -ne 0) { Write-AcceptanceGateReport }
} else {
    Add-Phase "whitebox" "mvn-test-full" $true "skipped"
}

if ($SkipRuntimeBlackbox -and $SkipLink -and $SkipStress -and -not $RequirePerf) {
    Write-AcceptanceGateReport
}

$adminUp = $false
try {
    $ping = Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/auth/login" -Method POST `
        -Body '{"username":"admin","password":"admin123"}' -ContentType "application/json" `
        -UseBasicParsing -TimeoutSec 5
    $adminUp = ($ping.StatusCode -eq 200)
} catch {}

if (-not $adminUp) {
    $runtimeOk = [bool]$AllowSkipRuntime
    Add-Phase "blackbox" "admin-reachable" $runtimeOk $(if ($runtimeOk) { "skipped-allow" } else { "Admin not reachable" })
    if (-not $runtimeOk) { Write-AcceptanceGateReport }
}

# --- Layer B: Blackbox (enterprise gate runtime subset) ---
if (-not $SkipRuntimeBlackbox) {
    & "$PSScriptRoot\run-full-e2e.ps1" -E2eProfile fullGreen -SceneTimeoutSec $SceneTimeoutSec
    Add-Phase "blackbox" "full-e2e-fullGreen" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"

    & "$PSScriptRoot\run-rbac-horizontal-e2e.ps1"
    Add-Phase "blackbox" "rbac-horizontal-e2e" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"

    & "$PSScriptRoot\run-chain-lifecycle-e2e.ps1"
    Add-Phase "blackbox" "chain-lifecycle-e2e" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"

    & "$PSScriptRoot\run-schedule-trigger-e2e.ps1" -AllowSkip:$AllowSkipRuntime
    switch ($LASTEXITCODE) {
        0 { Add-Phase "blackbox" "schedule-trigger-e2e" $true "passed" }
        2 { Add-Phase "blackbox" "schedule-trigger-e2e" [bool]$AllowSkipRuntime $(if ($AllowSkipRuntime) { "skipped" } else { "required-fail" }) }
        default { Add-Phase "blackbox" "schedule-trigger-e2e" $false "exit=$LASTEXITCODE" }
    }

    & "$PSScriptRoot\run-sso-e2e.ps1" -AllowSkip:$AllowSkipRuntime
    switch ($LASTEXITCODE) {
        0 { Add-Phase "blackbox" "sso-e2e" $true "passed" }
        2 { Add-Phase "blackbox" "sso-e2e" [bool]$AllowSkipRuntime $(if ($AllowSkipRuntime) { "skipped" } else { "required-fail" }) }
        default { Add-Phase "blackbox" "sso-e2e" $false "exit=$LASTEXITCODE" }
    }

    & "$PSScriptRoot\run-log-live-stream-e2e.ps1" -AllowSkip:$AllowSkipRuntime
    switch ($LASTEXITCODE) {
        0 { Add-Phase "blackbox" "log-live-stream-e2e" $true "passed" }
        2 { Add-Phase "blackbox" "log-live-stream-e2e" [bool]$AllowSkipRuntime $(if ($AllowSkipRuntime) { "skipped" } else { "required-fail" }) }
        default { Add-Phase "blackbox" "log-live-stream-e2e" $false "exit=$LASTEXITCODE" }
    }

    & "$PSScriptRoot\run-executor-read-cache-e2e.ps1" -AllowSkip:$AllowSkipRuntime
    switch ($LASTEXITCODE) {
        0 { Add-Phase "blackbox" "executor-read-cache-e2e" $true "passed" }
        2 { Add-Phase "blackbox" "executor-read-cache-e2e" [bool]$AllowSkipRuntime $(if ($AllowSkipRuntime) { "skipped" } else { "required-fail" }) }
        default { Add-Phase "blackbox" "executor-read-cache-e2e" $false "exit=$LASTEXITCODE" }
    }

    $allowEntSkip = -not $RequireEnterpriseProfile
    & "$PSScriptRoot\run-tenant-multi-e2e.ps1" -AllowSkip:$allowEntSkip
    switch ($LASTEXITCODE) {
        0 { Add-Phase "blackbox" "tenant-multi-e2e" $true "passed" }
        2 { Add-Phase "blackbox" "tenant-multi-e2e" $(-not $RequireEnterpriseProfile) $(if ($RequireEnterpriseProfile) { "required-skip" } else { "skipped-profile" }) }
        default { Add-Phase "blackbox" "tenant-multi-e2e" $false "exit=$LASTEXITCODE" }
    }

    $allowSecSkip = -not $RequireSecurityProfile
    & "$PSScriptRoot\run-security-token-e2e.ps1" -AllowSkip:$allowSecSkip
    switch ($LASTEXITCODE) {
        0 { Add-Phase "blackbox" "security-token-e2e" $true "passed" }
        2 { Add-Phase "blackbox" "security-token-e2e" $(-not $RequireSecurityProfile) $(if ($RequireSecurityProfile) { "required-skip" } else { "skipped-profile" }) }
        default { Add-Phase "blackbox" "security-token-e2e" $false "exit=$LASTEXITCODE" }
    }

    if ($RequireProdProfile) {
        & "$PSScriptRoot\run-production-profile-checklist.ps1" -AllowSkip:$AllowSkipRuntime -RequireProdProfile
    } else {
        & "$PSScriptRoot\run-production-profile-checklist.ps1" -AllowSkip:$AllowSkipRuntime
    }
    switch ($LASTEXITCODE) {
        0 { Add-Phase "blackbox" "production-profile-checklist" $true "passed" }
        2 { Add-Phase "blackbox" "production-profile-checklist" [bool]$AllowSkipRuntime $(if ($AllowSkipRuntime) { "skipped" } else { "required-fail" }) }
        default { Add-Phase "blackbox" "production-profile-checklist" $false "exit=$LASTEXITCODE" }
    }

    & "$PSScriptRoot\run-playwright-e2e.ps1" -BaseAdmin $BaseAdmin -AllowSkip:$AllowSkipRuntime
    switch ($LASTEXITCODE) {
        0 { Add-Phase "blackbox" "playwright-e2e" $true "passed" }
        2 { Add-Phase "blackbox" "playwright-e2e" [bool]$AllowSkipRuntime $(if ($AllowSkipRuntime) { "skipped" } else { "required-fail" }) }
        default { Add-Phase "blackbox" "playwright-e2e" $false "exit=$LASTEXITCODE" }
    }
} else {
    Add-Phase "blackbox" "runtime-suite" $true "skipped"
}

# --- Layer C: Platform link ---
if (-not $SkipLink) {
    & "$PSScriptRoot\run-platform-link-e2e.ps1" -BaseAdmin $BaseAdmin -AllowSkip:$AllowSkipRuntime
    switch ($LASTEXITCODE) {
        0 { Add-Phase "link" "platform-link-e2e" $true "passed" }
        2 { Add-Phase "link" "platform-link-e2e" [bool]$AllowSkipRuntime $(if ($AllowSkipRuntime) { "skipped" } else { "required-fail" }) }
        default { Add-Phase "link" "platform-link-e2e" $false "exit=$LASTEXITCODE" }
    }
} else {
    Add-Phase "link" "platform-link-e2e" $true "skipped"
}

# --- Layer D: Stress ---
if (-not $SkipStress) {
    & "$PSScriptRoot\run-log-stream-stress-e2e.ps1" -BaseAdmin $BaseAdmin -AllowSkip:$AllowSkipRuntime
    switch ($LASTEXITCODE) {
        0 { Add-Phase "stress" "log-stream-stress-e2e" $true "passed" }
        2 { Add-Phase "stress" "log-stream-stress-e2e" [bool]$AllowSkipRuntime $(if ($AllowSkipRuntime) { "skipped" } else { "required-fail" }) }
        default { Add-Phase "stress" "log-stream-stress-e2e" $false "exit=$LASTEXITCODE" }
    }
} else {
    Add-Phase "stress" "log-stream-stress-e2e" $true "skipped"
}

if ($RequirePerf) {
    & "$PSScriptRoot\run-perf-gate.ps1" -JavaHome $JavaHome -SkipRuntimeBlackbox
    Add-Phase "stress" "perf-gate-phase2c" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"
} else {
    Add-Phase "stress" "perf-gate-phase2c" $true "skipped-use-RequirePerf"
}

if ($IncludeOfflineChecks) {
    function Stop-ListenPortForOffline([int]$Port) {
        Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique |
            ForEach-Object { if ($_ -and $_ -ne 0) { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue } }
    }
    Write-Host "Stopping demo-app (8081/20550) for offline checks ..." -ForegroundColor DarkGray
    . "$PSScriptRoot\_acceptance-common.ps1"
    $warmToken = Login-AdminToken $BaseAdmin
    if ($warmToken) {
        $warmH = @{ Authorization = "Bearer $warmToken" }
        Invoke-AcceptanceApi GET "$BaseAdmin/api/zestflow/chains?appCode=demo-app&page=1&size=5" $null $warmH | Out-Null
    }
    Stop-ListenPortForOffline 8081
    Stop-ListenPortForOffline 20550
    $offlineDeadline = (Get-Date).AddSeconds(120)
    $staleReady = $false
    while ((Get-Date) -lt $offlineDeadline) {
        try {
            $login = Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/auth/login" -Method POST `
                -Body '{"username":"admin","password":"admin123"}' -ContentType "application/json" `
                -UseBasicParsing -TimeoutSec 5
            $tok = (ConvertFrom-Json $login.Content).data.token
            if ($tok) {
                $hr = @{ Authorization = "Bearer $tok" }
                $probe = Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/chains?appCode=demo-app&page=1&size=5" `
                    -Headers $hr -UseBasicParsing -TimeoutSec 10
                if ($probe.Content -match '"stale"\s*:\s*true') { $staleReady = $true; break }
            }
        } catch {}
        Start-Sleep -Seconds 5
    }
    if (-not $staleReady) { Write-Host "Offline stale cache not observed within 120s (will still run checks)" -ForegroundColor Yellow }

    & "$PSScriptRoot\run-executor-read-cache-e2e.ps1" -RequireStaleCache
    switch ($LASTEXITCODE) {
        0 { Add-Phase "link" "read-cache-stale-e2e" $true "passed" }
        2 { Add-Phase "link" "read-cache-stale-e2e" [bool]$AllowSkipRuntime "skipped" }
        default { Add-Phase "link" "read-cache-stale-e2e" $false "exit=$LASTEXITCODE" }
    }
    & "$PSScriptRoot\run-executor-offline-write-e2e.ps1" -RequireOffline
    switch ($LASTEXITCODE) {
        0 { Add-Phase "link" "offline-write-e2e" $true "passed" }
        2 { Add-Phase "link" "offline-write-e2e" [bool]$AllowSkipRuntime "skipped" }
        default { Add-Phase "link" "offline-write-e2e" $false "exit=$LASTEXITCODE" }
    }
} else {
    Add-Phase "link" "offline-checks" $true "skipped-use-IncludeOfflineChecks"
}

Write-AcceptanceGateReport
