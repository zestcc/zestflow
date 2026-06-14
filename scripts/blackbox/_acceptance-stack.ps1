# StrictV1 / E2E 栈启停 — 固定 8080/20550/20650，覆盖本地 application-local.yml 的 8082
$script:AcceptanceAdminProfiles = "local,strictv1-e2e"
$script:AcceptanceDemoProfiles = "local,strictv1-e2e"
$script:acceptanceAdminJob = $null
$script:acceptanceDemoJob = $null

function Stop-AcceptanceListenPort([int]$Port) {
    Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique |
        ForEach-Object { if ($_ -and $_ -ne 0) { Stop-Process -Id $_ -Force -ErrorAction SilentlyContinue } }
}

function Stop-AcceptanceStack {
    Write-Host "Stopping acceptance stack on 8080/8081/20550/20650 ..." -ForegroundColor DarkGray
    foreach ($p in @(8081, 20550, 20650, 8080)) { Stop-AcceptanceListenPort $p }
    if ($script:acceptanceAdminJob) {
        Stop-Job $script:acceptanceAdminJob -ErrorAction SilentlyContinue
        Remove-Job $script:acceptanceAdminJob -Force -ErrorAction SilentlyContinue
        $script:acceptanceAdminJob = $null
    }
    if ($script:acceptanceDemoJob) {
        Stop-Job $script:acceptanceDemoJob -ErrorAction SilentlyContinue
        Remove-Job $script:acceptanceDemoJob -Force -ErrorAction SilentlyContinue
        $script:acceptanceDemoJob = $null
    }
    Start-Sleep -Seconds 4
}

function Wait-AcceptanceAdmin([string]$BaseAdmin = "http://127.0.0.1:8080", [int]$TimeoutSec = 240) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/auth/login" -Method POST `
                -Body '{"username":"admin","password":"admin123"}' -ContentType "application/json" `
                -UseBasicParsing -TimeoutSec 5
            if ($r.StatusCode -eq 200) { return $true }
        } catch {}
        Start-Sleep -Seconds 2
    }
    return $false
}

function Wait-AcceptanceNetty([int]$TimeoutSec = 240) {
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

function Get-AcceptanceAdminToken([string]$BaseAdmin = "http://127.0.0.1:8080") {
    try {
        $r = Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/auth/login" -Method POST `
            -Body '{"username":"admin","password":"admin123"}' -ContentType "application/json" `
            -UseBasicParsing -TimeoutSec 5
        return (ConvertFrom-Json $r.Content).data.token
    } catch {
        return $null
    }
}

function Wait-AcceptanceExecutorOnline(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$AppCode = "demo-app",
    [int]$TimeoutSec = 180
) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $token = Get-AcceptanceAdminToken $BaseAdmin
        if (-not $token) { Start-Sleep -Seconds 2; continue }
        try {
            $h = @{ Authorization = "Bearer $token" }
            $r = Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/executors/apps?online=true" `
                -Headers $h -UseBasicParsing -TimeoutSec 5
            $apps = (ConvertFrom-Json $r.Content).data
            if ($apps | Where-Object { $_.appCode -eq $AppCode -or $_.appName -eq $AppCode }) {
                return $true
            }
            $r2 = Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/executors" -Headers $h -UseBasicParsing -TimeoutSec 5
            $list = (ConvertFrom-Json $r2.Content).data
            if ($list | Where-Object { $_.status -eq 1 -and ($_.appCode -eq $AppCode -or $_.appName -eq $AppCode) }) {
                return $true
            }
        } catch {}
        Start-Sleep -Seconds 2
    }
    return $false
}

function Wait-AcceptancePlaygroundWarmup(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$WarmScene = "SCN20260531050001",
    [int]$TimeoutSec = 120
) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $token = Get-AcceptanceAdminToken $BaseAdmin
        if (-not $token) { Start-Sleep -Seconds 2; continue }
        try {
            $h = @{ Authorization = "Bearer $token" }
            $r = Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/playground/execute/$WarmScene" -Method POST `
                -Body '{}' -ContentType "application/json" -Headers $h -UseBasicParsing -TimeoutSec 60
            $resp = ConvertFrom-Json $r.Content
            if ($resp.code -eq 200) {
                $st = $resp.data.status
                if ($null -eq $st -or $st -eq 1 -or $st -eq '1' -or $st -eq 'SUCCESS') { return $true }
            }
        } catch {}
        Start-Sleep -Seconds 3
    }
    return $false
}

