# 从 application-local.yml 读取 MySQL 凭据并执行迁库（不在命令行暴露密码）
$ErrorActionPreference = 'Stop'
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
if (-not (Test-Path $mysql)) {
    Write-Error "未找到 mysql.exe: $mysql"
}

$adminYml = Join-Path $PSScriptRoot '..\zestflow-admin\src\main\resources\application-local.yml'
if (-not (Test-Path $adminYml)) {
    Write-Error "缺少 application-local.yml，请先配置数据库连接"
}

$content = Get-Content $adminYml -Raw
if ($content -match 'username:\s*(\S+)') { $user = $Matches[1] } else { $user = 'root' }
if ($content -match 'password:\s*(\S+)') { $pass = $Matches[1] } else { Write-Error '未找到 password' }
if ($content -match 'jdbc:mysql://([^:/]+):(\d+)') {
    $dbHost = $Matches[1]
    $dbPort = $Matches[2]
} else {
    $dbHost = '127.0.0.1'
    $dbPort = '3306'
}

$cnf = Join-Path $env:TEMP "zestflow-mysql-$([guid]::NewGuid().ToString('N')).cnf"
@"
[client]
host=$dbHost
port=$dbPort
user=$user
password=$pass
"@ | Set-Content -Path $cnf -Encoding ASCII

try {
    $sql = Join-Path $PSScriptRoot 'migrate-observability.sql'
    Get-Content $sql -Raw -Encoding UTF8 | & $mysql --defaults-extra-file=$cnf --default-character-set=utf8mb4
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Write-Host '迁库成功'
} finally {
    Remove-Item $cnf -Force -ErrorAction SilentlyContinue
}
