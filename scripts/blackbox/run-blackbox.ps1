# ZestFlow black-box probe (local)
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$BaseNetty = "http://127.0.0.1:20550",
    [string]$BaseCollector = "http://127.0.0.1:20650",
    [switch]$PerfGateOnly,
    [string]$PolicyFile = (Join-Path $PSScriptRoot "perf-gate-policy.json")
)

$ErrorActionPreference = "Continue"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Ts = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportJson = Join-Path $OutDir $(if ($PerfGateOnly) { "perf-blackbox-$Ts.json" } else { "blackbox-$Ts.json" })

$results = New-Object System.Collections.Generic.List[object]
$script:exitCode = 0

function Add-Result($category, $name, $method, $url, $status, $ok, $ms, $note) {
    $results.Add([pscustomobject]@{
        category = $category; name = $name; method = $method; url = $url
        status = $status; ok = $ok; latencyMs = $ms; note = $note
    }) | Out-Null
    if (-not $ok) { $script:exitCode = 1 }
}

function Get-Percentile($sorted, $p) {
    $cnt = $sorted.Count
    if ($cnt -eq 0) { return 0 }
    if ($p -le 0) { return $sorted[0] }
    if ($p -ge 1) { return $sorted[$cnt - 1] }
    $idx = [int][Math]::Min($cnt - 1, [Math]::Floor($cnt * $p))
    return $sorted[$idx]
}

function Build-LatencyStats($latencies) {
    $sorted = @($latencies | Sort-Object)
    $cnt = $sorted.Count
    if ($cnt -eq 0) {
        return @{ count = 0; p50 = 0; p95 = 0; p99 = 0; p999 = 0; max = 0; avg = 0 }
    }
    $sum = ($sorted | Measure-Object -Sum).Sum
    return @{
        count = $cnt
        p50 = (Get-Percentile $sorted 0.50)
        p95 = (Get-Percentile $sorted 0.95)
        p99 = (Get-Percentile $sorted 0.99)
        p999 = (Get-Percentile $sorted 0.999)
        max = ($sorted | Measure-Object -Maximum).Maximum
        avg = [math]::Round($sum / $cnt, 2)
    }
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
        return @{ status = $status; ok = $false; ms = $sw.ElapsedMilliseconds; body = "" }
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
    $stats = Build-LatencyStats $latencies
    $qps = [math]::Round($requests / ($swTotal.ElapsedMilliseconds / 1000.0), 2)
    return @{
        mode = "serial"; name = $name; requests = $requests; success = $success; fail = $fail
        durationMs = $swTotal.ElapsedMilliseconds; qps = $qps
        latencyP50 = $stats.p50; latencyP95 = $stats.p95; latencyP99 = $stats.p99
        latencyP999 = $stats.p999; latencyMax = $stats.max; latencyAvg = $stats.avg
    }
}

function Measure-QpsConcurrent($name, $url, $method, $body, $headers, $requests, $concurrency) {
    $latencies = New-Object System.Collections.Concurrent.ConcurrentBag[int]
    $outcomes = New-Object System.Collections.Concurrent.ConcurrentBag[bool]
    $swTotal = [System.Diagnostics.Stopwatch]::StartNew()
    $sem = New-Object System.Threading.SemaphoreSlim($concurrency, $concurrency)
    $tasks = New-Object System.Collections.Generic.List[System.Threading.Tasks.Task]

    for ($i = 0; $i -lt $requests; $i++) {
        $task = [System.Threading.Tasks.Task]::Run([Action]{
            $null = $sem.Wait()
            try {
                $r = Invoke-Api $method $url $body $headers 60
                $latencies.Add([int]$r.ms) | Out-Null
                $ok = $r.ok -and $r.status -ge 200 -and $r.status -lt 300
                $outcomes.Add($ok) | Out-Null
            } finally {
                $null = $sem.Release()
            }
        })
        $tasks.Add($task) | Out-Null
    }

    [System.Threading.Tasks.Task]::WaitAll($tasks.ToArray())
    $swTotal.Stop()

    $success = @($outcomes | Where-Object { $_ }).Count
    $fail = $requests - $success
    $stats = Build-LatencyStats @($latencies.ToArray())
    $qps = [math]::Round($requests / ($swTotal.ElapsedMilliseconds / 1000.0), 2)
    return @{
        mode = "concurrent"; concurrency = $concurrency; name = $name; requests = $requests
        success = $success; fail = $fail
        durationMs = $swTotal.ElapsedMilliseconds; qps = $qps
        latencyP50 = $stats.p50; latencyP95 = $stats.p95; latencyP99 = $stats.p99
        latencyP999 = $stats.p999; latencyMax = $stats.max; latencyAvg = $stats.avg
    }
}

function Test-PerfGate($perfResults, $policy) {
    $allOk = $true
    foreach ($p in $perfResults) {
        $rule = $policy.blackboxMs.($p.name)
        if (-not $rule) { continue }
        $limit = [double]$rule.p999
        $ok = ([double]$p.latencyP999 -le $limit) -and ($p.fail -eq 0)
        $color = if ($ok) { 'Green' } else { 'Red' }
        Write-Host ("  [{0}] {1} p999={2}ms limit={3}ms fail={4}" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $p.name, $p.latencyP999, $limit, $p.fail) -ForegroundColor $color
        if (-not $ok) { $allOk = $false; $script:exitCode = 1 }
    }
    return $allOk
}

$policy = $null
if (Test-Path $PolicyFile) {
    try { $policy = Get-Content $PolicyFile -Raw | ConvertFrom-Json } catch {}
}

