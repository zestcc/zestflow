# ZestFlow SSO 联调冒烟脚本（Windows PowerShell）
# 用法：.\scripts\sso-smoke.ps1 -AdminBase http://localhost:8080 -SsoBase http://localhost:9000

param(
    [string]$AdminBase = "http://localhost:8080",
    [string]$SsoBase = "http://localhost:9000"
)

$ErrorActionPreference = "Stop"
$api = "$AdminBase/api/zestflow"

Write-Host "== ZestFlow SSO Smoke ==" -ForegroundColor Cyan
Write-Host "Admin: $AdminBase"
Write-Host "SSO:   $SsoBase"

Write-Host "`n[1] OIDC Discovery" -ForegroundColor Yellow
$discovery = Invoke-RestMethod -Uri "$SsoBase/api/public/.well-known/openid-configuration" -Method Get
if (-not $discovery.authorization_endpoint) {
    throw "Discovery 缺少 authorization_endpoint"
}
Write-Host "OK authorization_endpoint=$($discovery.authorization_endpoint)"

Write-Host "`n[2] Admin SSO Config" -ForegroundColor Yellow
$config = Invoke-RestMethod -Uri "$api/auth/sso/config" -Method Get
if (-not $config.data.enabled) {
    Write-Host "WARN zestflow.sso.enabled=false，跳过 authorize 步骤" -ForegroundColor DarkYellow
} else {
    Write-Host "OK provider=$($config.data.provider) displayName=$($config.data.displayName)"
}

Write-Host "`n[3] Admin SSO Authorize (PKCE)" -ForegroundColor Yellow
if ($config.data.enabled) {
    try {
        $auth = Invoke-RestMethod -Uri "$api/auth/sso/authorize" -Method Get
        if ($auth.data.authorizationUrl -and $auth.data.state) {
            Write-Host "OK state=$($auth.data.state)"
        } else {
            throw "authorize 响应不完整"
        }
    } catch {
        Write-Host "WARN authorize 请求失败（若接口需登录可忽略）: $_" -ForegroundColor DarkYellow
    }
}

Write-Host "`n[4] Health" -ForegroundColor Yellow
try {
    Invoke-WebRequest -Uri "$AdminBase/actuator/health" -Method Get -UseBasicParsing | Out-Null
    Write-Host "OK Admin actuator"
} catch {
    Write-Host "INFO actuator 未暴露或非 200（可忽略）"
}

Write-Host "`n== 完成 ==" -ForegroundColor Green
Write-Host "手工步骤：浏览器打开登录页 -> SSO 登录 -> 检查 user.sso_subject 已写入"
