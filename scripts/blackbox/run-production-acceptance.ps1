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
    [switch]$StrictV1,
    [int]$SceneTimeoutSec = 300
)

if ($StrictV1) {
    $RequirePerf = $true
    $IncludeOfflineChecks = $true
}

$ErrorActionPreference = "Continue"
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
. "$PSScriptRoot\_acceptance-stack.ps1"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("production-acceptance-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$phases = New-Object System.Collections.Generic.List[object]
$script:exitCode = 0
$script:acceptanceStackBooted = $false

function Add-Phase($layer, $name, $ok, $note) {
    $script:phases.Add([pscustomobject]@{ layer=$layer; phase=$name; ok=$ok; note=$note }) | Out-Null
    if (-not $ok) { $script:exitCode = 1 }
    $color = if ($ok) { 'Green' } else { 'Red' }
    Write-Host ("[{0}] [{1}] {2} — {3}" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $layer, $name, $note) -ForegroundColor $color
}

function Write-AcceptanceGateReport {
    if ($script:acceptanceStackBooted) {
        Stop-AcceptanceStack
        $script:acceptanceStackBooted = $false
    }
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
            strictV1 = [bool]$StrictV1
            offline = [bool]$IncludeOfflineChecks
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
$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;" + $env:Path

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

if (-not $adminUp -and $StrictV1) {
    Write-Host "StrictV1: booting acceptance stack on :8080 ..." -ForegroundColor Cyan
    Stop-AcceptanceStack
    Start-Sleep -Seconds 8
    for ($bootTry = 1; $bootTry -le 3; $bootTry++) {
        if ($bootTry -gt 1) {
            Write-Host "StrictV1 boot retry $bootTry/3 ..." -ForegroundColor Yellow
            Stop-AcceptanceStack
            Start-Sleep -Seconds 20
        }
        $script:acceptanceStackBooted = Boot-AcceptanceStack $Root $JavaHome
        if ($script:acceptanceStackBooted) {
            try {
                $ping = Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/auth/login" -Method POST `
                    -Body '{"username":"admin","password":"admin123"}' -ContentType "application/json" `
                    -UseBasicParsing -TimeoutSec 5
                $adminUp = ($ping.StatusCode -eq 200)
            } catch {}
            if ($adminUp) { break }
            $script:acceptanceStackBooted = $false
        }
    }
    Add-Phase "blackbox" "strictv1-boot-stack" ($script:acceptanceStackBooted -and $adminUp) $(if ($adminUp) { "admin:8080 ready" } else { "boot failed" })
}

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

    & "$PSScriptRoot\run-log-live-stream-ws-e2e.ps1" -AllowSkip:$AllowSkipRuntime
    switch ($LASTEXITCODE) {
        0 { Add-Phase "blackbox" "log-live-stream-ws-e2e" $true "passed" }
        2 { Add-Phase "blackbox" "log-live-stream-ws-e2e" [bool]$AllowSkipRuntime $(if ($AllowSkipRuntime) { "skipped" } else { "required-fail" }) }
        default { Add-Phase "blackbox" "log-live-stream-ws-e2e" $false "exit=$LASTEXITCODE" }
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
    $null = & "$PSScriptRoot\run-perf-gate.ps1" -JavaHome $JavaHome -SkipRuntimeBlackbox
    $perfExit = [int]$LASTEXITCODE
    Add-Phase "stress" "perf-gate-phase2c" ($perfExit -eq 0) "exit=$perfExit"
} else {
    Add-Phase "stress" "perf-gate-phase2c" $true "skipped-use-RequirePerf"
}

if ($IncludeOfflineChecks) {
    Write-Host "Preparing offline checks (warm read cache) ..." -ForegroundColor DarkGray
    . "$PSScriptRoot\_acceptance-common.ps1"
    if (-not (Wait-AcceptanceAdmin $BaseAdmin 120)) {
        Write-Host "Admin slow after long runtime; cool-down 20s ..." -ForegroundColor Yellow
        Start-Sleep -Seconds 20
        $null = Wait-AcceptanceAdmin $BaseAdmin 60
    }
    $warmToken = Ensure-AdminLoginToken $BaseAdmin 20 3
    $cacheWarmed = $false
    if ($warmToken) {
        $warmH = @{ Authorization = "Bearer $warmToken" }
        for ($w = 1; $w -le 3; $w++) {
            $cacheWarmed = Warm-AcceptanceReadCache $BaseAdmin $warmH "demo-app"
            if ($cacheWarmed) { break }
            Write-Host "Read-cache warm attempt $w/3 incomplete, retry ..." -ForegroundColor Yellow
            Start-Sleep -Seconds 5
        }
        if (-not $cacheWarmed) {
            Write-Host "Read-cache warm incomplete before offline checks (continuing)" -ForegroundColor Yellow
        }
    } else {
        Write-Host "Admin login failed before offline checks after retries" -ForegroundColor Yellow
    }
    Write-Host "Stopping demo-app for offline checks ..." -ForegroundColor DarkGray
    Stop-AcceptanceDemoForOffline
    if ($warmToken) {
        $dereg = Remove-AcceptanceAppExecutors $BaseAdmin $warmH "demo-app"
        Write-Host "Deregistered $dereg demo-app executor(s) for offline snapshot probe" -ForegroundColor DarkGray
    }
    $offlineDeadline = (Get-Date).AddSeconds(240)
    $staleReady = $false
    while ((Get-Date) -lt $offlineDeadline) {
        $probeTok = Ensure-AdminLoginToken $BaseAdmin 3 2
        if ($probeTok) {
            try {
                $hr = @{ Authorization = "Bearer $probeTok" }
                $probe = Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/chains?appCode=demo-app&page=1&size=5" `
                    -Headers $hr -UseBasicParsing -TimeoutSec 15
                if ($probe.Content -match '"stale"\s*:\s*true') { $staleReady = $true; break }
            } catch {}
        }
        Start-Sleep -Seconds 5
    }
    if (-not $staleReady) { Write-Host "Offline stale cache not observed within 240s (will still run checks)" -ForegroundColor Yellow }

    function Invoke-ReadCacheStaleE2e {
        & "$PSScriptRoot\run-executor-read-cache-e2e.ps1" -RequireStaleCache
        return [int]$LASTEXITCODE
    }
    $staleExit = Invoke-ReadCacheStaleE2e
    if ($staleExit -ne 0 -and -not $staleReady) {
        Write-Host "read-cache-stale failed, retry after 30s stale probe ..." -ForegroundColor Yellow
        Start-Sleep -Seconds 30
        $staleExit = Invoke-ReadCacheStaleE2e
    }
    switch ($staleExit) {
        0 { Add-Phase "link" "read-cache-stale-e2e" $true "passed" }
        2 { Add-Phase "link" "read-cache-stale-e2e" [bool]$AllowSkipRuntime "skipped" }
        default { Add-Phase "link" "read-cache-stale-e2e" $false "exit=$staleExit" }
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
