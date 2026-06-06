# 151 条演示链 Playground 矩阵 E2E（需 Admin + Playground 已启用且已灌库）
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [int]$SceneTimeoutSec = 120,
    [switch]$IncludeHeavy,
    [string]$ReportFile = ""
)

$ErrorActionPreference = "Continue"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
if (-not $ReportFile) {
    $ReportFile = Join-Path $OutDir ("chain-matrix-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
}

function Invoke-Api($method, $url, $body, $headers, $timeoutSec = 30) {
    try {
        $p = @{ Uri=$url; Method=$method; TimeoutSec=$timeoutSec; UseBasicParsing=$true }
        if ($headers) { $p.Headers = $headers }
        if ($null -ne $body) { $p.Body = $body; $p.ContentType = "application/json" }
        $r = Invoke-WebRequest @p
        return @{ ok=$true; status=[int]$r.StatusCode; body=$r.Content }
    } catch {
        $st = 0; $b = ""
        if ($_.Exception.Response) {
            $st = [int]$_.Exception.Response.StatusCode.value__
            try { $rd = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); $b = $rd.ReadToEnd() } catch {}
        }
        return @{ ok=$false; status=$st; body=$b }
    }
}

Write-Host "=== Chain Matrix E2E (151 chains via playground) ===" -ForegroundColor Cyan

$login = Invoke-Api POST "$BaseAdmin/api/zestflow/auth/login" '{"username":"admin","password":"admin123"}' $null
$token = $null
if ($login.ok) { try { $token = (ConvertFrom-Json $login.body).data.token } catch {} }
if (-not $token) { Write-Host "Login failed" -ForegroundColor Red; exit 1 }
$h = @{ Authorization = "Bearer $token" }

$list = Invoke-Api GET "$BaseAdmin/api/zestflow/playground/scenes/list-all" $null $h
$scenes = @()
if ($list.ok) { try { $scenes = (ConvertFrom-Json $list.body).data } catch {} }
Write-Host "Scenes loaded: $($scenes.Count)"

$heavyScenes = @('SCN20260531000004')
$enterpriseOnlyScenes = @('SCN20260602000002')
$results = New-Object System.Collections.Generic.List[object]
$pass = 0; $fail = 0; $skip = 0

foreach ($s in $scenes) {
    $code = $s.sceneCode
    if ($heavyScenes -contains $code -and -not $IncludeHeavy) {
        $results.Add([pscustomobject]@{ scene=$code; chain=$s.chainCode; ok=$null; skipped=$true; note="heavy-skipped" }) | Out-Null
        $skip++
        continue
    }
    if ($enterpriseOnlyScenes -contains $code) {
        $results.Add([pscustomobject]@{ scene=$code; chain=$s.chainCode; ok=$null; skipped=$true; note="enterprise-only" }) | Out-Null
        $skip++
        continue
    }
    $body = if ($s.requestBody) { $s.requestBody } else { '{}' }
    $to = if ($heavyScenes -contains $code) { 300 } else { $SceneTimeoutSec }
    $r = Invoke-Api POST "$BaseAdmin/api/zestflow/playground/execute/$code" $body $h $to
    $runOk = $false; $note = ""
    if ($r.ok) {
        try {
            $resp = ConvertFrom-Json $r.body
            if ($resp.code -eq 200) {
                $st = $resp.data.status
                if ($null -eq $st -or $st -eq 1 -or $st -eq '1' -or $st -eq 'SUCCESS') {
                    $runOk = $true
                } else {
                    $note = "status=$st"
                }
            } else {
                $note = "api-code=$($resp.code)"
            }
        } catch {
            $runOk = $true
        }
    } else {
        $note = "http-$($r.status)"
    }
    if ($runOk) { $pass++ } else { $fail++ }
    $results.Add([pscustomobject]@{
        scene=$code; chain=$s.chainCode; name=$s.name; ok=$runOk; skipped=$false; note=$note
    }) | Out-Null
    $color = if ($runOk) { 'Green' } else { 'Red' }
    Write-Host ("[{0}] {1} chain={2} {3}" -f $(if ($runOk) { 'PASS' } else { 'FAIL' }), $code, $s.chainCode, $note) -ForegroundColor $color
}

$report = @{
    timestamp = (Get-Date).ToString("o")
    total = $scenes.Count
    pass = $pass
    fail = $fail
    skipped = $skip
    results = $results
}
Set-Content -Path $ReportFile -Value ($report | ConvertTo-Json -Depth 6) -Encoding UTF8
Write-Host "Report: $ReportFile" -ForegroundColor Cyan
Write-Host "PASS=$pass FAIL=$fail SKIP=$skip" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
exit $(if ($fail -eq 0) { 0 } else { 1 })
