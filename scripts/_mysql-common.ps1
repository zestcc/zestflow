# Shared MySQL helpers for init / initData scripts
function Get-ZestFlowMysqlPassword {
    param([string]$Root)
    $localYml = Join-Path $Root 'zestflow-admin\src\main\resources\application-local.yml'
    if (-not (Test-Path $localYml)) {
        throw "Missing $localYml — copy application-local.example.yml first."
    }
    foreach ($line in Get-Content $localYml) {
        if ($line -match '^\s*password:\s*(.+)$' -and $line -notmatch 'admin123|your-') {
            return $Matches[1].Trim().Trim("'").Trim('"')
        }
    }
    throw 'Could not parse spring.datasource.password from application-local.yml'
}
