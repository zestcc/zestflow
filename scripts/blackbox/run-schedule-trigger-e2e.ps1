# E2E-08：调度 trigger 独立脚本 — 列出/创建 schedule → POST trigger
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

$list = Invoke-AcceptanceApi GET "$api/schedules?page=1&size=5" $null $h
Add-Check "schedules-list" $list.ok "status=$($list.status)"

$scheduleId = $null
if ($list.ok) {
    try {
        $root = ConvertFrom-Json $list.body
        $page = Get-DataNode $root
        if ($page.records -and $page.records.Count -gt 0) {
            $scheduleId = $page.records[0].id
        }
    } catch {}
}

if (-not $scheduleId) {
    $chains = Invoke-AcceptanceApi GET "$api/chains?appCode=$AppCode&page=1&size=10" $null $h
    $chainCode = $null
    $chainName = $null
    if ($chains.ok) {
        try {
            $cRoot = ConvertFrom-Json $chains.body
            $cPage = Get-DataNode $cRoot
            foreach ($row in @($cPage.records)) {
                if ($row.status -eq 4 -and $row.code) {
                    $chainCode = [string]$row.code
                    $chainName = [string]$row.name
                    break
                }
            }
            if (-not $chainCode -and $cPage.records -and $cPage.records.Count -gt 0) {
                $chainCode = [string]$cPage.records[0].code
                $chainName = [string]$cPage.records[0].name
            }
        } catch {}
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
        Add-Check "schedule-create" $created.ok "status=$($created.status)"
        if ($created.ok) {
            try {
                $createdRoot = ConvertFrom-Json $created.body
                $node = Get-DataNode $createdRoot
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
$triggerOk = $trigger.ok -and ($trigger.body -match '"code"\s*:\s*200|"status"')
Add-Check "schedule-trigger" $triggerOk "id=$scheduleId status=$($trigger.status)"

Write-AcceptanceChecks $checks
Save-AcceptanceReport $ReportJson @{ baseAdmin=$BaseAdmin; appCode=$AppCode; scheduleId=$scheduleId } $checks
$fail = @($checks | Where-Object { -not $_.ok }).Count
if ($fail -eq 0) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
