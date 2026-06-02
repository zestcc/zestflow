# ZestFlow black-box probe (local)
$ErrorActionPreference = "Continue"
$BaseAdmin = "http://127.0.0.1:8080"
$BaseNetty = "http://127.0.0.1:20550"
$BaseCollector = "http://127.0.0.1:20650"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Ts = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportJson = Join-Path $OutDir "blackbox-$Ts.json"

$results = New-Object System.Collections.Generic.List[object]

function Add-Result($category, $name, $method, $url, $status, $ok, $ms, $note) {
    $results.Add([pscustomobject]@{
        category = $category; name = $name; method = $method; url = $url
        status = $status; ok = $ok; latencyMs = $ms; note = $note
    }) | Out-Null
}

function Invoke-Api($method, $url, $body, $headers, $timeoutSec = 30) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $params = @{ Uri = $url; Method = $method; TimeoutSec = $timeoutSec; UseBasicParsing = $true }
        if ($headers) { $params.Headers = $headers }
        if ($null -ne $body) { $params.Body = $body; $params.ContentType = "application/json" }
        $r = Invoke-WebRequest @params
        $sw.Stop()
        return @{ status = [int]$r.StatusCode; ok = $true; ms = $sw.ElapsedMilliseconds; body = $r.Content }
    } catch {
        $sw.Stop()
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode.value__ }
        $respBody = ""
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object IO.StreamReader($stream)
            $respBody = $reader.ReadToEnd()
        } catch {}
        return @{ status = $status; ok = $false; ms = $sw.ElapsedMilliseconds; body = $respBody }
    }
}

function Measure-Qps($name, $url, $method, $body, $headers, $requests) {
    $latencies = New-Object System.Collections.Generic.List[int]
    $success = 0
    $fail = 0
    $swTotal = [System.Diagnostics.Stopwatch]::StartNew()
    for ($i = 0; $i -lt $requests; $i++) {
        $r = Invoke-Api $method $url $body $headers 60
        [void]$latencies.Add([int]$r.ms)
        if ($r.ok -and $r.status -ge 200 -and $r.status -lt 300) { $success++ } else { $fail++ }
    }
    $swTotal.Stop()
    $sorted = $latencies | Sort-Object
    $cnt = $sorted.Count
    $p50 = $sorted[[int]($cnt * 0.5)]
    $p95 = $sorted[[int][Math]::Min($cnt - 1, [Math]::Floor($cnt * 0.95))]
    $p99 = $sorted[[int][Math]::Min($cnt - 1, [Math]::Floor($cnt * 0.99))]
    $max = ($sorted | Measure-Object -Maximum).Maximum
    $qps = [math]::Round($requests / ($swTotal.ElapsedMilliseconds / 1000.0), 2)
    return @{
        name = $name; requests = $requests; success = $success; fail = $fail
        durationMs = $swTotal.ElapsedMilliseconds; qps = $qps
        latencyP50 = $p50; latencyP95 = $p95; latencyP99 = $p99; latencyMax = $max
    }
}

Write-Host "=== ZestFlow Blackbox ==="

$r = Invoke-Api GET "$BaseAdmin/api/dashboard/stats" $null $null
Add-Result "security" "admin-no-token" GET "$BaseAdmin/api/dashboard/stats" $r.status ($r.status -in 401,403) $r.ms ""

$r = Invoke-Api GET "$BaseAdmin/api/playground/scenes/list-all" $null $null
Add-Result "security" "playground-no-token" GET "$BaseAdmin/api/playground/scenes/list-all" $r.status ($r.status -in 401,403) $r.ms ""

$r = Invoke-Api POST "$BaseAdmin/api/registry/register" '{"executorId":"evil","host":"1.1.1.1","port":1}' $null
Add-Result "security" "registry-no-token-dev" POST "$BaseAdmin/api/registry/register" $r.status ($r.status -ge 200 -and $r.status -lt 500) $r.ms "dev-open"

$loginBody = '{"username":"admin","password":"admin123"}'
$r = Invoke-Api POST "$BaseAdmin/api/auth/login" $loginBody $null
$token = $null
if ($r.ok) { try { $token = (ConvertFrom-Json $r.body).data.token } catch {} }
Add-Result "auth" "login-ok" POST "$BaseAdmin/api/auth/login" $r.status ($null -ne $token) $r.ms ""

$authH = @{ Authorization = "Bearer $token" }

$r = Invoke-Api POST "$BaseAdmin/api/auth/login" '{"username":"admin","password":"wrong"}' $null
$badLoginOk = $false
if ($r.ok) { try { $badLoginOk = ((ConvertFrom-Json $r.body).code -ne 200) } catch {} } else { $badLoginOk = ($r.status -ge 400) }
Add-Result "auth" "login-bad-password" POST "$BaseAdmin/api/auth/login" $r.status $badLoginOk $r.ms ""

