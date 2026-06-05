# 校验生产配置模板不含危险默认值（CI / 发布前）
$ErrorActionPreference = "Stop"
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$failed = $false

function Test-ProdFile($relPath, $forbiddenPatterns, $requiredPatterns) {
    $path = Join-Path $Root $relPath
    if (-not (Test-Path $path)) {
        Write-Host "MISSING: $relPath" -ForegroundColor Red
        return $false
    }
    $content = Get-Content $path -Raw
    $ok = $true
    Write-Host "Check $relPath" -ForegroundColor Cyan
    foreach ($f in $forbiddenPatterns) {
        $matched = if ($f -match '^\(\?') {
            $content -match $f
        } else {
            $content -match [regex]::Escape($f)
        }
        if ($matched) {
            Write-Host "  FORBIDDEN: $f" -ForegroundColor Red
            $ok = $false
        }
    }
    foreach ($r in $requiredPatterns) {
        $matched = if ($r -match '^\(\?') {
            $content -match $r
        } else {
            $content -match [regex]::Escape($r)
        }
        if (-not $matched) {
            Write-Host "  REQUIRED missing: $r" -ForegroundColor Red
            $ok = $false
        }
    }
    if ($ok) { Write-Host "  OK" -ForegroundColor Green }
    return $ok
}

if (-not (Test-ProdFile "zestflow-admin\src\main\resources\application-prod.example.yml" @(
        'password: admin123',
        '(?ms)playground:\s*\r?\n\s*enabled:\s*true',
        '(?ms)flyway:\s*\r?\n\s*enabled:\s*false'
    ) @(
        'ip-demo-mode: disabled',
        'flyway:',
        '(?ms)flyway:\s*\r?\n\s*enabled:\s*true',
        'baseline-on-migrate:',
        'http-timeout-ms:',
        'reconcile:',
        'health-probe:'
    ))) { $failed = $true }

if (-not (Test-ProdFile "zestflow-demo\src\main\resources\application-prod.example.yml" @(
        'admin123',
        'execute-endpoint-enabled: true'
    ) @(
        'execute-endpoint-enabled: false',
        'address: 127.0.0.1'
    ))) { $failed = $true }

if ($failed) { exit 1 }

& (Join-Path $Root "scripts/db/validate-admin-flyway.ps1")
if ($LASTEXITCODE -ne 0) { exit 1 }

Write-Host "All prod template checks passed." -ForegroundColor Green
exit 0
