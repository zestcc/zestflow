# 验证可观测性迁库结果
$ErrorActionPreference = 'Stop'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
$adminYml = Join-Path $PSScriptRoot '..\zestflow-admin\src\main\resources\application-local.yml'
$content = Get-Content $adminYml -Raw
$user = if ($content -match 'username:\s*(\S+)') { $Matches[1] } else { 'root' }
$pass = if ($content -match 'password:\s*(\S+)') { $Matches[1] } else { throw 'no password' }
$dbHost = if ($content -match 'jdbc:mysql://([^:/]+):(\d+)') { $Matches[1] } else { '127.0.0.1' }
$dbPort = if ($content -match 'jdbc:mysql://([^:/]+):(\d+)') { $Matches[2] } else { '3306' }
$cnf = Join-Path $env:TEMP "zestflow-verify-$([guid]::NewGuid().ToString('N')).cnf"
@"
[client]
host=$dbHost
port=$dbPort
user=$user
password=$pass
"@ | Set-Content -Path $cnf -Encoding ASCII
$query = @"
SELECT 'app_log.execution_payload' AS item, COUNT(*) AS cnt FROM information_schema.TABLES WHERE TABLE_SCHEMA='zestflow_app_log' AND TABLE_NAME='execution_payload'
UNION ALL SELECT 'app_log.chain_event_payload_gone', COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='zestflow_app_log' AND TABLE_NAME='chain_event_payload'
UNION ALL SELECT 'app_log.invocation_payload_gone', COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='zestflow_app_log' AND TABLE_NAME='invocation_payload'
UNION ALL SELECT 'app_log.chain_event.params_gone', COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='zestflow_app_log' AND TABLE_NAME='chain_event' AND COLUMN_NAME='params'
UNION ALL SELECT 'admin.playground_record.invocation_id', COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='zestflow_admin' AND TABLE_NAME='playground_record' AND COLUMN_NAME='invocation_id'
UNION ALL SELECT 'admin.playground_record.request_body_gone', COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='zestflow_admin' AND TABLE_NAME='playground_record' AND COLUMN_NAME='request_body';
"@
try {
    $query | & $mysql --defaults-extra-file=$cnf --default-character-set=utf8mb4 -t
} finally {
    Remove-Item $cnf -Force -ErrorAction SilentlyContinue
}