$r = Invoke-Api GET "$BaseNetty/health" $null $null
Add-Result "executor" "netty-health" GET "$BaseNetty/health" $r.status ($r.status -eq 200) $r.ms ""

$r = Invoke-Api POST "$BaseNetty/api/orders/handleApplyAfterSale" '{"applyId":"BB-001"}' $null
Add-Result "executor" "netty-business-api" POST "$BaseNetty/api/orders/handleApplyAfterSale" $r.status ($r.status -eq 200) $r.ms ""

$r = Invoke-Api GET "$BaseNetty/api/chains" $null $null
Add-Result "executor" "netty-chains" GET "$BaseNetty/api/chains" $r.status ($r.status -eq 200) $r.ms ""

$r = Invoke-Api GET "$BaseNetty/api/endpoints" $null $null
Add-Result "executor" "netty-endpoints" GET "$BaseNetty/api/endpoints" $r.status ($r.status -eq 200) $r.ms ""

if ($token) {
    $r = Invoke-Api GET "$BaseAdmin/api/auth/userinfo" $null $authH
    Add-Result "admin" "userinfo" GET "$BaseAdmin/api/auth/userinfo" $r.status ($r.status -eq 200) $r.ms ""

    $r = Invoke-Api GET "$BaseAdmin/api/dashboard/stats" $null $authH
    Add-Result "admin" "dashboard" GET "$BaseAdmin/api/dashboard/stats" $r.status ($r.status -eq 200) $r.ms ""

    $chainsUrl = "$BaseAdmin/api/chains?appCode=demo-app" + '&page=1&size=5'
    $r = Invoke-Api GET $chainsUrl $null $authH
    Add-Result "admin" "chains-proxy" GET $chainsUrl $r.status ($r.status -eq 200) $r.ms ""

    $r = Invoke-Api POST "$BaseAdmin/api/playground/execute/SCN20260601000229" '{"applyId":"BB-PG-001"}' $authH 90
    Add-Result "playground" "execute-api-via-netty" POST "SCN20260601000229" $r.status ($r.status -eq 200) $r.ms ""

    $r = Invoke-Api POST "$BaseAdmin/api/playground/execute/SCN20260531000001" '{"message":"blackbox"}' $authH 90
    Add-Result "playground" "execute-hello-chain" POST "SCN20260531000001" $r.status ($r.status -eq 200) $r.ms ""

    $r = Invoke-Api GET "$BaseAdmin/api/playground/scenes/available-endpoints?appCode=demo-app" $null $authH
    $no8081 = -not ($r.body -match "http://localhost:8081")
    Add-Result "playground" "endpoints-no-tomcat-url" GET "available-endpoints" $r.status $no8081 $r.ms ""

    $rateOk = 0; $rate429 = 0
    for ($i = 1; $i -le 35; $i++) {
        $rr = Invoke-Api POST "$BaseAdmin/api/playground/execute/SCN20260531000001" '{"message":"r"}' $authH 30
        if ($rr.status -eq 429) { $rate429++ } elseif ($rr.status -eq 200) { $rateOk++ }
    }
    Add-Result "playground" "rate-limit-30" POST "execute-x35" 0 ($rate429 -gt 0) 0 "ok=$rateOk 429=$rate429"

    $r = Invoke-Api POST "$BaseAdmin/api/logs/events/query" '{"page":1,"size":5}' $authH
    Add-Result "admin" "logs-query" POST "$BaseAdmin/api/logs/events/query" $r.status ($r.status -eq 200) $r.ms ""
}

$perf = @()
$perf += Measure-Qps "netty-health" "$BaseNetty/health" GET $null $null 200
$perf += Measure-Qps "netty-business" "$BaseNetty/api/orders/handleApplyAfterSale" POST '{"applyId":"perf"}' $null 50
if ($token) {
    $perf += Measure-Qps "admin-login" "$BaseAdmin/api/auth/login" POST $loginBody $null 5
    $h = @{ Authorization = "Bearer $token" }
    $perf += Measure-Qps "admin-dashboard" "$BaseAdmin/api/dashboard/stats" GET $null $h 100
}

$out = @{
    timestamp = (Get-Date).ToString("o")
    environment = @{ admin = $BaseAdmin; netty = $BaseNetty; collector = $BaseCollector }
    functional = $results
    performance = $perf
}
$json = $out | ConvertTo-Json -Depth 6
Set-Content -Path $ReportJson -Value $json -Encoding UTF8
Write-Host "Report saved: $ReportJson"
$json
