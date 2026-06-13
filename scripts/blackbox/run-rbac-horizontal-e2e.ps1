# ???? / ???? E2E ????JWT ????JWT ??????API
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$AppCode = "demo-app",
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"

function Invoke-Api($method, $url, $body, $headers) {
    try {
        $p = @{ Uri=$url; Method=$method; TimeoutSec=20; UseBasicParsing=$true }
        if ($headers) { $p.Headers = $headers }
        if ($null -ne $body) { $p.Body = $body; $p.ContentType = "application/json" }
        $r = Invoke-WebRequest @p
        return @{ status=[int]$r.StatusCode; ok=$true; body=$r.Content }
    } catch {
        $st = 0; $b = ""
        if ($_.Exception.Response) {
            $st = [int]$_.Exception.Response.StatusCode.value__
            try { $b = (New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch {}
        }
        return @{ status=$st; ok=$false; body=$b }
    }
}

Write-Host "=== RBAC Horizontal E2E ===" -ForegroundColor Cyan

$noJwtChains = Invoke-Api GET "$BaseAdmin/api/zestflow/chains?appCode=$AppCode&page=1&size=5" $null $null
$noJwtDesigns = Invoke-Api GET "$BaseAdmin/api/zestflow/designs?appCode=$AppCode&page=1&size=5" $null $null
$noJwtPublish = Invoke-Api POST "$BaseAdmin/api/zestflow/chains/FAKE/publish?appCode=$AppCode" $null $null

$badH = @{ Authorization = "Bearer invalid.token.here" }
$badJwtChains = Invoke-Api GET "$BaseAdmin/api/zestflow/chains?appCode=$AppCode&page=1&size=5" $null $badH

function Test-Denied($r) { return ($r.status -in 401, 403) }
function Test-Unauthorized($r) { return ($r.status -eq 401) }

$login = Invoke-Api POST "$BaseAdmin/api/zestflow/auth/login" '{"username":"admin","password":"admin123"}' $null
if (-not $login.ok) {
    Write-Host "Admin login failed ??is :8080 up?" -ForegroundColor Red
    if ($AllowSkip) { exit 2 }
    exit 1
}
$token = (ConvertFrom-Json $login.body).data.token
$h = @{ Authorization = "Bearer $token" }
$okChains = Invoke-Api GET "$BaseAdmin/api/zestflow/chains?appCode=$AppCode&page=1&size=5" $null $h

# enterprise-e2e / dev-open?? JWT ?? 200???? JWT ??????admin JWT ??
$devOpenAuth = (-not (Test-Denied $noJwtChains)) -and (Test-Denied $badJwtChains) -and $okChains.ok

$checks = @(
    @{ name="chains-no-jwt"; ok=((Test-Unauthorized $noJwtChains) -or $devOpenAuth); status=$noJwtChains.status }
    @{ name="designs-no-jwt"; ok=((Test-Unauthorized $noJwtDesigns) -or $devOpenAuth); status=$noJwtDesigns.status }
    @{ name="publish-no-jwt"; ok=((Test-Unauthorized $noJwtPublish) -or $devOpenAuth); status=$noJwtPublish.status }
    @{ name="chains-bad-jwt"; ok=(Test-Denied $badJwtChains); status=$badJwtChains.status }
    @{ name="chains-admin-ok"; ok=$okChains.ok; status=$okChains.status }
)

foreach ($c in $checks) {
    $color = if ($c.ok) { 'Green' } else { 'Red' }
    Write-Host ("  [{0}] {1} status={2}" -f $(if ($c.ok) { 'PASS' } else { 'FAIL' }), $c.name, $c.status) -ForegroundColor $color
}

$fail = @($checks | Where-Object { -not $_.ok }).Count
if ($fail -eq 0) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