Write-Host "=== ZestFlow Blackbox $(if ($PerfGateOnly) { '(Perf Gate)' }) ==="

$token = $null
if (-not $PerfGateOnly) {
    $r = Invoke-Api GET "$BaseAdmin/api/zestflow/dashboard/stats" $null $null
    Add-Result "security" "admin-no-token" GET "$BaseAdmin/api/zestflow/dashboard/stats" $r.status ($r.status -eq 401) $r.ms ""

    $r = Invoke-Api GET "$BaseAdmin/api/zestflow/playground/scenes/list-all" $null $null
    Add-Result "security" "playground-no-token" GET "$BaseAdmin/api/zestflow/playground/scenes/list-all" $r.status ($r.status -in 401,403) $r.ms ""

    $r = Invoke-Api POST "$BaseAdmin/api/zestflow/registry/register" '{"executorId":"evil","host":"1.1.1.1","port":1}' $null
    Add-Result "security" "registry-no-token-dev" POST "$BaseAdmin/api/zestflow/registry/register" $r.status ($r.status -ge 200 -and $r.status -lt 500) $r.ms "dev-open"

    $loginBody = '{"username":"admin","password":"admin123"}'
    $r = Invoke-Api POST "$BaseAdmin/api/zestflow/auth/login" $loginBody $null
    if ($r.ok) { try { $token = (ConvertFrom-Json $r.body).data.token } catch {} }
    Add-Result "auth" "login-ok" POST "$BaseAdmin/api/zestflow/auth/login" $r.status ($null -ne $token) $r.ms ""

    $r = Invoke-Api POST "$BaseAdmin/api/zestflow/auth/login" '{"username":"admin","password":"wrong"}' $null
    $badLoginOk = $false
    if ($r.ok) { try { $badLoginOk = ((ConvertFrom-Json $r.body).code -ne 200) } catch {} } else { $badLoginOk = ($r.status -ge 400) }
    Add-Result "auth" "login-bad-password" POST "$BaseAdmin/api/zestflow/auth/login" $r.status $badLoginOk $r.ms ""

    $r = Invoke-Api GET "$BaseNetty/health" $null $null
    Add-Result "executor" "netty-health" GET "$BaseNetty/health" $r.status ($r.status -eq 200) $r.ms ""

    $r = Invoke-Api POST "$BaseNetty/api/orders/handleApplyAfterSale" '{"applyId":"BB-001"}' $null
    Add-Result "executor" "netty-business-api" POST "$BaseNetty/api/orders/handleApplyAfterSale" $r.status ($r.status -eq 200) $r.ms ""

    $r = Invoke-Api GET "$BaseNetty/api/chains" $null $null
    Add-Result "executor" "netty-chains" GET "$BaseNetty/api/chains" $r.status ($r.status -eq 200) $r.ms ""

    $r = Invoke-Api GET "$BaseNetty/api/endpoints" $null $null
    Add-Result "executor" "netty-endpoints" GET "$BaseNetty/api/endpoints" $r.status ($r.status -eq 200) $r.ms ""

    if ($token) {
        $authH = @{ Authorization = "Bearer $token" }
        $r = Invoke-Api GET "$BaseAdmin/api/zestflow/auth/userinfo" $null $authH
        Add-Result "admin" "userinfo" GET "$BaseAdmin/api/zestflow/auth/userinfo" $r.status ($r.status -eq 200) $r.ms ""
        $r = Invoke-Api GET "$BaseAdmin/api/zestflow/dashboard/stats" $null $authH
        Add-Result "admin" "dashboard" GET "$BaseAdmin/api/zestflow/dashboard/stats" $r.status ($r.status -eq 200) $r.ms ""
        $r = Invoke-Api POST "$BaseAdmin/api/zestflow/logs/events/query" '{"page":1,"size":5}' $authH
        Add-Result "admin" "logs-query" POST "$BaseAdmin/api/zestflow/logs/events/query" $r.status ($r.status -eq 200) $r.ms ""
    }
}

$perf = @()
if ($policy -and $policy.blackboxMs) {
    $hRule = $policy.blackboxMs.'netty-health'
    $bRule = $policy.blackboxMs.'netty-business'
    $perf += Measure-Qps "netty-health" "$BaseNetty/health" GET $null $null $hRule.requests
    $bizPath = if ($bRule.path) { $bRule.path } else { "/api/chains" }
    $bizMethod = if ($bRule.method) { $bRule.method } else { "GET" }
    $bizBody = if ($bizMethod -eq "POST") { '{"applyId":"perf"}' } else { $null }
    $perf += Measure-Qps "netty-business" "$BaseNetty$bizPath" $bizMethod $bizBody $null $bRule.requests
} else {
    $perf += Measure-Qps "netty-health" "$BaseNetty/health" GET $null $null 200
    $perf += Measure-Qps "netty-business" "$BaseNetty/api/orders/handleApplyAfterSale" POST '{"applyId":"perf"}' $null 50
}

if ($policy) {
    Write-Host "--- Perf gate (P99.9) ---" -ForegroundColor Cyan
    Test-PerfGate $perf $policy | Out-Null
}

$out = @{
    timestamp = (Get-Date).ToString("o")
    environment = @{ admin = $BaseAdmin; netty = $BaseNetty; collector = $BaseCollector }
    functional = $results
    performance = $perf
    perfGateOnly = [bool]$PerfGateOnly
    exitCode = $script:exitCode
}
$json = $out | ConvertTo-Json -Depth 6
Set-Content -Path $ReportJson -Value $json -Encoding UTF8
Write-Host "Report saved: $ReportJson"
if (-not $PerfGateOnly) { $json }
exit $script:exitCode
