# ZestFlow 数据库初始化 — DDL（建库建表）
# 用法：powershell -File scripts/init.ps1
# 依赖：MySQL 8.x；密码从 zestflow-admin/application-local.yml 读取
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

$initFiles = @(
    (Join-Path $Root 'zestflow-admin\src\main\resources\db\init.sql'),
    (Join-Path $Root 'zestflow-executor\src\main\resources\db\init.sql'),
    (Join-Path $Root 'zestflow-collector\collector-jdbc\src\main\resources\db\init.sql')
)

foreach ($file in $initFiles) {
    if (-not (Test-Path $file)) { Write-Error "Missing init.sql: $file" }
    Write-Host "Applying $(Split-Path $file -Parent | Split-Path -Leaf)/init.sql ..."
    & $MysqlBin -h $MysqlHost -u $MysqlUser --default-character-set=utf8mb4 -e "source $($file -replace '\\','/')" 2>&1 | Out-Host
}

Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
Write-Host 'Done: init (admin + executor + collector DDL)'
