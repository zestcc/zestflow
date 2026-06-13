# Playwright 浏览器 E2E — 登录页 + SSO 回调（需 Admin :8080 已启动且已 npm install）
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"
$UiRoot = Join-Path (Split-Path $PSScriptRoot -Parent | Split-Path -Parent) "zestflow-admin-ui"

Write-Host "=== Playwright Browser E2E ===" -ForegroundColor Cyan

if (-not (Test-Path (Join-Path $UiRoot "node_modules"))) {
    Write-Host "node_modules missing — run npm install in zestflow-admin-ui first" -ForegroundColor Yellow
    if ($AllowSkip) { exit 2 }
    exit 1
}

Push-Location $UiRoot
$env:E2E_BASE_URL = $BaseAdmin
$env:PLAYWRIGHT_CHANNEL = if ($env:PLAYWRIGHT_CHANNEL) { $env:PLAYWRIGHT_CHANNEL } else { "chrome" }
try {
    # 使用 channel=chrome 时可跳过 Chromium 下载；若需内置浏览器可设 PLAYWRIGHT_FORCE_INSTALL=1
    if ($env:PLAYWRIGHT_FORCE_INSTALL -eq "1") {
        npx playwright install chromium 2>&1 | Out-Host
    }
    npx playwright test -c e2e/playwright.config.ts 2>&1 | Out-Host
    $code = $LASTEXITCODE
} finally {
    Pop-Location
}

if ($code -eq 0) { exit 0 }
if ($AllowSkip) { exit 2 }
exit 1
