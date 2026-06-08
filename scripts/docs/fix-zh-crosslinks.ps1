# Fix corrupted [简体中文](*.md) cross-links in *.en.md (mojibake from bulk edits).
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$zhLabel = [char]0x7B80 + [char]0x4F53 + [char]0x4E2D + [char]0x6587  # 简体中文
$updated = [System.Collections.Generic.List[string]]::new()

Get-ChildItem -Path $repoRoot -Recurse -Filter '*.en.md' | ForEach-Object {
    $cnHref = $_.Name -replace '\.en\.md$', '.md'
    $cnBase = [System.IO.Path]::GetFileNameWithoutExtension($cnHref)
    $content = [System.IO.File]::ReadAllText($_.FullName)

    # Broken pattern: [mojibake](FILE.md) missing ]
    $content = $content -replace '\[[^\]]*\(' + [regex]::Escape($cnHref) + '\)', "[$zhLabel]($cnHref)"

    # Wrong target: [简体中文](FILE.en.md)
    $pattern = '\[' + [regex]::Escape($zhLabel) + '\]\(' + [regex]::Escape($cnBase) + '\.en\.md\)'
    $content = [regex]::Replace($content, $pattern, "[$zhLabel]($cnHref)")

    $original = [System.IO.File]::ReadAllText($_.FullName)
    if ($content -ne $original) {
        [System.IO.File]::WriteAllText($_.FullName, $content, [System.Text.UTF8Encoding]::new($false))
        $updated.Add($_.FullName) | Out-Null
    }
}

Write-Host "Fixed $($updated.Count) files:"
$updated | ForEach-Object { Write-Host "  $_" }
