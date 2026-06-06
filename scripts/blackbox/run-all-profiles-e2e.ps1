# 全 profile E2E 编排：local → enterprise → security → playground-disabled → perf
param(
    [string]$JavaHome = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "D:\IT\JDK17\jdk-17.0.19+10" }),
    [switch]$SkipMavenTest,
    [int]$SceneTimeoutSec = 300
)

$ErrorActionPreference = "Continue"
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("all-profiles-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$phases = New-Object System.Collections.Generic.List[object]
$script:exitCode = 0
$script:adminJob = $null
$script:demoJob = $null

function Add-Phase($name, $ok, $note) {
    $script:phases.Add([pscustomobject]@{ phase = $name; ok = $ok; note = $note }) | Out-Null
    if (-not $ok) { $script:exitCode = 1 }
    $color = if ($ok) { 'Green' } else { 'Red' }
    Write-Host ("[{0}] {1} — {2}" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $name, $note) -ForegroundColor $color
}

function Stop-ListenPort([int]$Port) {
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique |
        ForEach-Object { if ($_ -and $_ -ne 0) { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue } }
}

function Stop-Services {
    Write-Host "Stopping services on 8080/8081/20550/20650 ..." -ForegroundColor DarkGray
    foreach ($p in @(8081, 20550, 20650, 8080)) { Stop-ListenPort $p }
    if ($script:adminJob) { Stop-Job $script:adminJob -ErrorAction SilentlyContinue; Remove-Job $script:adminJob -Force -ErrorAction SilentlyContinue; $script:adminJob = $null }
    if ($script:demoJob) { Stop-Job $script:demoJob -ErrorAction SilentlyContinue; Remove-Job $script:demoJob -Force -ErrorAction SilentlyContinue; $script:demoJob = $null }
    Start-Sleep -Seconds 4
}

function Wait-Admin([int]$TimeoutSec = 180) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:8080/api/zestflow/auth/login" -Method POST `
                -Body '{"username":"admin","password":"admin123"}' -ContentType "application/json" `
                -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -eq 200) { return $true }
        } catch {}
        Start-Sleep -Seconds 2
    }
    return $false
}

function Wait-Netty([int]$TimeoutSec = 180) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:20550/health" -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -eq 200) { return $true }
        } catch {}
        Start-Sleep -Seconds 2
    }
    return $false
}

function Start-Admin([string]$Profiles) {
    $script:adminJob = Start-Job -Name "zestflow-admin" -ScriptBlock {
        param($Root, $JavaHome, $Profiles)
        $env:JAVA_HOME = $JavaHome
        $env:Path = "$JavaHome\bin;" + $env:Path
        Set-Location $Root
        & mvn -q spring-boot:run -pl zestflow-admin -DskipTests "-Dspring-boot.run.profiles=$Profiles"
    } -ArgumentList $Root, $JavaHome, $Profiles
}

function Start-Demo([string]$Profiles) {
    $script:demoJob = Start-Job -Name "zestflow-demo" -ScriptBlock {
        param($Root, $JavaHome, $Profiles)
        $env:JAVA_HOME = $JavaHome
        $env:Path = "$JavaHome\bin;" + $env:Path
        Set-Location $Root
        & mvn -q spring-boot:run -pl zestflow-demo -DskipTests "-Dspring-boot.run.profiles=$Profiles"
    } -ArgumentList $Root, $JavaHome, $Profiles
}

function Wait-SecurityReady([int]$TimeoutSec = 240) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $login = Invoke-WebRequest -Uri "http://127.0.0.1:8080/api/zestflow/auth/login" -Method POST `
                -Body '{"username":"admin","password":"admin123"}' -ContentType "application/json" `
                -UseBasicParsing -TimeoutSec 5
            $token = (ConvertFrom-Json $login.Content).data.token
            if (-not $token) { Start-Sleep -Seconds 3; continue }
            $h = @{ Authorization = "Bearer $token" }
            $feat = Invoke-WebRequest -Uri "http://127.0.0.1:8080/api/zestflow/system/features" -Headers $h -UseBasicParsing -TimeoutSec 5
            $json = ConvertFrom-Json $feat.Content
            if ($json.security.registryTokenConfigured) { return $true }
        } catch {}
        Start-Sleep -Seconds 3
    }
    return $false
}

function Boot-Stack([string]$AdminProfiles, [string]$DemoProfiles) {
    Stop-Services
    Write-Host "Boot Admin profiles=$AdminProfiles Demo profiles=$DemoProfiles" -ForegroundColor Cyan
    Start-Admin $AdminProfiles
    if ($DemoProfiles) {
        Start-Demo $DemoProfiles
        if (-not (Wait-Admin -TimeoutSec 240)) { return $false }
        if (-not (Wait-Netty -TimeoutSec 240)) { return $false }
    } else {
        if (-not (Wait-Admin -TimeoutSec 240)) { return $false }
    }
    if ($AdminProfiles -like '*security-e2e*') {
        if (-not (Wait-SecurityReady)) {
            Write-Host "security-e2e profile not active on Admin" -ForegroundColor Red
            return $false
        }
    }
    Start-Sleep -Seconds 5
    return $true
}

function Run-Script($name, [hashtable]$NamedArgs = @{}) {
    $path = Join-Path $PSScriptRoot $name
    if ($NamedArgs.Count -gt 0) {
        & $path @NamedArgs
    } else {
        & $path
    }
    return $LASTEXITCODE
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
