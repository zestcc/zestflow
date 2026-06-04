# Ensure playground_scene uses tenant-scoped unique key (idempotent)
param(
    [string]$MysqlUser = 'root',
    [string]$MysqlHost = '127.0.0.1',
    [string]$MysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$localYml = Join-Path $Root 'zestflow-admin\src\main\resources\application-local.yml'
$pwd = $null
foreach ($line in Get-Content $localYml) {
    if ($line -match '^\s*password:\s*(.+)$' -and $line -notmatch 'admin123|your-') {
        $pwd = $Matches[1].Trim().Trim("'").Trim('"')
        break
    }
}
if (-not $pwd) { Write-Error 'Could not parse password' }
$env:MYSQL_PWD = $pwd
$sql = @"
USE zestflow_admin;
SET @has_old := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='playground_scene' AND index_name='uk_scene_code');
SET @drop_old := IF(@has_old > 0, 'ALTER TABLE playground_scene DROP INDEX uk_scene_code', 'SELECT 1');
PREPARE s1 FROM @drop_old; EXECUTE s1; DEALLOCATE PREPARE s1;
SET @has_new := (SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='playground_scene' AND index_name='uk_tenant_scene');
SET @add_new := IF(@has_new = 0, 'ALTER TABLE playground_scene ADD UNIQUE KEY uk_tenant_scene (tenant_id, scene_code)', 'SELECT 1');
PREPARE s2 FROM @add_new; EXECUTE s2; DEALLOCATE PREPARE s2;
"@
& $MysqlBin -h $MysqlHost -u $MysqlUser --default-character-set=utf8mb4 -e $sql 2>&1 | Out-Host
Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
Write-Host 'Done: playground tenant-scoped unique key'
