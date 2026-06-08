# Rewrite internal doc links in *.en.md to *.en.md mirrors when they exist.
# Skips: 简体中文 cross-links, CATALOG.en.md (bilingual index tables), external URLs.
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Resolve-DocPath {
    param([string]$FromFile, [string]$Href)
    if ($Href -match '^(https?://|mailto:)') { return $null }
    $baseDir = Split-Path $FromFile -Parent
    $combined = [System.IO.Path]::GetFullPath((Join-Path $baseDir $Href))
    return $combined
}

function Get-EnMirrorPath {
    param([string]$MdPath)
    if ($MdPath -match '\.en\.md$') { return $MdPath }
    return ($MdPath -replace '\.md$', '.en.md')
}

$skipFiles = @('CATALOG.en.md')
$updated = [System.Collections.Generic.List[string]]::new()

Get-ChildItem -Path $repoRoot -Recurse -Filter '*.en.md' | ForEach-Object {
    if ($skipFiles -contains $_.Name) { return }

    $file = $_.FullName
    $content = [System.IO.File]::ReadAllText($file)
    $newContent = [regex]::Replace($content, '\[([^\]]*)\]\(([^)]+)\)', {
        param($m)
        $text = $m.Groups[1].Value
        $href = $m.Groups[2].Value

        if ($text -match '简体中文|Chinese|中文|Simplified') { return $m.Value }
        if ($href -match '\.en\.md$') { return $m.Value }
        if ($href -notmatch '\.md$') { return $m.Value }

        $resolved = Resolve-DocPath -FromFile $file -Href $href
        if (-not $resolved -or -not (Test-Path $resolved)) { return $m.Value }

        $enMirror = Get-EnMirrorPath $resolved
        if (-not (Test-Path $enMirror)) { return $m.Value }

        $enHref = $href -replace '([^/\\]+)\.md$', '$1.en.md'
        return "[$text]($enHref)"
    })

    if ($newContent -ne $content) {
        [System.IO.File]::WriteAllText($file, $newContent, [System.Text.UTF8Encoding]::new($false))
        $updated.Add($file) | Out-Null
    }
}

Write-Host "Updated $($updated.Count) English doc files:"
$updated | ForEach-Object { Write-Host "  $_" }
