# E2E-08：调度 trigger 独立脚本 — 链调度创建/选取 → POST trigger
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$AppCode = "demo-app",
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"
. "$PSScriptRoot\_acceptance-common.ps1"

$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("schedule-trigger-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$checks = New-Object System.Collections.Generic.List[object]
$api = "$BaseAdmin/api/zestflow"

function Add-Check($name, $ok, $note) {
    $checks.Add([pscustomobject]@{ name=$name; ok=$ok; note=$note }) | Out-Null
}

function Get-DataNode($json) {
    if ($null -eq $json) { return $null }
    if ($json.data) { return $json.data }
    return $json
}

function Get-PageRecords($jsonBody) {
    try {
        $page = Get-DataNode (ConvertFrom-Json $jsonBody)
        if ($page.records) { return @($page.records) }
        if ($page.list) { return @($page.list) }
    } catch {}
    return @()
}

Write-Host "=== Schedule Trigger E2E ===" -ForegroundColor Cyan

$token = Login-AdminToken $BaseAdmin
if (-not $token) {
    Add-Check "login" $false "failed"
    Save-AcceptanceReport $ReportJson @{ baseAdmin=$BaseAdmin } $checks
    if ($AllowSkip) { exit 2 }
    exit 1
}
Add-Check "login" $true "ok"
$h = @{ Authorization = "Bearer $token" }

$list = Invoke-AcceptanceApi GET "$api/schedules?jobType=CHAIN&page=1&size=10" $null $h
$listOk = $list.ok -and (Test-ResultBusinessOk $list.body)
Add-Check "schedules-list" $listOk $(if ($listOk) { "status=$($list.status)" } else { "chain-proxy-unavailable status=$($list.status)" })

if (-not $listOk) {
    Add-Check "schedule-create" $false "skipped-chain-proxy"
    Add-Check "schedule-trigger" $false "skipped-chain-proxy"
    Write-AcceptanceChecks $checks
    Save-AcceptanceReport $ReportJson @{ baseAdmin=$BaseAdmin; appCode=$AppCode } $checks
    if ($AllowSkip) { exit 2 }
    exit 1
}

$scheduleId = $null
if ($list.ok) {
    foreach ($row in (Get-PageRecords $list.body)) {
        if ($row.id -and $row.jobType -eq 'CHAIN' -and $row.chainCode) {
            $scheduleId = $row.id
            break
        }
    }
}

if (-not $scheduleId) {
    $chains = Invoke-AcceptanceApi GET "$api/chains?appCode=$AppCode&page=1&size=10" $null $h
    $chainCode = $null
    $chainName = $null
    if ($chains.ok) {
        foreach ($row in (Get-PageRecords $chains.body)) {
            if ($row.code -eq 'CHN_DEMO_NODE_1' -and $row.status -eq 4) {
                $chainCode = [string]$row.code
                $chainName = [string]$row.name
                break
            }
        }
        if (-not $chainCode) {
            foreach ($row in (Get-PageRecords $chains.body)) {
                if ($row.status -eq 4 -and $row.code) {
                    $chainCode = [string]$row.code
                    $chainName = [string]$row.name
                    break
                }
            }
        }
        if (-not $chainCode) {
            $records = Get-PageRecords $chains.body
            if ($records.Count -gt 0) {
                $chainCode = [string]$records[0].code
                $chainName = [string]$records[0].name
            }
        }
    }
    if ($chainCode) {
        $suffix = Get-Date -Format "HHmmss"
        $createBody = @{
            chainCode = $chainCode
            chainName = $(if ($chainName) { $chainName } else { "E2E-Schedule-$suffix" })
            cron = "0 0 2 * * ?"
            remark = "auto schedule-trigger e2e"
        } | ConvertTo-Json -Compress
        $created = Invoke-AcceptanceApi POST "$api/schedules" $createBody $h
        $createOk = $created.ok -and (Test-ResultBusinessOk $created.body)
        Add-Check "schedule-create" $createOk "status=$($created.status)"
        if ($createOk) {
            try {
                $node = Get-DataNode (ConvertFrom-Json $created.body)
                if ($node.id) { $scheduleId = $node.id }
            } catch {}
        }
    } else {
        Add-Check "schedule-create" $false "no-chain-for-schedule"
    }
}

if (-not $scheduleId) {
    Add-Check "schedule-trigger" $false "skipped-no-schedule"
    Write-AcceptanceChecks $checks
    Save-AcceptanceReport $ReportJson @{ baseAdmin=$BaseAdmin; appCode=$AppCode } $checks
    if ($AllowSkip) { exit 2 }
    exit 1
}

$trigger = Invoke-AcceptanceApi POST "$api/schedules/$scheduleId/trigger" $null $h 90
$triggerOk = $trigger.ok -and (Test-ResultBusinessOk $trigger.body)
$bizCode = $null
try { $bizCode = (ConvertFrom-Json $trigger.body).code } catch {}
Add-Check "schedule-trigger" $triggerOk "id=$scheduleId http=$($trigger.status) code=$bizCode"

Write-AcceptanceChecks $checks
Save-AcceptanceReport $ReportJson @{ baseAdmin=$BaseAdmin; appCode=$AppCode; scheduleId=$scheduleId } $checks
$fail = @($checks | Where-Object { -not $_.ok }).Count
if ($fail -eq 0) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
