# AI Copilot 性能 / 压测（validate + RAG；可选 Mock LLM explain）
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [int]$ValidateConcurrency = 20,
    [int]$ValidateIterations = 50,
    [int]$RagConcurrency = 10,
    [int]$RagIterations = 30,
    [int]$ExplainConcurrency = 5,
    [int]$ExplainIterations = 10,
    [switch]$UseMockLlm,
    [int]$MockLlmPort = 18766,
    [int]$ValidateP95Ms = 2000,
    [int]$RagP95Ms = 3000,
    [int]$ExplainP95Ms = 5000
)

$ErrorActionPreference = "Continue"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("ai-copilot-perf-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$checks = New-Object System.Collections.Generic.List[object]

function Add-Check($name, $ok, $note, $metrics) {
    $checks.Add([pscustomobject]@{ name = $name; ok = $ok; note = $note; metrics = $metrics }) | Out-Null
}
function Save-Report {
    $out = @{ timestamp = (Get-Date).ToString("o"); checks = $checks }
    Set-Content -Path $ReportJson -Value ($out | ConvertTo-Json -Depth 8) -Encoding UTF8
    Write-Host "Saved: $ReportJson"
}

function Invoke-TimedRequest($method, $url, $body, $headers, [int]$TimeoutSec = 60) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $p = @{ Uri = $url; Method = $method; TimeoutSec = $TimeoutSec; UseBasicParsing = $true }
        if ($headers) { $p.Headers = $headers }
        if ($null -ne $body) { $p.Body = $body; $p.ContentType = "application/json" }
        $r = Invoke-WebRequest @p
        $sw.Stop()
        return @{ ok = $true; ms = $sw.ElapsedMilliseconds; status = [int]$r.StatusCode }
    } catch {
        $sw.Stop()
        return @{ ok = $false; ms = $sw.ElapsedMilliseconds; status = 0 }
    }
}

function Run-LoadTest($name, $concurrency, $iterations, $scriptBlock) {
    $jobs = @()
    $perWorker = [Math]::Ceiling($iterations / [double]$concurrency)
    for ($i = 0; $i -lt $concurrency; $i++) {
        $jobs += Start-Job -ScriptBlock $scriptBlock -ArgumentList $perWorker
    }
    $allMs = New-Object System.Collections.Generic.List[int]
    $errors = 0
    $jobs | Wait-Job | ForEach-Object {
        $r = Receive-Job $_
        Remove-Job $_
        $errors += $r.errors
        foreach ($m in $r.ms) { $allMs.Add([int]$m) | Out-Null }
    }
    $sorted = $allMs | Sort-Object
    $count = $sorted.Count
    if ($count -eq 0) {
        return @{ ok = $false; note = "no samples"; metrics = $null }
    }
    $p50 = $sorted[[int][Math]::Floor($count * 0.50)]
    $p95 = $sorted[[int][Math]::Floor($count * 0.95)]
    $p99 = $sorted[[int][Math]::Min($count - 1, [int][Math]::Floor($count * 0.99))]
    $errRate = $errors / [double]$count
    $metrics = @{
        samples = $count; errors = $errors; errorRate = [Math]::Round($errRate, 4)
        p50Ms = $p50; p95Ms = $p95; p99Ms = $p99
    }
    return @{ ok = ($errRate -eq 0); note = "p95=${p95}ms err=$errors/$count"; metrics = $metrics }
}

Write-Host "=== AI Copilot Perf ===" -ForegroundColor Cyan

$login = Invoke-TimedRequest POST "$BaseAdmin/api/zestflow/auth/login" '{"username":"admin","password":"admin123"}' $null
if (-not $login.ok) {
    Add-Check "perf-login" $false "status=$($login.status)" $null
    Save-Report
    exit 1
}
$token = (ConvertFrom-Json (Invoke-WebRequest -Uri "$BaseAdmin/api/zestflow/auth/login" -Method POST -Body '{"username":"admin","password":"admin123"}' -ContentType "application/json" -UseBasicParsing).Content).data.token
$h = @{ Authorization = "Bearer $token" }
Add-Check "perf-login" $true "" @{ ms = $login.ms }

$mockProc = $null
if ($UseMockLlm) {
    $mockProc = Start-Process powershell -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $PSScriptRoot "mock-llm-server.ps1"),
        "-Port", $MockLlmPort
    ) -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 2
    $mockCfg = (@{
        enabled = $true; preset = 'custom'
        baseUrl = "http://127.0.0.1:$MockLlmPort/v1"
        apiKey = 'mock-perf'; model = 'mock-perf'
    } | ConvertTo-Json -Compress)
    Invoke-TimedRequest PUT "$BaseAdmin/api/zestflow/ai/tenant-config" $mockCfg $h | Out-Null
}

