# Apply demo-app seed to local MySQL (reads password from zestflow-admin application-local.yml)
param(
    [string]$MysqlUser = 'root',
    [string]$MysqlHost = '127.0.0.1',
    [string]$MysqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$localYml = Join-Path $Root 'zestflow-admin\src\main\resources\application-local.yml'
if (-not (Test-Path $localYml)) {
    Write-Error "Missing $localYml — copy application-local.example.yml first."
}
$pwd = $null
foreach ($line in Get-Content $localYml) {
    if ($line -match '^\s*password:\s*(.+)$' -and $line -notmatch 'admin123|your-') {
        $pwd = $Matches[1].Trim().Trim("'").Trim('"')
        break
    }
}
if (-not $pwd) { Write-Error 'Could not parse spring.datasource.password from application-local.yml' }

$env:MYSQL_PWD = $pwd
$cleanup = Join-Path $PSScriptRoot 'cleanup-playground-legacy.sql'
$execData = Join-Path $Root 'zestflow-executor\src\main\resources\db\initData.sql'
$adminData = Join-Path $Root 'zestflow-admin\src\main\resources\db\initData.sql'

if (-not (Test-Path $MysqlBin)) { Write-Error "mysql not found: $MysqlBin" }
& $MysqlBin -h $MysqlHost -u $MysqlUser --default-character-set=utf8mb4 -e "source $($cleanup -replace '\\','/')" 2>&1 | Out-Host
& $MysqlBin -h $MysqlHost -u $MysqlUser --default-character-set=utf8mb4 -e "source $($execData -replace '\\','/')" 2>&1 | Out-Host
& $MysqlBin -h $MysqlHost -u $MysqlUser --default-character-set=utf8mb4 -e "source $($adminData -replace '\\','/')" 2>&1 | Out-Host
Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
Write-Host 'Done: cleanup + executor initData + admin initData'
