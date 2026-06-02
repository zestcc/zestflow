# Generate chain_graph_snapshot seed from executor initData.sql (zf_chain_version graph_data)
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$ExecInit = Join-Path $Root 'zestflow-executor\src\main\resources\db\initData.sql'
$OutFile = Join-Path $Root 'zestflow-collector\collector-jdbc\src\main\resources\db\initData.sql'
$SnapshotSeedTime = '2020-01-01 00:00:00'

if (-not (Test-Path $ExecInit)) { throw "Missing $ExecInit" }

$content = [System.IO.File]::ReadAllText($ExecInit, [System.Text.Encoding]::UTF8)
$pattern = "INSERT IGNORE INTO ``zf_chain_version``[^;]*VALUES\s*\('([^']+)',\s*(\d+),\s*'[^']*',\s*'(\{.*?\})',\s*'\{"
$matches = [regex]::Matches($content, $pattern, [System.Text.RegularExpressions.RegexOptions]::Singleline)

$rows = New-Object System.Collections.Generic.List[string]
$seen = @{}
foreach ($m in $matches) {
    $chainCode = $m.Groups[1].Value
    $version = [int]$m.Groups[2].Value
    $graphJson = $m.Groups[3].Value -replace "''", "'"
    $key = "$chainCode|$version"
    if ($seen.ContainsKey($key)) { continue }
    $seen[$key] = $true
    $escaped = ($graphJson -replace "'", "''")
    $rows.Add("('$chainCode', $version, '$escaped', 1, 1, 'demo-app', 'system', '$SnapshotSeedTime', '$SnapshotSeedTime')")
}

if ($rows.Count -eq 0) {
    throw 'No zf_chain_version graph_data rows parsed from initData.sql'
}

$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine('-- ZestFlow Collector log DB seed: chain_graph_snapshot for demo chains')
[void]$sb.AppendLine('-- 2026-06-02: synced from executor initData zf_chain_version')
[void]$sb.AppendLine('-- Run after collector init.sql (Apply-DemoSeed.ps1 applies this file)')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('USE `zestflow_app_log`;')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('INSERT IGNORE INTO `chain_graph_snapshot` (`chain_code`, `version`, `graph_data`, `status`, `tenant_id`, `app_code`, `created_by`, `created_at`, `updated_at`) VALUES')
[void]$sb.AppendLine(($rows -join ",`n") + ';')

$utf8Bom = New-Object System.Text.UTF8Encoding $true
[System.IO.File]::WriteAllText($OutFile, $sb.ToString(), $utf8Bom)
Write-Host "OK log snapshots=$($rows.Count) -> $OutFile"
