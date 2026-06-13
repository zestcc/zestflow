# ZestFlow Back-Channel Logout E2E
param(
    [string]$SsoUrl = "http://localhost:9000",
    [string]$ZfUrl = "http://localhost:8080",
    [string]$Username = "admin",
    [string]$Password = "admin123"
)

$ErrorActionPreference = "Stop"
$passed = 0
$failed = 0

function Pass([string]$Name) {
    Write-Host "[PASS] $Name" -ForegroundColor Green
    $script:passed++
}

function Fail([string]$Name, [string]$Reason) {
    Write-Host "[FAIL] $Name - $Reason" -ForegroundColor Red
    $script:failed++
}

function Assert-UserinfoOk([string]$Token, [string]$Step) {
    $resp = Invoke-WebRequest -Uri "$ZfUrl/api/zestflow/auth/userinfo" `
        -Headers @{ Authorization = "Bearer $Token" } -UseBasicParsing -TimeoutSec 15
    if ($resp.StatusCode -eq 200 -and $resp.Content -match '"username"\s*:\s*"admin"') {
        Pass $Step
        return $true
    }
    Fail $Step "unexpected response: $($resp.Content.Substring(0, [Math]::Min(200, $resp.Content.Length)))"
    return $false
}

Write-Host "ZestFlow Back-Channel Logout E2E"
Write-Host "  SSO: $SsoUrl"
Write-Host "  ZF:  $ZfUrl"
Write-Host ""

# Step 0: establish SSO OAuth authorization (backchannel only notifies RPs with grants)
try {
    $auth = Invoke-RestMethod -Uri "$ZfUrl/api/zestflow/auth/sso/authorize" -TimeoutSec 15
    $authUrl = $auth.data.authorizationUrl
    $jar = Join-Path $env:TEMP "zestflow-sso-oauth.txt"
    Remove-Item $jar -ErrorAction SilentlyContinue
    curl.exe -s -c $jar -b $jar -o NUL $authUrl | Out-Null
    curl.exe -s -c $jar -b $jar -o NUL -X POST "$SsoUrl/login" `
        -d "username=$Username&password=$Password" -H "Content-Type: application/x-www-form-urlencoded" | Out-Null
    $hdr = curl.exe -s -c $jar -b $jar -D - -o NUL --max-redirs 0 $authUrl
    if ($hdr -match "code=") { Pass "sso-oauth-authorization" } else { Fail "sso-oauth-authorization" "no code in redirect" }
} catch {
    Fail "sso-oauth-authorization" $_.Exception.Message
}

# Step 1: ZestFlow local login
try {
    $login = Invoke-RestMethod -Method Post -Uri "$ZfUrl/api/zestflow/auth/login" `
        -ContentType "application/json" -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) -TimeoutSec 15
    if ($login.data.token) { Pass "zestflow-login" } else { Fail "zestflow-login" "no token" }
    $zfToken = $login.data.token
} catch {
    Fail "zestflow-login" $_.Exception.Message
    exit 1
}

# Step 2: userinfo works before logout
if (-not (Assert-UserinfoOk $zfToken "pre-logout-userinfo")) { exit 1 }

# Step 3: SSO admin logout triggers backchannel
$ssoSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
try {
    $null = Invoke-RestMethod -Method Post -Uri "$SsoUrl/api/admin/auth/login" `
        -ContentType "application/json" -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) `
        -WebSession $ssoSession -TimeoutSec 15
    Pass "sso-admin-login"

    try {
        $null = Invoke-WebRequest -Method Post -Uri "$SsoUrl/api/admin/auth/logout" -WebSession $ssoSession -UseBasicParsing -TimeoutSec 15
        Pass "sso-admin-logout-trigger"
    } catch {
        Fail "sso-admin-logout-trigger" $_.Exception.Message
    }
} catch {
    Fail "sso-logout-chain" $_.Exception.Message
}

Start-Sleep -Seconds 6

# Step 4: revoked JWT must be denied (HTTP 401)
try {
    $resp = Invoke-WebRequest -Uri "$ZfUrl/api/zestflow/auth/userinfo" `
        -Headers @{ Authorization = "Bearer $zfToken" } -UseBasicParsing -TimeoutSec 15
    if ($resp.StatusCode -eq 401 -or $resp.Content -match 'AUTH_UNAUTHORIZED') {
        Pass "post-logout-deny"
    } elseif ($resp.Content -match '"username"\s*:\s*"admin"') {
        Fail "post-logout-deny" "still accessible after backchannel logout"
    } else {
        Fail "post-logout-deny" "unexpected body: $($resp.Content.Substring(0, [Math]::Min(200, $resp.Content.Length)))"
    }
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 401) {
        Pass "post-logout-deny"
    } else {
        Fail "post-logout-deny" $_.Exception.Message
    }
}

Write-Host ""
Write-Host "Result: $passed passed, $failed failed"
if ($failed -gt 0) { exit 1 }
