# Verify every Chinese user doc under docs/ has a matching *.en.md mirror.
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$docsRoot = Join-Path $repoRoot 'docs'

$missing = [System.Collections.Generic.List[string]]::new()
$noEnglishLink = [System.Collections.Generic.List[string]]::new()

Get-ChildItem -Path $docsRoot -Recurse -Filter '*.md' |
    Where-Object { $_.Name -notmatch '\.en\.md$' } |
    ForEach-Object {
        $enPath = Join-Path $_.DirectoryName ($_.BaseName + '.en.md')
        if (-not (Test-Path $enPath)) {
            $missing.Add($_.FullName.Substring($repoRoot.Length + 1)) | Out-Null
            return
        }
        $content = [System.IO.File]::ReadAllText($_.FullName)
        if ($content -notmatch '\[English\]') {
            $noEnglishLink.Add($_.FullName.Substring($repoRoot.Length + 1)) | Out-Null
        }
    }

$exitCode = 0
if ($missing.Count -gt 0) {
    Write-Host "MISSING English mirrors ($($missing.Count)):" -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "  $_" }
    $exitCode = 1
} else {
    Write-Host "OK: All docs/*.md have *.en.md mirrors."
}

if ($noEnglishLink.Count -gt 0) {
    Write-Host "MISSING [English] header link ($($noEnglishLink.Count)):" -ForegroundColor Yellow
    $noEnglishLink | ForEach-Object { Write-Host "  $_" }
    $exitCode = 1
} else {
    Write-Host "OK: All Chinese docs have [English] header links."
}

exit $exitCode
