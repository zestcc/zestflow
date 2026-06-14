# ???? ??Phase 2c ?? + ????Playground/Netty ?? + 75 ????
param(
    [string]$JavaHome = $(if ($env:JAVA_HOME) { $env:JAVA_HOME } else { "D:\IT\JDK17\jdk-17.0.19+10" }),
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$BaseNetty = "http://127.0.0.1:20550",
    [string]$BaseCollector = "http://127.0.0.1:20650",
    [string]$PolicyFile = (Join-Path $PSScriptRoot "perf-gate-policy.json"),
    [int]$HeavyIterations = 5,
    [int]$HelloRequests = 20,
    [switch]$SkipMavenPerf
)

$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent

$ErrorActionPreference = "Continue"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("full-perf-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$phases = New-Object System.Collections.Generic.List[object]
$runtimePerf = New-Object System.Collections.Generic.List[object]
$script:exitCode = 0

function Add-Phase($name, $ok, $note, $metrics) {
    $script:phases.Add([pscustomobject]@{
        phase = $name; ok = $ok; note = $note; metrics = $metrics
    }) | Out-Null
    if (-not $ok) { $script:exitCode = 1 }
    $color = if ($ok) { 'Green' } else { 'Red' }
    Write-Host ("[{0}] {1} ??{2}" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $name, $note) -ForegroundColor $color
}

function Get-Percentile($sorted, $p) {
    $cnt = $sorted.Count
    if ($cnt -eq 0) { return 0 }
    $idx = [int][Math]::Min($cnt - 1, [Math]::Floor($cnt * $p))
    return $sorted[$idx]
}

function Build-LatencyStats($latencies) {
    $sorted = @($latencies | Sort-Object)
    $cnt = $sorted.Count
    if ($cnt -eq 0) { return @{ count = 0; p50 = 0; p95 = 0; p99 = 0; p999 = 0; max = 0; avg = 0 } }
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

function Invoke-Api($method, $url, $body, $headers, $timeoutSec = 120) {
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

function Measure-PlaygroundPerf($name, $sceneId, $body, $headers, $requests, $timeoutSec) {
    $url = "$BaseAdmin/api/zestflow/playground/execute/$sceneId"
    $latencies = New-Object System.Collections.Generic.List[int]
    $success = 0
    $fail = 0
    $swTotal = [System.Diagnostics.Stopwatch]::StartNew()
    for ($i = 0; $i -lt $requests; $i++) {
        $r = Invoke-Api POST $url $body $headers $timeoutSec
        [void]$latencies.Add([int]$r.ms)
        if ($r.ok -and $r.status -eq 200) { $success++ } else { $fail++ }
    }
    $swTotal.Stop()
    $stats = Build-LatencyStats $latencies
    return @{
        name = $name; sceneId = $sceneId; mode = "serial"
        requests = $requests; success = $success; fail = $fail
        durationMs = $swTotal.ElapsedMilliseconds
        latencyP50 = $stats.p50; latencyP95 = $stats.p95; latencyP99 = $stats.p99
        latencyP999 = $stats.p999; latencyMax = $stats.max; latencyAvg = $stats.avg
    }
}

function Test-RuntimeLimit($result, $limitP999, $limitP95) {
    $ok = ($result.fail -eq 0)
    if ($limitP999 -gt 0 -and [double]$result.latencyP999 -gt $limitP999) { $ok = $false }
    if ($limitP95 -gt 0 -and [double]$result.latencyP95 -gt $limitP95) { $ok = $false }
    return $ok
}

function Write-FullPerfReport {
    $report = @{
        timestamp = (Get-Date).ToString("o")
        exitCode = $script:exitCode
        environment = @{ admin = $BaseAdmin; netty = $BaseNetty; collector = $BaseCollector }
        phases = $phases
        runtimePerformance = $runtimePerf
        note = "???? = Maven perf + Netty ?? + Playground Hello/75?? P-03 ???? / P-04 ??Executor ?????"
    }
    Set-Content -Path $ReportJson -Value ($report | ConvertTo-Json -Depth 8) -Encoding UTF8
    Write-Host "Full perf report: $ReportJson" -ForegroundColor Cyan
    Write-Host "========== Exit $($script:exitCode) ==========" -ForegroundColor $(if ($script:exitCode -eq 0) { 'Green' } else { 'Red' })
    exit $script:exitCode
}

Write-Host "========== ZestFlow Full Perf (????) ==========" -ForegroundColor Cyan

if (-not $SkipMavenPerf) {
    & "$PSScriptRoot\run-perf-gate.ps1" -JavaHome $JavaHome -SkipRuntimeBlackbox
    Add-Phase "maven-perf-gate" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE" $null
    if ($LASTEXITCODE -ne 0) { Write-FullPerfReport }

    $env:JAVA_HOME = $JavaHome
    $env:Path = "$JavaHome\bin;" + $env:Path
    Push-Location $Root
    Write-Host "P-03 queue saturation (AsyncEventCollector 8192) ..." -ForegroundColor DarkGray
    & mvn -q test -pl zestflow-collector/collector-jdbc -am -Pperf "-Dtest=AsyncEventCollectorStressTest" "-Dsurefire.failIfNoSpecifiedTests=false"
    $p03Exit = $LASTEXITCODE
    Add-Phase "p03-queue-stress" ($p03Exit -eq 0) "exit=$p03Exit" $null
    if ($p03Exit -ne 0) { Pop-Location; Write-FullPerfReport }

    Write-Host "P-04 round-robin (10 executors uniform) ..." -ForegroundColor DarkGray
    & mvn -q test -pl zestflow-admin -am -Pperf "-Dtest=RoundRobinDistributionGateTest" "-Dsurefire.failIfNoSpecifiedTests=false"
    $p04Exit = $LASTEXITCODE
    Add-Phase "p04-round-robin" ($p04Exit -eq 0) "exit=$p04Exit" $null
    Pop-Location
    if ($p04Exit -ne 0) { Write-FullPerfReport }
} else {
    Add-Phase "maven-perf-gate" $true "skipped" $null
}

. "$PSScriptRoot\_acceptance-stack.ps1"
$stackReady = Ensure-AcceptanceRuntimeStack $Root $JavaHome
if (-not $stackReady) {
    Add-Phase "runtime-stack-ready" $false "admin/netty/collector not reachable after re-boot" $null
    Write-FullPerfReport
}
Add-Phase "runtime-stack-ready" $true "admin+netty+collector up" $null

$policy = $null
if (Test-Path $PolicyFile) {
    try { $policy = Get-Content $PolicyFile -Raw | ConvertFrom-Json } catch {}
}

$nettyUp = $false
try {
    $ping = Invoke-WebRequest -Uri "$BaseNetty/health" -UseBasicParsing -TimeoutSec 5
    $nettyUp = ($ping.StatusCode -eq 200)
} catch {}

if ($nettyUp) {
    Start-Sleep -Seconds 15
    & "$PSScriptRoot\run-blackbox.ps1" -BaseAdmin $BaseAdmin -BaseNetty $BaseNetty -PerfGateOnly
    $nettyPerfExit = [int]$LASTEXITCODE
    if ($nettyPerfExit -ne 0) {
        Write-Host "runtime-netty-perf retry after 30s cooldown ..." -ForegroundColor DarkYellow
        Start-Sleep -Seconds 30
        & "$PSScriptRoot\run-blackbox.ps1" -BaseAdmin $BaseAdmin -BaseNetty $BaseNetty -PerfGateOnly
        $nettyPerfExit = [int]$LASTEXITCODE
    }
    Add-Phase "runtime-netty-perf" ($nettyPerfExit -eq 0) "exit=$nettyPerfExit" $null
} else {
    Add-Phase "runtime-netty-perf" $false "Netty $BaseNetty not reachable" $null
}

$token = $null
$login = Invoke-Api POST "$BaseAdmin/api/zestflow/auth/login" '{"username":"admin","password":"admin123"}' $null 10
if ($login.ok) {
    try { $token = (ConvertFrom-Json $login.body).data.token } catch {}
}

if (-not $token) {
    Add-Phase "playground-hello-perf" $false "Admin login failed ($BaseAdmin)" $null
    Add-Phase "playground-heavy75-perf" $false "skipped-no-token" $null
} else {
    $authH = @{ Authorization = "Bearer $token" }

    $helloRule = $policy.playgroundMs.'hello'
    $helloLimitP999 = if ($helloRule) { [double]$helloRule.p999 } else { 5000 }
    $hello = Measure-PlaygroundPerf "playground-hello" "SCN20260531000001" `
        '{"message":"full-perf"}' $authH $HelloRequests 90
    $runtimePerf.Add($hello) | Out-Null
    $helloOk = Test-RuntimeLimit $hello $helloLimitP999 0
    Write-Host ("  hello p999={0}ms limit={1}ms fail={2}" -f $hello.latencyP999, $helloLimitP999, $hello.fail) -ForegroundColor $(if ($helloOk) { 'Green' } else { 'Red' })
    Add-Phase "playground-hello-perf" $helloOk ("p999=$($hello.latencyP999)ms fail=$($hello.fail)") $hello

    $heavyRule = $policy.playgroundMs.'heavy75'
    $heavyLimitP95 = if ($heavyRule) { [double]$heavyRule.p95 } else { 30000 }
    $heavyLimitP999 = if ($heavyRule) { [double]$heavyRule.p999 } else { 60000 }
    $heavyReq = $HeavyIterations
    $heavy = Measure-PlaygroundPerf "playground-heavy75" "SCN20260531000004" `
        '{"stress":true}' $authH $heavyReq 300
    $runtimePerf.Add($heavy) | Out-Null
    $heavyOk = Test-RuntimeLimit $heavy $heavyLimitP999 $heavyLimitP95
    Write-Host ("  heavy75 p95={0}ms p999={1}ms fail={2}" -f $heavy.latencyP95, $heavy.latencyP999, $heavy.fail) -ForegroundColor $(if ($heavyOk) { 'Green' } else { 'Red' })
    Add-Phase "playground-heavy75-perf" $heavyOk ("p95=$($heavy.latencyP95)ms p999=$($heavy.latencyP999)ms fail=$($heavy.fail)") $heavy
}

$collectorUp = $false
try {
    $c = Invoke-WebRequest -Uri "$BaseCollector/collector/health" -UseBasicParsing -TimeoutSec 5
    $collectorUp = ($c.StatusCode -eq 200)
} catch {}

if ($collectorUp -and $policy -and $policy.collectorMs) {
    function Measure-CollectorHealthPerf($rule) {
        $warmup = if ($rule.warmup) { [int]$rule.warmup } else { 20 }
        for ($w = 0; $w -lt $warmup; $w++) {
            [void](Invoke-Api GET "$BaseCollector/collector/health" $null $null 10)
        }
        $latencies = New-Object System.Collections.Generic.List[int]
        $success = 0; $fail = 0
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        for ($i = 0; $i -lt $rule.requests; $i++) {
            $r = Invoke-Api GET "$BaseCollector/collector/health" $null $null 10
            [void]$latencies.Add([int]$r.ms)
            if ($r.ok -and $r.status -eq 200) { $success++ } else { $fail++ }
        }
        $sw.Stop()
        $stats = Build-LatencyStats $latencies
        return @{
            name = "collector-health"; mode = "serial"; requests = $rule.requests
            success = $success; fail = $fail; durationMs = $sw.ElapsedMilliseconds
            latencyP50 = $stats.p50; latencyP95 = $stats.p95; latencyP99 = $stats.p99
            latencyP999 = $stats.p999; latencyMax = $stats.max; latencyAvg = $stats.avg
        }
    }

    $cRule = $policy.collectorMs.'collector-health'
    $collectorPerf = Measure-CollectorHealthPerf $cRule
    $cLimit = [double]$cRule.p999
    $cOk = ($collectorPerf.fail -eq 0) -and ([double]$collectorPerf.latencyP999 -le $cLimit)
    if (-not $cOk) {
        Write-Host "collector-health retry after 30s cooldown ..." -ForegroundColor DarkYellow
        Start-Sleep -Seconds 30
        $collectorPerf = Measure-CollectorHealthPerf $cRule
        $cOk = ($collectorPerf.fail -eq 0) -and ([double]$collectorPerf.latencyP999 -le $cLimit)
    }
    $runtimePerf.Add($collectorPerf) | Out-Null
    Write-Host ("  collector p999={0}ms limit={1}ms fail={2}" -f $collectorPerf.latencyP999, $cLimit, $collectorPerf.fail) -ForegroundColor $(if ($cOk) { 'Green' } else { 'Red' })
    Add-Phase "collector-health-perf" $cOk ("p999=$($collectorPerf.latencyP999)ms fail=$($collectorPerf.fail)") $collectorPerf
} else {
    Add-Phase "collector-health-perf" $false $(if ($collectorUp) { "no-policy" } else { "Collector $BaseCollector/collector/health unreachable" }) $null
}

Write-FullPerfReport
