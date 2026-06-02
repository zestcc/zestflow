# registry-token + executor-access-token 成对 E2E（需 security-e2e profile 重启 Admin + Executor）
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$BaseNetty = "http://127.0.0.1:20550",
    [string]$RegistryToken = "e2e-security-registry-token",
    [string]$ExecutorAccessToken = "e2e-security-executor-token",
    [string]$RegistryPath = "/api/registry/executor/heartbeat",
    [switch]$AllowSkip,
    [switch]$SkipExecutorTests
)

$ErrorActionPreference = "Stop"

function Invoke-Http($method, $url, $body, $headers, $timeoutSec = 20) {
    try {
        $p = @{ Uri=$url; Method=$method; TimeoutSec=$timeoutSec; UseBasicParsing=$true }
        if ($headers) { $p.Headers = $headers }
        if ($null -ne $body) { $p.Body = $body; $p.ContentType = "application/json" }
        $r = Invoke-WebRequest @p
        return @{ status=[int]$r.StatusCode; ok=$true; body=$r.Content }
    } catch {
        $st = 0
        if ($_.Exception.Response) {
            $st = [int]$_.Exception.Response.StatusCode.value__
        }
        return @{ status=$st; ok=$false; body="" }
    }
}

function Get-SecurityFeatures {
    $login = Invoke-Http POST "$BaseAdmin/api/auth/login" '{"username":"admin","password":"admin123"}' $null
    if (-not $login.ok) { return $null }
    $token = $null
    try { $token = (ConvertFrom-Json $login.body).data.token } catch {}
    if (-not $token) { return $null }
    $h = @{ Authorization = "Bearer $token" }
    $feat = Invoke-Http GET "$BaseAdmin/api/system/features" $null $h
    if (-not $feat.ok) { return $null }
    try {
        return @{
            headers = $h
            json = (ConvertFrom-Json $feat.body)
        }
    } catch {
        return $null
    }
}

Write-Host "=== Security Token E2E ===" -ForegroundColor Cyan

$probe = Get-SecurityFeatures
if ($null -eq $probe) {
    Write-Host "Cannot probe /api/system/features (Admin down or login failed)" -ForegroundColor Red
    if ($AllowSkip) { exit 2 }
    exit 1
}

$registryOn = $false
$executorTokenOn = $false
if ($probe.json.security) {
    $registryOn = [bool]$probe.json.security.registryTokenConfigured
    if ($probe.json.security.PSObject.Properties.Name -contains 'executorAccessTokenConfigured') {
        $executorTokenOn = [bool]$probe.json.security.executorAccessTokenConfigured
    }
}

if (-not $registryOn) {
    Write-Host "registry-token 未开启（非 security-e2e profile）" -ForegroundColor Yellow
    Write-Host "重启: Admin/Executor 加 -Dspring-boot.run.profiles=local,security-e2e" -ForegroundColor Yellow
    if ($AllowSkip) { exit 2 }
    exit 1
}

# --- Registry token ---
function Invoke-Registry($token) {
    $headers = @{}
    if ($token) { $headers["X-Registry-Token"] = $token }
    $body = '{"executorId":"e2e-security-probe","executorHost":"127.0.0.1","executorPort":20550,"appCode":"demo-app","appName":"Demo"}'
    $r = Invoke-Http POST "$BaseAdmin$RegistryPath" $body $headers
    return $r.status
}

$noReg = Invoke-Registry ""
$badReg = Invoke-Registry "wrong-registry-token"
$okReg = Invoke-Registry $RegistryToken
Write-Host "registry no-token=$noReg wrong=$badReg valid=$okReg (expect 401/401/2xx)"
$registryOk = ($noReg -eq 401) -and ($badReg -eq 401) -and ($okReg -ge 200 -and $okReg -lt 300)

# --- Executor access token ---
$executorOk = $true
if (-not $SkipExecutorTests) {
    if (-not $executorTokenOn) {
        Write-Host "executor-access-token 未在 Admin 配置，跳过 Netty 探测" -ForegroundColor Yellow
        $executorOk = $false
    } else {
        function Invoke-Netty($path, $token) {
            $headers = @{}
            if ($token) { $headers["X-Access-Token"] = $token }
            $r = Invoke-Http GET "$BaseNetty$path" $null $headers 10
            return $r.status
        }
        $health = Invoke-Netty "/health" $null
        $noExec = Invoke-Netty "/api/chains" $null
        $badExec = Invoke-Netty "/api/chains" "wrong-executor-token"
        $okExec = Invoke-Netty "/api/chains" $ExecutorAccessToken
        Write-Host "netty health=$health (expect 200)"
        Write-Host "netty no-token=$noExec wrong=$badExec valid=$okExec (expect 401/401/200)"
        $executorOk = ($health -eq 200) -and ($noExec -eq 401) -and ($badExec -eq 401) -and ($okExec -eq 200)
    }
}

$pass = $registryOk -and $executorOk
if ($pass) {
    Write-Host "PASS" -ForegroundColor Green
    exit 0
}
Write-Host "FAIL registry=$registryOk executor=$executorOk" -ForegroundColor Red
if ($AllowSkip) { exit 2 }
exit 1
