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
        if ($content -match $f) {
            Write-Host "  FORBIDDEN: $f" -ForegroundColor Red
            $ok = $false
        }
    }
    foreach ($r in $requiredPatterns) {
        if ($content -notmatch [regex]::Escape($r)) {
            Write-Host "  REQUIRED missing: $r" -ForegroundColor Red
            $ok = $false
        }
    }
    if ($ok) { Write-Host "  OK" -ForegroundColor Green }
    return $ok
}

if (-not (Test-ProdFile "zestflow-admin\src\main\resources\application-prod.example.yml" @(
        'password: admin123',
        '(?ms)playground:\s*\r?\n\s*enabled:\s*true'
    ) @(
        'enabled: false',
        'ip-demo-mode: disabled',
        'flyway:',
        'enabled: true'
    ))) { $failed = $true }

if (-not (Test-ProdFile "zestflow-demo\src\main\resources\application-prod.example.yml" @(
        'admin123',
        'execute-endpoint-enabled: true'
    ) @(
        'execute-endpoint-enabled: false',
        'address: 127.0.0.1'
    ))) { $failed = $true }

if ($failed) { exit 1 }
Write-Host "All prod template checks passed." -ForegroundColor Green
exit 0
