# 日志 SSE 实时流黑盒 E2E — 需 Admin :8080 + Collector 可达
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$AppCode = "demo-app",
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("log-live-stream-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$checks = New-Object System.Collections.Generic.List[object]
$api = "$BaseAdmin/api/zestflow"

function Add-Check($name, $ok, $note) {
    $checks.Add([pscustomobject]@{ name=$name; ok=$ok; note=$note }) | Out-Null
}
function Save-Report {
    $out = @{ timestamp=(Get-Date).ToString("o"); baseAdmin=$BaseAdmin; appCode=$AppCode; checks=$checks }
    Set-Content -Path $ReportJson -Value ($out | ConvertTo-Json -Depth 6) -Encoding UTF8
    Write-Host "Saved: $ReportJson"
}

function Invoke-Api($method, $url, $body, $headers, [int]$TimeoutSec = 30) {
    try {
        $p = @{ Uri=$url; Method=$method; TimeoutSec=$TimeoutSec; UseBasicParsing=$true }
        if ($headers) { $p.Headers = $headers }
        if ($null -ne $body) { $p.Body = $body; $p.ContentType = "application/json" }
        $r = Invoke-WebRequest @p
        return @{ ok=$true; status=[int]$r.StatusCode; body=$r.Content }
    } catch {
        $st = 0; $b = ""
        if ($_.Exception.Response) {
            $st = [int]$_.Exception.Response.StatusCode.value__
            try { $b = (New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch {}
        }
        return @{ ok=$false; status=$st; body=$b }
    }
}

function Read-SseUntilEvent($url, $token, $eventName, [int]$TimeoutSec = 10) {
    $req = [System.Net.HttpWebRequest]::Create($url)
    $req.Method = "GET"
    $req.Timeout = ($TimeoutSec * 1000)
    $req.ReadWriteTimeout = ($TimeoutSec * 1000)
    $req.Accept = "text/event-stream"
    $req.Headers.Add("Authorization", "Bearer $token")
    $resp = $null
    $reader = $null
    try {
        $resp = $req.GetResponse()
        $reader = New-Object IO.StreamReader($resp.GetResponseStream())
        $buf = New-Object System.Text.StringBuilder
        $deadline = (Get-Date).AddSeconds($TimeoutSec)
        while ((Get-Date) -lt $deadline) {
            if ($reader.EndOfStream) { break }
            $line = $reader.ReadLine()
            if ($null -eq $line) {
                Start-Sleep -Milliseconds 100
                continue
            }
            [void]$buf.AppendLine($line)
            if ($line -eq "event: $eventName") {
                return @{ ok=$true; snippet=$buf.ToString() }
            }
        }
        return @{ ok=$false; snippet=$buf.ToString() }
    } catch {
        return @{ ok=$false; snippet=$_.Exception.Message }
    } finally {
        if ($reader) { try { $reader.Close() } catch {} }
        if ($resp) { try { $resp.Close() } catch {} }
    }
}

Write-Host "=== Log Live Stream E2E ===" -ForegroundColor Cyan

$login = Invoke-Api POST "$api/auth/login" '{"username":"admin","password":"admin123"}' $null
if (-not $login.ok) {
    Add-Check "login" $false "status=$($login.status)"
    Save-Report
    if ($AllowSkip) { exit 2 }
    exit 1
}
$token = (ConvertFrom-Json $login.body).data.token
$h = @{ Authorization = "Bearer $token" }
Add-Check "login" $true "ok"

$noJwt = Invoke-Api GET "$api/logs/executions/fake-exec/stream?appCode=$AppCode" $null $null
$denied = ($noJwt.status -in 401, 403)
Add-Check "stream-no-jwt" $denied $(if ($denied) { "status=$($noJwt.status)" } else { "expected 401/403 got $($noJwt.status)" })

$listBody = @{
    appCode = $AppCode
    page = 1
    pageSize = 5
} | ConvertTo-Json -Compress
$list = Invoke-Api POST "$api/logs/executions" $listBody $h
$listOk = $false
$executionId = $null
if ($list.ok) {
    try {
        $listJson = ConvertFrom-Json $list.body
        $records = $listJson.data.records
        if ($records -and $records.Count -gt 0) {
            $executionId = $records[0].executionId
            $listOk = [bool]$executionId
        } else {
            $listOk = $true
        }
        Add-Check "executions-list" $listOk $(if ($executionId) { "executionId=$executionId" } else { "no executions (smoke skipped)" })
    } catch {
        Add-Check "executions-list" $false $_.Exception.Message
    }
} else {
    Add-Check "executions-list" $false "status=$($list.status)"
}

if ($executionId) {
    $streamUrl = "$api/logs/executions/$executionId/stream?appCode=$AppCode"
    $sse = Read-SseUntilEvent $streamUrl $token "connected" 12
    Add-Check "sse-connected" $sse.ok $(if ($sse.ok) { "received connected event" } else { "timeout or no event; snippet=$($sse.snippet)" })
} else {
    Add-Check "sse-connected" $true "skipped-no-execution"
}

$fail = @($checks | Where-Object { -not $_.ok }).Count
foreach ($c in $checks) {
    $color = if ($c.ok) { 'Green' } else { 'Red' }
    Write-Host ("  [{0}] {1} — {2}" -f $(if ($c.ok) { 'PASS' } else { 'FAIL' }), $c.name, $c.note) -ForegroundColor $color
}
Save-Report
if ($fail -eq 0) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
