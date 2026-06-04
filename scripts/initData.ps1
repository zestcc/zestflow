# ZestFlow 数据库初始化 — DML（种子数据）
# 用法：powershell -File scripts/initData.ps1
# 顺序：先执行 scripts/init.ps1
param(
    [string]$MysqlUser = 'root',
    [string]$MysqlHost = '127.0.0.1',
    [string]$MysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot '_mysql-common.ps1')

$pwd = Get-ZestFlowMysqlPassword -Root $Root
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

Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
Write-Host 'Done: initData (executor + admin + collector seed)'
