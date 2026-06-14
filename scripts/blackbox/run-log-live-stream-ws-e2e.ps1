# 日志 WebSocket 实时流黑盒 E2E — WS connected + trace/done
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$AppCode = "demo-app",
    [string]$SceneCode = "SCN20260531000001",
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"
. "$PSScriptRoot\_acceptance-common.ps1"

$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("log-live-stream-ws-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$checks = New-Object System.Collections.Generic.List[object]
$api = "$BaseAdmin/api/zestflow"

function Add-Check($name, $ok, $note) {
    $checks.Add([pscustomobject]@{ name = $name; ok = $ok; note = $note }) | Out-Null
}

Write-Host "=== Log Live Stream WebSocket E2E ===" -ForegroundColor Cyan

$token = Login-AdminToken $BaseAdmin
if (-not $token) {
    Add-Check "login" $false "status=failed"
    Save-AcceptanceReport $ReportJson @{ baseAdmin = $BaseAdmin } $checks
    if ($AllowSkip) { exit 2 }
    exit 1
}
Add-Check "login" $true "ok"

$feat = Invoke-AcceptanceApi GET "$api/system/features" $null @{ Authorization = "Bearer $token" } 10
$wsEnabled = $false
if ($feat.ok) {
    try { $wsEnabled = (ConvertFrom-Json $feat.body).logLiveStream.websocketEnabled -eq $true } catch {}
}
Add-Check "features-ws-enabled" $wsEnabled $(if ($wsEnabled) { "websocketEnabled=true" } else { "websocket disabled" })
if (-not $wsEnabled) {
    Save-AcceptanceReport $ReportJson @{ baseAdmin = $BaseAdmin; appCode = $AppCode } $checks
    if ($AllowSkip) { exit 2 }
    exit 1
}

$exec = Invoke-PlaygroundScene $BaseAdmin $token $SceneCode '{"userId":"U10086"}' 120
$executionId = Extract-ExecutionId $exec.body
Add-Check "playground-trigger" ($exec.ok -and $executionId) $(if ($executionId) { "executionId=$executionId" } else { "status=$($exec.status)" })

if (-not $executionId) {
    $listBody = @{ appCode = $AppCode; page = 1; pageSize = 5 } | ConvertTo-Json -Compress
    $list = Invoke-AcceptanceApi POST "$api/logs/executions" $listBody @{ Authorization = "Bearer $token" }
    if ($list.ok) {
        try {
            $records = (ConvertFrom-Json $list.body).data.records
            if ($records -and $records.Count -gt 0) { $executionId = $records[0].executionId }
        } catch {}
    }
}

if ($executionId) {
    Wait-ExecutionInLogs $BaseAdmin $token $AppCode $executionId 25 | Out-Null
    $wsBase = $BaseAdmin -replace '^http', 'ws'
    $wsUrl = "$wsBase/api/zestflow/logs/executions/$executionId/ws?appCode=$([Uri]::EscapeDataString($AppCode))&access_token=$([Uri]::EscapeDataString($token))"
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $ws = New-Object System.Net.WebSockets.ClientWebSocket
    $cts = New-Object System.Threading.CancellationTokenSource
    $cts.CancelAfter(35000)
    $gotConnected = $false
    $gotTraceOrDone = $false
    try {
        $ws.ConnectAsync([Uri]$wsUrl, $cts.Token).GetAwaiter().GetResult() | Out-Null
        $buffer = New-Object byte[] 65536
        $deadline = (Get-Date).AddSeconds(35)
        while ((Get-Date) -lt $deadline) {
            $seg = [ArraySegment[byte]]::new($buffer)
            $result = $ws.ReceiveAsync($seg, $cts.Token).GetAwaiter().GetResult()
            if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) { break }
            $text = [Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count)
            try {
                $json = ConvertFrom-Json $text
                if ($json.event -eq 'connected') { $gotConnected = $true }
                if ($json.event -eq 'trace' -or $json.event -eq 'done') { $gotTraceOrDone = $true; break }
            } catch {}
        }
    } catch {
        Add-Check "ws-session" $false $_.Exception.Message
    } finally {
        try { if ($ws.State -eq 'Open') { $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'done', $cts.Token).GetAwaiter().GetResult() | Out-Null } } catch {}
        try { $ws.Dispose() } catch {}
        $cts.Dispose()
    }
    $sw.Stop()
    Add-Check "ws-connected" $gotConnected $(if ($gotConnected) { "ms=$($sw.ElapsedMilliseconds)" } else { "timeout" })
    Add-Check "ws-trace-or-done" $gotTraceOrDone $(if ($gotTraceOrDone) { "ok" } else { "no trace/done" })
} else {
    Add-Check "ws-connected" $false "no executionId"
    Add-Check "ws-trace-or-done" $false "no executionId"
}

Write-AcceptanceChecks $checks
Save-AcceptanceReport $ReportJson @{ baseAdmin = $BaseAdmin; appCode = $AppCode } $checks
$fail = @($checks | Where-Object { -not $_.ok }).Count
if ($fail -eq 0) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
