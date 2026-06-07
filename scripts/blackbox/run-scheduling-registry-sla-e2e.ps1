# Scheduling / SLA / Registry black-box E2E
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$BaseCollector = "http://127.0.0.1:20650",
    [string]$RegistryToken = "",
    [string]$AdminToken = ""
)

$ErrorActionPreference = "Stop"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Ts = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportJson = Join-Path $OutDir "scheduling-registry-sla-e2e-$Ts.json"
$results = New-Object System.Collections.Generic.List[object]

function Add-R($id, $name, $ok, $ms, $note) {
    $results.Add([pscustomobject]@{ id=$id; name=$name; ok=$ok; latencyMs=$ms; note=$note }) | Out-Null
}

function Invoke-Timed($scriptBlock) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $r = & $scriptBlock
        $sw.Stop()
        return @{ ok=$true; ms=$sw.ElapsedMilliseconds; data=$r }
    } catch {
        $sw.Stop()
        return @{ ok=$false; ms=$sw.ElapsedMilliseconds; data=$_.Exception.Message }
    }
}

if (-not $AdminToken) {
    try {
        $loginResp = Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/auth/login" -Method POST `
            -Body '{"username":"admin","password":"admin123"}' -ContentType "application/json" `
            -UseBasicParsing -TimeoutSec 30
        $loginJson = $loginResp.Content | ConvertFrom-Json
        if ($loginJson.data.token) { $AdminToken = $loginJson.data.token }
    } catch {}
}

# BB-SCH-01 schedules list
$schHeaders = @{}
if ($AdminToken) { $schHeaders["Authorization"] = "Bearer $AdminToken" }
$t = Invoke-Timed {
    Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/schedules?page=1&size=1" -Method GET -Headers $schHeaders -UseBasicParsing -TimeoutSec 15
}
$schOk = $t.ok -and $t.data.StatusCode -eq 200
Add-R "BB-SCH-01" "Admin schedules list" $schOk $t.ms $(if ($schOk) { "status=200" } else { $t.data })

# BB-SLA-01 Collector SLA scan
$t = Invoke-Timed {
    Invoke-WebRequest -Uri "$BaseCollector/collector/alerts/scan" -Method POST -UseBasicParsing -TimeoutSec 120
}
$slaOk = $t.ok -and $t.data.StatusCode -eq 200
Add-R "BB-SLA-01" "Collector SLA scan" $slaOk $t.ms $(if ($slaOk) { $t.data.Content.Substring(0, [Math]::Min(120, $t.data.Content.Length)) } else { $t.data })

# BB-INT-01 internal scopes (optional token — empty token allowed in local dev)
$headers = @{}
if ($RegistryToken) { $headers["X-Registry-Token"] = $RegistryToken }
$t = Invoke-Timed {
    Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/internal/alerts/scopes" -Method GET -Headers $headers -UseBasicParsing -TimeoutSec 30
}
$intOk = $t.ok -and $t.data.StatusCode -eq 200
Add-R "BB-INT-01" "Admin internal alert scopes" $intOk $t.ms $(if ($intOk) { "200" } else { $t.data })

# BB-REG-02 platform jobs disabled
$t = Invoke-Timed {
    Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/schedules?jobType=PLATFORM&page=1&size=50" -Method GET -Headers $schHeaders -UseBasicParsing -TimeoutSec 15
}
$regOk = $false
if ($t.ok -and $t.data.StatusCode -eq 200) {
    $body = $t.data.Content | ConvertFrom-Json
    $records = @($body.data.records)
    $flush = $records | Where-Object { $_.jobKey -eq 'admin.registry.heartbeat-flush' -and $_.status -eq 1 }
    $offline = $records | Where-Object { $_.jobKey -eq 'admin.registry.offline-check' -and $_.status -eq 1 }
    $regOk = (-not $flush -and -not $offline)
}
Add-R "BB-REG-02" "Deprecated registry jobs disabled" $regOk $t.ms $(if ($regOk) { "ok" } else { "still enabled or unreachable" })

# BB-SLA-02 manual scan via Admin
if ($AdminToken) {
    $auth = @{ Authorization = "Bearer $AdminToken" }
    $t = Invoke-Timed {
        Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/alerts/scan" -Method POST -Headers $auth -UseBasicParsing -TimeoutSec 120
    }
    $manOk = $t.ok -and $t.data.StatusCode -eq 200
    Add-R "BB-SLA-02" "Admin manual SLA scan" $manOk $t.ms $(if ($manOk) { "200" } else { $t.data })
} else {
    Add-R "BB-SLA-02" "Admin manual SLA scan" $false 0 "login-failed"
}

$fail = @($results | Where-Object { -not $_.ok }).Count
$report = [pscustomobject]@{
    timestamp = (Get-Date).ToString("o")
    total = $results.Count
    failed = $fail
    cases = $results
}
$report | ConvertTo-Json -Depth 6 | Set-Content -Path $ReportJson -Encoding UTF8
Write-Host "Report: $ReportJson"
if ($fail -gt 0) { exit 1 }
exit 0
