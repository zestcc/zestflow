# MySQL 客户端路径解析 — 供 init.ps1 / initData.ps1 dot-source
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
