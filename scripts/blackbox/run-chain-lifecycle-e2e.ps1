# ?????? E2E???????????????? ??Netty /execute
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

function Resolve-EntityCode($root) {
    if ($null -eq $root) { return $null }
    if ($root.PSObject.Properties['code'] -and [string]$root.code -match '^\d+$') {
        if ([int]$root.code -ne 200) { return $null }
    }
    $node = Get-Data $root
    if (-not $node) { $node = $root }
    if ($node.PSObject.Properties['code']) {
        $c = [string]$node.code
        if ($c -match '^\d+$') { return $null }
        return $c
    }
    return $null
}

Write-Host "=== Chain Lifecycle E2E ===" -ForegroundColor Cyan

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

$suffix = Get-Date -Format "HHmmss"
$graphObj = @{
    nodes = @(@{
        id = "n1"; label = "E2E"; type = "SCRIPT"
        script = "seq.map('ok', true, 'e2e', 'lifecycle-$suffix')"
    })
    edges = @()
}
$graphJson = ($graphObj | ConvertTo-Json -Compress -Depth 8)
$chainDataJson = '{"version":1,"nodes":[{"id":"n1","label":"E2E","type":"SCRIPT","script":"true"}],"edges":[]}'

$designBody = @{
    name = "E2E-Lifecycle-$suffix"
    description = "auto lifecycle e2e"
    appCode = $AppCode
    graphData = $graphJson
    chainData = $chainDataJson
} | ConvertTo-Json -Compress -Depth 8

$designCode = $null
for ($attempt = 1; $attempt -le 3; $attempt++) {
    $designResp = Invoke-Api POST "$BaseAdmin/api/zestflow/designs" $designBody $h
    if (-not $designResp.ok) {
        Write-Host "Create design HTTP failed status=$($designResp.status) attempt=$attempt" -ForegroundColor Yellow
        Start-Sleep -Seconds 2
        continue
    }
    try {
        $dRoot = ConvertFrom-Json $designResp.body
        $designCode = Resolve-EntityCode $dRoot
        if (-not $designCode -and $dRoot.code -match '^\d+$' -and [int]$dRoot.code -ne 200) {
            Write-Host "Create design business error code=$($dRoot.code) msg=$($dRoot.message) attempt=$attempt" -ForegroundColor Yellow
        }
    } catch {}
    if ($designCode) { break }
    Start-Sleep -Seconds 2
}
if (-not $designCode) {
    Write-Host "Design code missing or create failed after retries" -ForegroundColor Red
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

$chainCode = $null
for ($attempt = 1; $attempt -le 3; $attempt++) {
    $chainResp = Invoke-Api POST "$BaseAdmin/api/zestflow/chains" $chainBody $h
    if (-not $chainResp.ok) {
        Write-Host "Create chain HTTP failed status=$($chainResp.status) attempt=$attempt" -ForegroundColor Yellow
        Start-Sleep -Seconds 2
        continue
    }
    try {
        $cRoot = ConvertFrom-Json $chainResp.body
        $chainCode = Resolve-EntityCode $cRoot
        if (-not $chainCode -and $cRoot.code -match '^\d+$' -and [int]$cRoot.code -ne 200) {
            Write-Host "Create chain business error code=$($cRoot.code) msg=$($cRoot.message) attempt=$attempt" -ForegroundColor Yellow
        }
    } catch {}
    if ($chainCode) { break }
    Start-Sleep -Seconds 2
}
if (-not $chainCode) {
    Write-Host "Chain code missing or create failed after retries" -ForegroundColor Red
    if ($AllowSkip) { exit 2 }
    exit 1
}
Write-Host "Chain created: $chainCode"

$bindBody = "{`"chainCode`":`"$chainCode`",`"appCode`":`"$AppCode`"}"
$bindResp = Invoke-Api POST "$BaseAdmin/api/zestflow/designs/$designCode/bindings?appCode=$AppCode" $bindBody $h
$bindOk = $bindResp.ok
Write-Host "Bind design-chain status=$($bindResp.status) ok=$bindOk"

$publish = Invoke-Api POST "$BaseAdmin/api/zestflow/chains/$chainCode/publish?appCode=$AppCode" $null $h 120
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
        $st = 0
        if ($execJson.PSObject.Properties['status']) { $st = [int]$execJson.status }
        if ($execJson.success -eq $true -or $execJson.code -eq 200 -or $st -eq 4 -or $st -eq 1 -or $st -eq 3) {
            $execOk = $true
        }
    } catch {}
}
Write-Host "Execute status=$($execResp.status) ok=$execOk"

$activeOk = $false
$activeResp = Invoke-Api GET "$BaseAdmin/api/zestflow/chains/active-codes?appCode=$AppCode" $null $h
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
    Invoke-Api DELETE "$BaseAdmin/api/zestflow/chains/$chainCode?appCode=$AppCode" $null $h | Out-Null
    Invoke-Api DELETE "$BaseAdmin/api/zestflow/designs/$designCode?appCode=$AppCode" $null $h | Out-Null
}

$allOk = $bindOk -and $publishOk -and $execOk -and $activeOk
Write-Host "Checks: bind=$bindOk publish=$publishOk execute=$execOk active-codes=$activeOk" -ForegroundColor $(if ($allOk) { 'Green' } else { 'Red' })

if ($allOk) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
