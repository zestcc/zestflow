# Reset auto-provisioned IP demo tenants before E2E (keeps seed tenant 1/2 mappings)
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
DELETE FROM tenant_ip_mapping;
DELETE FROM playground_scene WHERE tenant_id NOT IN (1,2);
DELETE FROM tenant WHERE code LIKE 'demo-%' AND id NOT IN (1,2);
"@
& $MysqlBin -h $MysqlHost -u $MysqlUser --default-character-set=utf8mb4 -e $sql 2>&1 | Out-Host
Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
Write-Host 'Done: reset ip demo test data'
