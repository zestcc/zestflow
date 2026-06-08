# Add [English](*.en.md) link to Chinese docs when a mirror exists.
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

$updated = [System.Collections.Generic.List[string]]::new()

function Add-EnglishLink {
    param([string]$FilePath, [string]$EnFileName)

    $content = [System.IO.File]::ReadAllText($FilePath)
    if ($content -match '\[English\]\(') { return }

    $link = "[English]($EnFileName)"
    $lines = $content -split "`r?`n"
    $insertAt = 1

    for ($i = 1; $i -lt [Math]::Min(8, $lines.Length); $i++) {
        if ($lines[$i] -match '^>\s*\*\*') {
            if ($lines[$i] -notmatch '\[English\]') {
                $lines[$i] = $lines[$i].TrimEnd() + ' | ' + $link
                $insertAt = -1
            }
            break
        }
        if ($lines[$i].Trim() -ne '' -and $lines[$i] -notmatch '^#') { break }
    }

    if ($insertAt -ge 0) {
        $newLines = [System.Collections.Generic.List[string]]::new()
        for ($i = 0; $i -lt $lines.Length; $i++) {
            $newLines.Add($lines[$i])
            if ($i -eq 1) {
                $newLines.Add("> **语言** 简体中文 | $link")
            }
        }
        $lines = $newLines.ToArray()
    }

    $newContent = (($lines -join "`n").TrimEnd()) + "`n"
    if ($newContent -ne $content) {
        [System.IO.File]::WriteAllText($FilePath, $newContent, [System.Text.UTF8Encoding]::new($false))
        $updated.Add($FilePath) | Out-Null
    }
}

Get-ChildItem -Path (Join-Path $repoRoot 'docs') -Recurse -Filter '*.md' |
    Where-Object { $_.Name -notmatch '\.en\.md$' } |
    ForEach-Object {
        $enPath = Join-Path $_.DirectoryName ($_.BaseName + '.en.md')
        if (Test-Path $enPath) {
            Add-EnglishLink -FilePath $_.FullName -EnFileName ($_.BaseName + '.en.md')
        }
    }

foreach ($name in @('CONTRIBUTING.md', 'CHANGELOG.md')) {
    $cn = Join-Path $repoRoot $name
    $en = Join-Path $repoRoot ($name -replace '\.md$', '.en.md')
    if ((Test-Path $cn) -and (Test-Path $en)) {
        Add-EnglishLink -FilePath $cn -EnFileName ($name -replace '\.md$', '.en.md')
    }
}

Write-Host "Updated $($updated.Count) files:"
$updated | ForEach-Object { Write-Host "  $_" }
