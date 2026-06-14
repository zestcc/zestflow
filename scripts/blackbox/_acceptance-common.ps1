# 生产验收公共函数 — 被 link/stress/acceptance 脚本 dot-source
function Invoke-AcceptanceApi($method, $url, $body, $headers, [int]$TimeoutSec = 60) {
    try {
        $p = @{ Uri=$url; Method=$method; TimeoutSec=$TimeoutSec; UseBasicParsing=$true }
        if ($headers) { $p.Headers = $headers }
        if ($null -ne $body) { $p.Body = $body; $p.ContentType = "application/json" }
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $r = Invoke-WebRequest @p
        $sw.Stop()
        return @{ ok=$true; status=[int]$r.StatusCode; body=$r.Content; ms=$sw.ElapsedMilliseconds }
    } catch {
        $sw.Stop()
        $st = 0; $b = ""
        if ($_.Exception.Response) {
            $st = [int]$_.Exception.Response.StatusCode.value__
            try { $b = (New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch {}
        }
        return @{ ok=$false; status=$st; body=$b; ms=$sw.ElapsedMilliseconds }
    }
}

function Test-ResultBusinessOk($jsonBody) {
    if (-not $jsonBody) { return $false }
    try { return ((ConvertFrom-Json $jsonBody).code -eq 200) } catch { return $false }
}

function Login-AdminToken($BaseAdmin, $username = "admin", $password = "admin123") {
    $login = Invoke-AcceptanceApi POST "$BaseAdmin/api/zestflow/auth/login" (@{ username=$username; password=$password } | ConvertTo-Json -Compress) $null 20
    if (-not $login.ok) { return $null }
    try { return (ConvertFrom-Json $login.body).data.token } catch { return $null }
}

function Read-SseUntilEvent($url, $token, $eventName, [int]$TimeoutSec = 12) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
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
                Start-Sleep -Milliseconds 80
                continue
            }
            [void]$buf.AppendLine($line)
            if ($line -eq "event: $eventName" -or $line -eq "event:$eventName") {
                $sw.Stop()
                return @{ ok=$true; ms=$sw.ElapsedMilliseconds; snippet=$buf.ToString() }
            }
        }
        $sw.Stop()
        return @{ ok=$false; ms=$sw.ElapsedMilliseconds; snippet=$buf.ToString() }
    } catch {
        $sw.Stop()
        return @{ ok=$false; ms=$sw.ElapsedMilliseconds; snippet=$_.Exception.Message }
    } finally {
        if ($reader) { try { $reader.Close() } catch {} }
        if ($resp) { try { $resp.Close() } catch {} }
    }
}

function Read-WebSocketUntilEvent($wsUrl, $eventName, [int]$TimeoutSec = 15) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $ws = New-Object System.Net.WebSockets.ClientWebSocket
    $cts = New-Object System.Threading.CancellationTokenSource
    $cts.CancelAfter($TimeoutSec * 1000)
    try {
        $ws.ConnectAsync([Uri]$wsUrl, $cts.Token).GetAwaiter().GetResult() | Out-Null
        $buffer = New-Object byte[] 65536
        $deadline = (Get-Date).AddSeconds($TimeoutSec)
        while ((Get-Date) -lt $deadline) {
            $seg = [ArraySegment[byte]]::new($buffer)
            $result = $ws.ReceiveAsync($seg, $cts.Token).GetAwaiter().GetResult()
            if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) { break }
            $text = [Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count)
            try {
                $json = ConvertFrom-Json $text
                if ($json.event -eq $eventName) {
                    $sw.Stop()
                    return @{ ok = $true; ms = $sw.ElapsedMilliseconds; payload = $json.data }
                }
            } catch {}
        }
        $sw.Stop()
        return @{ ok = $false; ms = $sw.ElapsedMilliseconds }
    } catch {
        $sw.Stop()
        return @{ ok = $false; ms = $sw.ElapsedMilliseconds; snippet = $_.Exception.Message }
    } finally {
        try { if ($ws.State -eq 'Open') { $ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'done', $cts.Token).GetAwaiter().GetResult() | Out-Null } } catch {}
        try { $ws.Dispose() } catch {}
        $cts.Dispose()
    }
}

function Invoke-PlaygroundScene($BaseAdmin, $token, $sceneCode, $bodyJson, [int]$TimeoutSec = 120) {
    $h = @{ Authorization = "Bearer $token" }
    return Invoke-AcceptanceApi POST "$BaseAdmin/api/zestflow/playground/execute/$sceneCode" $bodyJson $h $TimeoutSec
}

