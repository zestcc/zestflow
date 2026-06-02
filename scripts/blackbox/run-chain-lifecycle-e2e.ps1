# 链全生命周期 E2E：创建设计 → 绑定链 → 发布 → Netty /execute
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$BaseNetty = "http://127.0.0.1:20550",
    [string]$AppCode = "demo-app",
    [switch]$AllowSkip,
    [switch]$SkipCleanup
)

$ErrorActionPreference = "Continue"

function Invoke-Api($method, $url, $body, $headers, $timeoutSec = 90) {
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

function Get-Data($json) {
    if ($null -eq $json) { return $null }
    if ($json.data) { return $json.data }
    return $json
}

Write-Host "=== Chain Lifecycle E2E ===" -ForegroundColor Cyan

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

$suffix = Get-Date -Format "HHmmss"
$graphObj = @{
    nodes = @(@{
        id = "n1"; label = "E2E"; type = "SCRIPT"
        script = "return { ok: true, e2e: 'lifecycle-$suffix' };"
    })
    edges = @()
}
$graphJson = ($graphObj | ConvertTo-Json -Compress -Depth 8)
$chainDataJson = '{"version":1,"entryNodeId":"n1"}'

$designBody = @{
    name = "E2E-Lifecycle-$suffix"
    description = "auto lifecycle e2e"
    appCode = $AppCode
    graphData = $graphJson
    chainData = $chainDataJson
} | ConvertTo-Json -Compress -Depth 8

$designResp = Invoke-Api POST "$BaseAdmin/api/designs" $designBody $h
if (-not $designResp.ok) {
    Write-Host "Create design failed status=$($designResp.status) body=$($designResp.body)" -ForegroundColor Red
    if ($AllowSkip) { exit 2 }
    exit 1
}
$designCode = $null
try {
    $dRoot = ConvertFrom-Json $designResp.body
    $dNode = Get-Data $dRoot
    if ($dNode.code) { $designCode = [string]$dNode.code }
} catch {}
if (-not $designCode) {
    Write-Host "Design code missing in response" -ForegroundColor Red
    if ($AllowSkip) { exit 2 }
    exit 1
}
Write-Host "Design created: $designCode"

$chainBody = @{
    name = "E2E-Chain-$suffix"
    description = "lifecycle e2e chain"
    appCode = $AppCode
    status = 2
} | ConvertTo-Json -Compress

$chainResp = Invoke-Api POST "$BaseAdmin/api/chains" $chainBody $h
if (-not $chainResp.ok) {
    Write-Host "Create chain failed status=$($chainResp.status)" -ForegroundColor Red
    if ($AllowSkip) { exit 2 }
    exit 1
}
$chainCode = $null
try {
    $cRoot = ConvertFrom-Json $chainResp.body
    $cNode = Get-Data $cRoot
    if (-not $cNode) { $cNode = $cRoot }
    if ($cNode.code) { $chainCode = [string]$cNode.code }
} catch {}
if (-not $chainCode) {
    Write-Host "Chain code missing" -ForegroundColor Red
    if ($AllowSkip) { exit 2 }
    exit 1
}
Write-Host "Chain created: $chainCode"

$bindBody = "{`"chainCode`":`"$chainCode`",`"appCode`":`"$AppCode`"}"
$bindResp = Invoke-Api POST "$BaseAdmin/api/designs/$designCode/bindings?appCode=$AppCode" $bindBody $h
$bindOk = $bindResp.ok
Write-Host "Bind design-chain status=$($bindResp.status) ok=$bindOk"

$publish = Invoke-Api POST "$BaseAdmin/api/chains/$chainCode/publish?appCode=$AppCode" $null $h 120
$publishOk = $false
$success = 0; $total = 0
if ($publish.ok) {
    try {
        $pubRoot = ConvertFrom-Json $publish.body
        $pubData = Get-Data $pubRoot
        $total = [int]$pubData.total
        $success = [int]$pubData.success
        $publishOk = ($total -gt 0) -and ($success -eq $total)
    } catch {}
}
Write-Host "Publish success=$success total=$total"

$execBody = "{`"chainCode`":`"$chainCode`",`"params`":{`"e2eTag`":`"lifecycle-$suffix`"}}"
$execResp = Invoke-Api POST "$BaseNetty/execute" $execBody $null 60
$execOk = $false
if ($execResp.ok) {
    try {
        $execJson = ConvertFrom-Json $execResp.body
        if ($execJson.success -eq $true -or $execJson.code -eq 200 -or $execJson.status -eq 3 -or $execJson.status -eq 1) {
            $execOk = $true
        }
    } catch {}
}
Write-Host "Execute status=$($execResp.status) ok=$execOk"

$activeOk = $false
$activeResp = Invoke-Api GET "$BaseAdmin/api/chains/active-codes?appCode=$AppCode" $null $h
if ($activeResp.ok) {
    try {
        $raw = ConvertFrom-Json $activeResp.body
        $list = @()
        if ($raw -is [System.Array]) { $list = @($raw) }
        elseif ($raw.data -is [System.Array]) { $list = @($raw.data) }
        $activeOk = $list -contains $chainCode
    } catch {}
}

if (-not $SkipCleanup) {
    Invoke-Api DELETE "$BaseAdmin/api/chains/$chainCode?appCode=$AppCode" $null $h | Out-Null
    Invoke-Api DELETE "$BaseAdmin/api/designs/$designCode?appCode=$AppCode" $null $h | Out-Null
}

$allOk = $bindOk -and $publishOk -and $execOk -and $activeOk
Write-Host "Checks: bind=$bindOk publish=$publishOk execute=$execOk active-codes=$activeOk" -ForegroundColor $(if ($allOk) { 'Green' } else { 'Red' })

if ($allOk) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
