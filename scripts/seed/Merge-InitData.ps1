# Merge generated demo seed into initData.sql (keeps full CHN_DEMO_AFTER_SALE DAG block)
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$utf8 = New-Object System.Text.UTF8Encoding $true

$execInit = Join-Path $Root 'zestflow-executor\src\main\resources\db\initData.sql'
$execGen = Join-Path $Root 'zestflow-executor\src\main\resources\db\demo-chains-generated.sql'
$adminInit = Join-Path $Root 'zestflow-admin\src\main\resources\db\initData.sql'
$adminGen = Join-Path $Root 'zestflow-admin\src\main\resources\db\demo-scenes-generated.sql'

$execLines = [System.IO.File]::ReadAllLines($execInit, $utf8)
$afterSaleStart = -1
for ($i = 0; $i -lt $execLines.Length; $i++) {
    if ($execLines[$i] -like '*DES_DEMO_AFTER_SALE + CHN_DEMO_AFTER_SALE*') {
        $afterSaleStart = $i - 1
        break
    }
}
if ($afterSaleStart -lt 0) {
    for ($i = 0; $i -lt $execLines.Length; $i++) {
        if ($execLines[$i] -like '*CHN_DEMO_AFTER_SALE*' -and $execLines[$i] -like '*updated_by*') {
            $afterSaleStart = $i - 3
            break
        }
    }
}
if ($afterSaleStart -lt 0) { throw 'AFTER_SALE block not found in executor initData.sql' }

$afterSaleBlock = New-Object System.Collections.Generic.List[string]
for ($i = $afterSaleStart; $i -lt $execLines.Length; $i++) {
    $line = $execLines[$i]
    if ($line -like '*playground_scene*') { continue }
    if ($line -match '^\s*VALUES\s*\(\s*''SCN') { continue }
    $afterSaleBlock.Add($line)
}

$headerEnd = 0
for ($i = 0; $i -lt $execLines.Length; $i++) {
    if ($execLines[$i] -like '*zf_chain*' -and $execLines[$i] -like '*INSERT*') {
        $headerEnd = $i
        break
    }
}
$header = $execLines[0..($headerEnd - 1)]

$genLines = @()
foreach ($line in [System.IO.File]::ReadAllLines($execGen, $utf8)) {
    if ($line.StartsWith('USE ')) { continue }
    if ($line.StartsWith('-- 2026-06-02: demo-app full chains')) { continue }
    $genLines += $line
}

$execOut = New-Object System.Collections.Generic.List[string]
foreach ($line in $header) { [void]$execOut.Add($line) }
[void]$execOut.Add('')
[void]$execOut.Add('-- demo-app linear chains with real components')
[void]$execOut.Add('')
foreach ($line in $genLines) { [void]$execOut.Add($line) }
[void]$execOut.Add('')
foreach ($line in $afterSaleBlock) { [void]$execOut.Add($line) }
[System.IO.File]::WriteAllLines($execInit, $execOut.ToArray(), $utf8)

$adminLines = [System.IO.File]::ReadAllLines($adminInit, $utf8)
$sceneStart = 0
for ($i = 0; $i -lt $adminLines.Length; $i++) {
    if ($adminLines[$i] -like '*playground_scene*' -and $adminLines[$i] -like '*INSERT*') {
        $sceneStart = $i - 1
        break
    }
}
$adminHeader = $adminLines[0..($sceneStart - 1)]
$adminGenLines = @()
foreach ($line in [System.IO.File]::ReadAllLines($adminGen, $utf8)) {
    if ($line.StartsWith('USE ')) { continue }
    if ($line.StartsWith('-- 2026-06-02: demo-app playground')) { continue }
    $adminGenLines += $line
}
$adminOut = New-Object System.Collections.Generic.List[string]
foreach ($line in $adminHeader) { [void]$adminOut.Add($line) }
[void]$adminOut.Add('-- demo-app playground scenes (28 full-chain)')
[void]$adminOut.Add('')
foreach ($line in $adminGenLines) { [void]$adminOut.Add($line) }
[System.IO.File]::WriteAllLines($adminInit, $adminOut.ToArray(), $utf8)

Write-Host "Merged executor=$execInit admin=$adminInit"
