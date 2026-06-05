# 校验 Admin Flyway 迁移脚本规范（CI / 发布前）
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$failed = $false

function Fail([string]$msg) {
    Write-Host "FAIL: $msg" -ForegroundColor Red
    $script:failed = $true
}

$migrationDir = Join-Path $Root "zestflow-admin/src/main/resources/db/migration"
$v1 = Join-Path $migrationDir "V1__init_admin_schema.sql"

Write-Host "Validate Admin Flyway" -ForegroundColor Cyan

if (-not (Test-Path $v1)) {
    Fail "missing V1__init_admin_schema.sql"
} else {
    $v1Content = Get-Content $v1 -Raw
    if ($v1Content -match 'CREATE TABLE IF NOT EXISTS') {
        Fail "V1 must not use CREATE TABLE IF NOT EXISTS (Flyway tracks applied versions)"
    }
    if ($v1Content -match '(?i)CREATE\s+DATABASE') {
        Fail "V1 must not CREATE DATABASE (Flyway uses datasource schema)"
    }
}

Get-ChildItem $migrationDir -Filter "V*.sql" -ErrorAction SilentlyContinue | ForEach-Object {
    $c = Get-Content $_.FullName -Raw
    if ($c -match '(?i)CREATE\s+DATABASE') {
        Fail "$($_.Name) must not CREATE DATABASE"
    }
}

if ($failed) { exit 1 }
Write-Host "Admin Flyway checks passed." -ForegroundColor Green
exit 0