function Start-AcceptanceAdminJob([string]$Root, [string]$JavaHome, [string]$Profiles) {
    $script:acceptanceAdminJob = Start-Job -Name "zestflow-admin-acceptance" -ScriptBlock {
        param($Root, $JavaHome, $Profiles)
        $env:JAVA_HOME = $JavaHome
        $env:Path = "$JavaHome\bin;" + $env:Path
        Set-Location $Root
        & mvn -q spring-boot:run -pl zestflow-admin -DskipTests "-Dspring-boot.run.profiles=$Profiles"
    } -ArgumentList $Root, $JavaHome, $Profiles
}

function Start-AcceptanceDemoJob([string]$Root, [string]$JavaHome, [string]$Profiles) {
    $script:acceptanceDemoJob = Start-Job -Name "zestflow-demo-acceptance" -ScriptBlock {
        param($Root, $JavaHome, $Profiles)
        $env:JAVA_HOME = $JavaHome
        $env:Path = "$JavaHome\bin;" + $env:Path
        Set-Location $Root
        & mvn -q spring-boot:run -pl zestflow-demo -DskipTests "-Dspring-boot.run.profiles=$Profiles"
    } -ArgumentList $Root, $JavaHome, $Profiles
}

function Ensure-AcceptanceArtifacts([string]$Root, [string]$JavaHome) {
    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;" + $env:Path
    Push-Location $Root
    Write-Host "mvn install -pl zestflow-admin,zestflow-demo -am (acceptance prep) ..." -ForegroundColor DarkGray
    & mvn -q install -DskipTests -pl zestflow-admin,zestflow-demo -am 2>&1 | Out-Null
    $ok = ($LASTEXITCODE -eq 0)
    Pop-Location
    return $ok
}

function Boot-AcceptanceStack {
    param(
        [string]$Root,
        [string]$JavaHome,
        [string]$AdminProfiles = $script:AcceptanceAdminProfiles,
        [string]$DemoProfiles = $script:AcceptanceDemoProfiles
    )
    if (-not (Ensure-AcceptanceArtifacts $Root $JavaHome)) { return $false }
    Stop-AcceptanceStack
    Write-Host "Boot acceptance stack Admin=$AdminProfiles Demo=$DemoProfiles" -ForegroundColor Cyan
    Start-AcceptanceAdminJob $Root $JavaHome $AdminProfiles
    if ($DemoProfiles) {
        Start-AcceptanceDemoJob $Root $JavaHome $DemoProfiles
        if (-not (Wait-AcceptanceAdmin)) { return $false }
        if (-not (Wait-AcceptanceNetty)) { return $false }
        if (-not (Wait-AcceptanceExecutorOnline)) {
            Write-Host "Acceptance stack: demo-app executor not online" -ForegroundColor Red
            return $false
        }
        if (-not (Wait-AcceptancePlaygroundWarmup)) {
            Write-Host "Acceptance stack: playground warmup not green" -ForegroundColor Red
            return $false
        }
    } else {
        if (-not (Wait-AcceptanceAdmin)) { return $false }
    }
    Start-Sleep -Seconds 3
    return $true
}

function Wait-AcceptanceSecurityReady([int]$TimeoutSec = 240) {
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

function Boot-AcceptanceProfileStack {
    param(
        [string]$Root,
        [string]$JavaHome,
        [string]$AdminProfiles,
        [string]$DemoProfiles
    )
    if (-not (Boot-AcceptanceStack $Root $JavaHome $AdminProfiles $DemoProfiles)) {
        return $false
    }
    if ($AdminProfiles -like '*security-e2e*') {
        return Wait-AcceptanceSecurityReady
    }
    return $true
}
