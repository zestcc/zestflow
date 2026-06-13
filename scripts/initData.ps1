# ZestFlow 数据库初始化 — DML（种子数据）
# 用法：powershell -File scripts/initData.ps1
# 顺序：先执行 scripts/init.ps1
param(
    [string]$MysqlUser = 'root',
    [string]$MysqlHost = '127.0.0.1',
    [string]$MysqlBin = ''
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent

function Resolve-MysqlBin([string]$Preferred) {
    if ($Preferred -and (Test-Path $Preferred)) { return $Preferred }
    foreach ($candidate in @(
        'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe',
        'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe',
        'D:\IT\MYSQL\mysql8\mysql-8.0.32-winx64\bin\mysql.exe'
    )) {
        if (Test-Path $candidate) { return $candidate }
    }
    $cmd = Get-Command mysql -ErrorAction SilentlyContinue
    if ($cmd -and (Test-Path $cmd.Source)) { return $cmd.Source }
    throw 'mysql.exe not found; pass -MysqlBin explicitly'
}

$MysqlBin = Resolve-MysqlBin $MysqlBin

$localYml = Join-Path $Root 'zestflow-admin\src\main\resources\application-local.yml'
if (-not (Test-Path $localYml)) { Write-Error "Missing $localYml — copy application-local.example.yml first." }
$pwd = $null
foreach ($line in Get-Content $localYml) {
    if ($line -match '^\s*password:\s*(.+)$' -and $line -notmatch 'admin123|your-') {
        $pwd = $Matches[1].Trim().Trim("'").Trim('"')
        break
    }
}
if (-not $pwd) { Write-Error 'Could not parse spring.datasource.password from application-local.yml' }
$env:MYSQL_PWD = $pwd

$dataFiles = @(
    (Join-Path $Root 'zestflow-executor\src\main\resources\db\initData.sql'),
    (Join-Path $Root 'zestflow-admin\src\main\resources\db\initData.sql'),
    (Join-Path $Root 'zestflow-collector\collector-jdbc\src\main\resources\db\initData.sql')
)

foreach ($file in $dataFiles) {
    if (-not (Test-Path $file)) { Write-Error "Missing initData.sql: $file" }
    Write-Host "Applying $(Split-Path (Split-Path $file -Parent) -Leaf)/initData.sql ..."
    & $MysqlBin -h $MysqlHost -u $MysqlUser --default-character-set=utf8mb4 -e "source $($file -replace '\\','/')" 2>&1 | Out-Host
}

Write-Host 'Repair admin user_tenant bindings (if admin user already exists)...'
$repairSql = @"
USE zestflow_admin;
INSERT IGNORE INTO user_tenant (user_id, tenant_id, is_tenant_admin, created_by, created_at)
SELECT u.id, 1, 1, 'system', NOW() FROM user u WHERE u.username='admin';
INSERT IGNORE INTO user_tenant (user_id, tenant_id, is_tenant_admin, created_by, created_at)
SELECT u.id, 2, 1, 'system', NOW() FROM user u WHERE u.username='admin';
"@
& $MysqlBin -h $MysqlHost -u $MysqlUser --default-character-set=utf8mb4 -e $repairSql 2>&1 | Out-Host

Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
Write-Host 'Done: initData (executor + admin + collector seed)'