function Extract-ExecutionId($jsonBody) {
    if (-not $jsonBody) { return $null }
    try {
        $root = ConvertFrom-Json $jsonBody
        $data = if ($root.data) { $root.data } else { $root }
        foreach ($key in @('instanceId', 'executionId', 'orderId')) {
            if ($data.$key) { return [string]$data.$key }
        }
        if ($data.data) {
            foreach ($key in @('instanceId', 'executionId', 'orderId')) {
                if ($data.data.$key) { return [string]$data.data.$key }
            }
        }
    } catch {}
    return $null
}

function Wait-ExecutionInLogs($BaseAdmin, $token, $appCode, $executionId, [int]$MaxWaitSec = 30) {
    $h = @{ Authorization = "Bearer $token" }
    $deadline = (Get-Date).AddSeconds($MaxWaitSec)
    while ((Get-Date) -lt $deadline) {
        $body = @{ appCode=$appCode; page=1; pageSize=20; executionId=$executionId } | ConvertTo-Json -Compress
        $list = Invoke-AcceptanceApi POST "$BaseAdmin/api/zestflow/logs/executions" $body $h 20
        if ($list.ok) {
            try {
                $data = (ConvertFrom-Json $list.body).data
                $records = if ($data.list) { $data.list } else { $data.records }
                if ($records | Where-Object { $_.executionId -eq $executionId }) {
                    return $true
                }
            } catch {}
        }
        Start-Sleep -Seconds 2
    }
    return $false
}

function Save-AcceptanceReport($path, $meta, $checks) {
    $out = @{
        timestamp = (Get-Date).ToString("o")
        meta = $meta
        checks = $checks
        failCount = @($checks | Where-Object { -not $_.ok }).Count
    }
    Set-Content -Path $path -Value ($out | ConvertTo-Json -Depth 8) -Encoding UTF8
    Write-Host "Saved: $path"
}

function Write-AcceptanceChecks($checks) {
    foreach ($c in $checks) {
        $color = if ($c.ok) { 'Green' } else { 'Red' }
        Write-Host ("  [{0}] {1} — {2}" -f $(if ($c.ok) { 'PASS' } else { 'FAIL' }), $c.name, $c.note) -ForegroundColor $color
    }
}

function Warm-AcceptanceReadCache(
    [string]$BaseAdmin,
    [hashtable]$Headers,
    [string]$AppCode = "demo-app"
) {
    $warmed = $false
    foreach ($i in 1..8) {
        $chains = Invoke-AcceptanceApi GET "$BaseAdmin/api/zestflow/chains?appCode=$AppCode&page=1&size=5" $null $Headers 30
        $designs = Invoke-AcceptanceApi GET "$BaseAdmin/api/zestflow/designs?appCode=$AppCode&page=1&size=5" $null $Headers 30
        $hasData = ($chains.ok -and ($chains.body -match '"total"\s*:\s*[1-9]')) `
            -or ($designs.ok -and ($designs.body -match '"total"\s*:\s*[1-9]'))
        $notStale = ($chains.body -notmatch '"stale"\s*:\s*true') -and ($designs.body -notmatch '"stale"\s*:\s*true')
        if ($hasData -and $notStale) {
            $warmed = $true
            break
        }
        Start-Sleep -Seconds 3
    }
    return $warmed
}

function Remove-AcceptanceAppExecutors(
    [string]$BaseAdmin,
    [hashtable]$Headers,
    [string]$AppCode = "demo-app"
) {
    $seen = New-Object 'System.Collections.Generic.HashSet[string]'
    function Remove-OneExecutor([string]$executorId) {
        if ([string]::IsNullOrWhiteSpace($executorId)) { return }
        if (-not $seen.Add($executorId)) { return }
        $eid = [uri]::EscapeDataString([string]$executorId)
        Invoke-AcceptanceApi DELETE "$BaseAdmin/api/zestflow/registry/$eid" $null $null 10 | Out-Null
        if ($Headers) {
            Invoke-AcceptanceApi PUT "$BaseAdmin/api/zestflow/executors/$eid/status" '{"status":0}' $Headers 10 | Out-Null
        }
        $script:accDeregCount++
    }
    $script:accDeregCount = 0
    $list = Invoke-AcceptanceApi GET "$BaseAdmin/api/zestflow/executors" $null $Headers 20
    if ($list.ok) {
        try {
            foreach ($ex in @((ConvertFrom-Json $list.body).data)) {
                $match = ($ex.appCode -eq $AppCode) -or ($ex.appName -eq $AppCode) -or ($ex.executorId -like "$AppCode@*")
                if ($match) { Remove-OneExecutor $ex.executorId }
            }
        } catch {}
    }
    $peers = Invoke-AcceptanceApi GET "$BaseAdmin/api/zestflow/registry/peers?appCode=$AppCode" $null $Headers 20
    if ($peers.ok) {
        try {
            foreach ($p in @((ConvertFrom-Json $peers.body).data)) {
                Remove-OneExecutor $p.executorId
            }
        } catch {}
    }
    return $script:accDeregCount
}