$validateBody = '{"appCode":"demo-app","chainCode":"PERF","chainData":"{\"code\":\"PERF\",\"version\":1,\"nodes\":[{\"id\":\"n1\",\"label\":\"t\",\"type\":\"TASK\",\"component\":\"validateUser\"}],\"edges\":[]}"}'
$valBlock = {
    param($n)
    $msList = @(); $err = 0
    $base = $using:BaseAdmin
    $body = $using:validateBody
    $hdr = $using:h
    for ($i = 0; $i -lt $n; $i++) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            Invoke-WebRequest -Uri "$base/api/zestflow/ai/design/validate" -Method POST -Body $body -Headers $hdr -ContentType "application/json" -UseBasicParsing -TimeoutSec 60 | Out-Null
            $sw.Stop(); $msList += $sw.ElapsedMilliseconds
        } catch { $sw.Stop(); $err++; $msList += $sw.ElapsedMilliseconds }
    }
    return @{ ms = $msList; errors = $err }
}
$valPerf = Run-LoadTest "validate" $ValidateConcurrency $ValidateIterations $valBlock
$valPass = $valPerf.ok -and ($valPerf.metrics.p95Ms -le $ValidateP95Ms)
Add-Check "perf-validate-load" $valPass $valPerf.note $valPerf.metrics

$ragBlock = {
    param($n)
    $msList = @(); $err = 0
    $base = $using:BaseAdmin
    $hdr = $using:h
    for ($i = 0; $i -lt $n; $i++) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            Invoke-WebRequest -Uri "$base/api/zestflow/ai/rag/search?q=Aviator+chainCtx&limit=2" -Method GET -Headers $hdr -UseBasicParsing -TimeoutSec 60 | Out-Null
            $sw.Stop(); $msList += $sw.ElapsedMilliseconds
        } catch { $sw.Stop(); $err++; $msList += $sw.ElapsedMilliseconds }
    }
    return @{ ms = $msList; errors = $err }
}
$ragPerf = Run-LoadTest "rag" $RagConcurrency $RagIterations $ragBlock
$ragPass = $ragPerf.ok -and ($ragPerf.metrics.p95Ms -le $RagP95Ms)
Add-Check "perf-rag-search-load" $ragPass $ragPerf.note $ragPerf.metrics

if ($UseMockLlm) {
    $chainCtx = '{"code":"PERF","version":1,"nodes":[{"id":"n1","label":"t","type":"TASK","component":"validateUser"}],"edges":[]}'
    $explainBody = (@{
        appCode = "demo-app"; designId = "perf"; chainCode = "PERF"
        chainData = $chainCtx; userMessage = "explain"
    } | ConvertTo-Json -Compress -Depth 6)
    $explainBlock = {
        param($n)
        $msList = @(); $err = 0
        $base = $using:BaseAdmin
        $body = $using:explainBody
        $hdr = $using:h
        for ($i = 0; $i -lt $n; $i++) {
            $sw = [System.Diagnostics.Stopwatch]::StartNew()
            try {
                Invoke-WebRequest -Uri "$base/api/zestflow/ai/design/explain" -Method POST -Body $body -Headers $hdr -ContentType "application/json" -UseBasicParsing -TimeoutSec 120 | Out-Null
                $sw.Stop(); $msList += $sw.ElapsedMilliseconds
            } catch { $sw.Stop(); $err++; $msList += $sw.ElapsedMilliseconds }
        }
        return @{ ms = $msList; errors = $err }
    }
    $expPerf = Run-LoadTest "explain" $ExplainConcurrency $ExplainIterations $explainBlock
    $expPass = $expPerf.ok -and ($expPerf.metrics.p95Ms -le $ExplainP95Ms)
    Add-Check "perf-explain-mock-llm" $expPass $expPerf.note $expPerf.metrics
}

if ($mockProc -and -not $mockProc.HasExited) {
    Stop-Process -Id $mockProc.Id -Force -ErrorAction SilentlyContinue
}

$fail = @($checks | Where-Object { -not $_.ok }).Count
Write-Host "Checks: $($checks.Count - $fail)/$($checks.Count) passed" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
Save-Report
if ($fail -gt 0) { exit 1 }
exit 0
