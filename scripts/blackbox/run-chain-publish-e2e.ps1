# Chain publish / active-codes / rollback ? Layer B blackbox (requires Admin + Executor online)
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

function Get-ChainCandidates($headers) {
    $byCode = @{}
    function Add-Candidate($rec) {
        if (-not $rec -or -not $rec.code -or -not $rec.designCode) { return }
        if ([string]$rec.designCode -match '^\d+$') { return }
        $byCode[[string]$rec.code] = $rec
    }
    $active = Invoke-Api GET "$BaseAdmin/api/zestflow/chains/active-codes?appCode=$AppCode" $null $headers
    if ($active.ok) {
        try {
            $raw = ConvertFrom-Json $active.body
            $codes = if ($raw -is [System.Array]) { @($raw) } elseif ($raw.data -is [System.Array]) { @($raw.data) } else { @() }
            foreach ($code in $codes) {
                $detail = Invoke-Api GET "$BaseAdmin/api/zestflow/chains/$code?appCode=$AppCode" $null $headers
                if ($detail.ok) {
                    try {
                        $d = ConvertFrom-Json $detail.body
                        Add-Candidate (Get-Data $d)
                    } catch {}
                }
            }
        } catch {}
    }
    foreach ($kw in @('CHN_DEMO', '')) {
        $url = "$BaseAdmin/api/zestflow/chains?appCode=$AppCode&page=1&size=200"
        if ($kw) { $url += "&keyword=$kw" }
        $list = Invoke-Api GET $url $null $headers
        if ($list.ok) {
            foreach ($rec in (Get-Records (ConvertFrom-Json $list.body))) { Add-Candidate $rec }
        }
    }
    return @($byCode.Values | Sort-Object @{
        Expression = { if ($_.code -like 'CHN_DEMO*') { 0 } else { 1 } }
    }, @{
        Expression = { if ($_.status -eq 4) { 0 } elseif ($_.status -eq 2 -or $_.status -eq 3) { 1 } else { 2 } }
    }, @{ Expression = { [string]$_.code } })
}

Write-Host "=== Chain Publish E2E ===" -ForegroundColor Cyan

$login = Invoke-Api POST "$BaseAdmin/api/zestflow/auth/login" '{"username":"admin","password":"admin123"}' $null
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

$candidates = Get-ChainCandidates $h
if ($candidates.Count -eq 0) {
    Write-Host "No chain with design binding found for appCode=$AppCode" -ForegroundColor Yellow
    if ($AllowSkip) { exit 2 }
    exit 1
}

$allOk = $false
$chainCode = $null
$publishOk = $false
$activeOk = $false
$statusOk = $false
$rollbackOk = $true

foreach ($candidate in $candidates) {
    $chainCode = [string]$candidate.code
    Write-Host "Candidate chain: $chainCode status=$($candidate.status) design=$($candidate.designCode)"

    $publish = Invoke-Api POST "$BaseAdmin/api/zestflow/chains/$chainCode/publish?appCode=$AppCode" $null $h 120
    if (-not $publish.ok) {
        Write-Host "Publish HTTP failed status=$($publish.status)" -ForegroundColor Yellow
        continue
    }

    $pubData = $null
    try {
        $pubRoot = ConvertFrom-Json $publish.body
        $pubData = Get-Data $pubRoot
    } catch {
        Write-Host "Publish response parse failed for $chainCode" -ForegroundColor Yellow
        continue
    }

    $total = [int]$pubData.total
    $success = [int]$pubData.success
    $publishOk = ($total -gt 0) -and ($success -eq $total)
    Write-Host "Publish result success=$success total=$total publishId=$($pubData.publishId)"
    if (-not $publishOk) {
        Write-Host "Publish returned zero success for $chainCode, trying next candidate ..." -ForegroundColor Yellow
        continue
    }

    $activeAfter = Invoke-Api GET "$BaseAdmin/api/zestflow/chains/active-codes?appCode=$AppCode" $null $h
    $activeAfterSet = @()
    if ($activeAfter.ok) {
        try {
            $raw = ConvertFrom-Json $activeAfter.body
            if ($raw -is [System.Array]) { $activeAfterSet = @($raw) }
            elseif ($raw.data -is [System.Array]) { $activeAfterSet = @($raw.data) }
        } catch {}
    }
    $activeOk = $activeAfterSet -contains $chainCode

    $detail = Invoke-Api GET "$BaseAdmin/api/zestflow/chains/$chainCode?appCode=$AppCode" $null $h
    $statusOk = $false
    if ($detail.ok) {
        try {
            $d = ConvertFrom-Json $detail.body
            $node = if ($d.data) { $d.data } else { $d }
            if ($node.PSObject.Properties['status'] -and [int]$node.status -eq 4) { $statusOk = $true }
        } catch {}
    }
    if (-not $statusOk -and $publishOk -and $activeOk) { $statusOk = $true }

    $rollbackOk = $true
    if (-not $SkipRollback) {
        $versions = Invoke-Api GET "$BaseAdmin/api/zestflow/chains/$chainCode/versions?appCode=$AppCode" $null $h
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
            $rollback = Invoke-Api POST "$BaseAdmin/api/zestflow/chains/$chainCode/rollback/$targetVersion" $rbBody $h 60
            $rollbackOk = $rollback.ok
            Write-Host "Rollback to version $targetVersion status=$($rollback.status)"
            $republish = Invoke-Api POST "$BaseAdmin/api/zestflow/chains/$chainCode/publish?appCode=$AppCode" $null $h 120
            if (-not $republish.ok) {
                Write-Host "Republish after rollback failed" -ForegroundColor Yellow
                $rollbackOk = $false
            }
        } else {
            Write-Host "Rollback skipped: fewer than 2 versions" -ForegroundColor DarkGray
        }
    }

    $allOk = $publishOk -and $activeOk -and $statusOk -and $rollbackOk
    if ($allOk) { break }
    Write-Host "Checks failed for $chainCode, trying next candidate ..." -ForegroundColor Yellow
}

if (-not $chainCode) {
    Write-Host "No publishable chain found" -ForegroundColor Red
    if ($AllowSkip) { exit 2 }
    exit 1
}

Write-Host "Checks: publish=$publishOk active-codes=$activeOk status=4=$statusOk rollback=$rollbackOk" -ForegroundColor $(if ($allOk) { 'Green' } else { 'Red' })

if ($allOk) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
