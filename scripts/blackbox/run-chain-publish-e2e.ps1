# 链发布 / active-codes / 可选回滚 — Layer B 黑盒（需 Admin + Executor 在线）
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$AppCode = "demo-app",
    [switch]$SkipRollback,
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"

function Invoke-Api($method, $url, $body, $headers, $timeoutSec = 60) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $p = @{ Uri=$url; Method=$method; TimeoutSec=$timeoutSec; UseBasicParsing=$true }
        if ($headers) { $p.Headers = $headers }
        if ($null -ne $body) { $p.Body = $body; $p.ContentType = "application/json" }
        $r = Invoke-WebRequest @p
        $sw.Stop()
        return @{ status=[int]$r.StatusCode; ok=$true; ms=$sw.ElapsedMilliseconds; body=$r.Content }
    } catch {
        $sw.Stop()
        $st = 0; $b = ""
        if ($_.Exception.Response) {
            $st = [int]$_.Exception.Response.StatusCode.value__
            try { $rd = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); $b = $rd.ReadToEnd() } catch {}
        }
        return @{ status=$st; ok=$false; ms=$sw.ElapsedMilliseconds; body=$b }
    }
}

function Get-Records($json) {
    if ($null -eq $json) { return @() }
    if ($json.records) { return @($json.records) }
    if ($json.data -and $json.data.records) { return @($json.data.records) }
    return @()
}

function Get-Data($json) {
    if ($null -eq $json) { return $null }
    if ($json.data) { return $json.data }
    return $json
}

Write-Host "=== Chain Publish E2E ===" -ForegroundColor Cyan

$login = Invoke-Api POST "$BaseAdmin/api/auth/login" '{"username":"admin","password":"admin123"}' $null
if (-not $login.ok) {
    Write-Host "Login failed status=$($login.status)" -ForegroundColor Red
    if ($AllowSkip) { exit 2 }
    exit 1
}
$token = $null
try { $token = (ConvertFrom-Json $login.body).data.token } catch {}
if (-not $token) {
    Write-Host "Login token missing" -ForegroundColor Red
    if ($AllowSkip) { exit 2 }
    exit 1
}
$h = @{ Authorization = "Bearer $token" }

$list = Invoke-Api GET "$BaseAdmin/api/chains?appCode=$AppCode&page=1&size=50" $null $h
if (-not $list.ok) {
    Write-Host "List chains failed status=$($list.status)" -ForegroundColor Red
    if ($AllowSkip) { exit 2 }
    exit 1
}

$chains = Get-Records (ConvertFrom-Json $list.body)
$candidate = $chains | Where-Object { $_.designCode -and $_.code } | Sort-Object {
    if ($_.status -eq 2 -or $_.status -eq 3) { 0 } elseif ($_.status -eq 4) { 1 } else { 2 }
} | Select-Object -First 1

if (-not $candidate) {
    Write-Host "No chain with design binding found for appCode=$AppCode" -ForegroundColor Yellow
    if ($AllowSkip) { exit 2 }
    exit 1
}

$chainCode = [string]$candidate.code
Write-Host "Candidate chain: $chainCode status=$($candidate.status) design=$($candidate.designCode)"

$activeBefore = Invoke-Api GET "$BaseAdmin/api/chains/active-codes?appCode=$AppCode" $null $h
$activeSet = @()
if ($activeBefore.ok) {
    try {
        $raw = ConvertFrom-Json $activeBefore.body
        if ($raw -is [System.Array]) { $activeSet = @($raw) }
        elseif ($raw.data -is [System.Array]) { $activeSet = @($raw.data) }
    } catch {}
}

$publish = Invoke-Api POST "$BaseAdmin/api/chains/$chainCode/publish?appCode=$AppCode" $null $h 120
if (-not $publish.ok) {
    Write-Host "Publish HTTP failed status=$($publish.status) body=$($publish.body)" -ForegroundColor Red
    if ($AllowSkip) { exit 2 }
    exit 1
}

$pubData = $null
try {
    $pubRoot = ConvertFrom-Json $publish.body
    $pubData = Get-Data $pubRoot
} catch {
    Write-Host "Publish response parse failed" -ForegroundColor Red
    if ($AllowSkip) { exit 2 }
    exit 1
}

$total = [int]$pubData.total
$success = [int]$pubData.success
$publishOk = ($total -gt 0) -and ($success -eq $total)
Write-Host "Publish result success=$success total=$total publishId=$($pubData.publishId)"

$activeAfter = Invoke-Api GET "$BaseAdmin/api/chains/active-codes?appCode=$AppCode" $null $h
$activeAfterSet = @()
if ($activeAfter.ok) {
    try {
        $raw = ConvertFrom-Json $activeAfter.body
        if ($raw -is [System.Array]) { $activeAfterSet = @($raw) }
        elseif ($raw.data -is [System.Array]) { $activeAfterSet = @($raw.data) }
    } catch {}
}
$activeOk = $activeAfterSet -contains $chainCode

$detail = Invoke-Api GET "$BaseAdmin/api/chains/$chainCode?appCode=$AppCode" $null $h
$statusOk = $false
if ($detail.ok) {
    try {
        $d = ConvertFrom-Json $detail.body
        $node = if ($d.data) { $d.data } else { $d }
        if ($node.status -eq 4) { $statusOk = $true }
    } catch {}
}

$rollbackOk = $true
if (-not $SkipRollback) {
    $versions = Invoke-Api GET "$BaseAdmin/api/chains/$chainCode/versions?appCode=$AppCode" $null $h
    $targetVersion = $null
    if ($versions.ok) {
        try {
            $vRoot = ConvertFrom-Json $versions.body
            $vList = @()
            if ($vRoot -is [System.Array]) { $vList = @($vRoot) }
            elseif ($vRoot.records) { $vList = @($vRoot.records) }
            elseif ($vRoot.data -is [System.Array]) { $vList = @($vRoot.data) }
            if ($vList.Count -ge 2) {
                $sorted = $vList | Sort-Object { [int]$_.version }
                $targetVersion = [int]$sorted[0].version
            }
        } catch {}
    }
    if ($null -ne $targetVersion) {
        $rbBody = "{`"appCode`":`"$AppCode`"}"
        $rollback = Invoke-Api POST "$BaseAdmin/api/chains/$chainCode/rollback/$targetVersion" $rbBody $h 60
        $rollbackOk = $rollback.ok
        Write-Host "Rollback to version $targetVersion status=$($rollback.status)"
        # 回滚后再次发布，恢复 demo 环境
        $republish = Invoke-Api POST "$BaseAdmin/api/chains/$chainCode/publish?appCode=$AppCode" $null $h 120
        if (-not $republish.ok) {
            Write-Host "Republish after rollback failed" -ForegroundColor Yellow
            $rollbackOk = $false
        }
    } else {
        Write-Host "Rollback skipped: fewer than 2 versions" -ForegroundColor DarkGray
    }
}

$allOk = $publishOk -and $activeOk -and $statusOk -and $rollbackOk
Write-Host "Checks: publish=$publishOk active-codes=$activeOk status=4=$statusOk rollback=$rollbackOk" -ForegroundColor $(if ($allOk) { 'Green' } else { 'Red' })

if ($allOk) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
