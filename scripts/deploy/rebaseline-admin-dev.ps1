# 开发库 Flyway Rebaseline — 清空 history 后由 Admin 非 prod 启动重放 V1→V3
param(
    [string]$Host = "127.0.0.1",
    [int]$Port = 3306,
    [string]$User = "root",
    [string]$Database = "zestflow_admin"
)

$ErrorActionPreference = "Stop"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$sql = Join-Path $PSScriptRoot "rebaseline-admin-dev.sql"

Write-Host "Rebaseline Flyway history for $Database on ${Host}:${Port} ..."
Write-Host "SQL: $sql"
Write-Host ""
Write-Host "请确保 MySQL 客户端在 PATH 中，并按需输入密码："
& mysql -h $Host -P $Port -u $User -p $Database -e "DELETE FROM flyway_schema_history;"
Write-Host ""
Write-Host "完成。请重启 Admin（application-local / 非 prod），Flyway 将重放 V1→V3。"
