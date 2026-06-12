#Requires -Version 5.1
<#
  部署包压缩 — package-admin.ps1 / package-demo.ps1 共用
  Linux: .tar.gz（优先 7-Zip / Git Bash tar / System32 tar / 内置 .NET）
  Windows: .zip
#>

function Resolve-ArchiveOutputPath([string]$Path) {
    if ([IO.Path]::IsPathRooted($Path)) {
        return $Path
    }
    return (Join-Path (Get-Location).Path $Path)
}

function Get-TarExecutable {
    $paths = @(
        "$env:SystemRoot\System32\tar.exe"
        "$env:ProgramFiles\Git\usr\bin\tar.exe"
        "${env:ProgramFiles(x86)}\Git\usr\bin\tar.exe"
    )
    $cmd = Get-Command tar -ErrorAction SilentlyContinue
    if ($cmd -and $cmd.Source -notmatch 'WindowsApps') {
        $paths = @($cmd.Source) + $paths
    }
    foreach ($p in $paths) {
        if ($p -and (Test-Path -LiteralPath $p)) { return $p }
    }
    return $null
}

function Get-GitBashExecutable {
    $paths = @(
        "$env:ProgramFiles\Git\bin\bash.exe"
        "${env:ProgramFiles(x86)}\Git\bin\bash.exe"
    )
    foreach ($p in $paths) {
        if (Test-Path -LiteralPath $p) { return $p }
    }
    return $null
}

function Get-SevenZipExecutable {
    $paths = @()
    $cmd = Get-Command 7z -ErrorAction SilentlyContinue
    if ($cmd) { $paths += $cmd.Source }
    $paths += @(
        "$env:ProgramFiles\7-Zip\7z.exe"
        "${env:ProgramFiles(x86)}\7-Zip\7z.exe"
        (Join-Path $PSScriptRoot "tools/7za.exe")
    )
    foreach ($p in $paths) {
        if ($p -and (Test-Path -LiteralPath $p)) { return $p }
    }
    return $null
}

function Invoke-Native {
    param(
        [Parameter(Mandatory)] [string]$FilePath,
        [Parameter(Mandatory)] [string[]]$ArgumentList
    )
    $proc = Start-Process -FilePath $FilePath -ArgumentList $ArgumentList `
        -Wait -PassThru -NoNewWindow -ErrorAction Stop
    return $proc.ExitCode
}

function Ensure-DotNetTarGzHelper {
    if ('ZestFlowTarGz' -as [type]) { return }
    Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.IO.Compression;
using System.Text;

public static class ZestFlowTarGz
{
    public static void CreateFromDirectory(string sourceDir, string tarGzPath)
    {
        sourceDir = Path.GetFullPath(sourceDir);
        using (var fs = File.Create(tarGzPath))
        using (var gz = new GZipStream(fs, CompressionLevel.Optimal))
        {
            AddDirectory(gz, sourceDir, sourceDir);
            gz.Write(new byte[1024], 0, 1024);
        }
    }

    static void AddDirectory(Stream tar, string root, string current)
    {
        foreach (var dir in Directory.GetDirectories(current))
        {
            var rel = ToTarPath(root, dir) + "/";
            WriteEntry(tar, rel, null, true, 0, DateTime.UtcNow);
            AddDirectory(tar, root, dir);
        }
        foreach (var file in Directory.GetFiles(current))
        {
            var rel = ToTarPath(root, file);
            var data = File.ReadAllBytes(file);
            var mtime = File.GetLastWriteTimeUtc(file);
            WriteEntry(tar, rel, data, false, data.Length, mtime);
        }
    }

    static string ToTarPath(string root, string fullPath)
    {
        var rel = fullPath.Substring(root.Length).TrimStart(Path.DirectorySeparatorChar, Path.AltDirectorySeparatorChar);
        return rel.Replace('\\', '/');
    }

    static void WriteEntry(Stream tar, string name, byte[] data, bool isDir, long size, DateTime mtime)
    {
        var header = new byte[512];
        var nameBytes = Encoding.ASCII.GetBytes(name);
        if (nameBytes.Length > 100) throw new IOException("tar path too long: " + name);
        Array.Copy(nameBytes, 0, header, 0, nameBytes.Length);
        // C# 无八进制字面量：0644/0755 会被当成十进制 644/755，Linux tar 校验失败
        WriteOctal(header, 100, 8, isDir ? 493 : 420);
        WriteOctal(header, 108, 8, 0);
        WriteOctal(header, 116, 8, 0);
        WriteOctal(header, 124, 12, size);
        WriteOctal(header, 136, 12, (long)mtime.ToUniversalTime().Subtract(new DateTime(1970, 1, 1)).TotalSeconds);
        header[156] = (byte)(isDir ? (byte)'5' : (byte)'0');
        Encoding.ASCII.GetBytes("ustar").CopyTo(header, 257);
        header[262] = (byte)'0';
        header[263] = (byte)'0';
        WriteChecksum(header);
        tar.Write(header, 0, 512);
        if (!isDir && data != null && data.Length > 0)
        {
            tar.Write(data, 0, data.Length);
            var pad = (512 - (data.Length % 512)) % 512;
            if (pad > 0) tar.Write(new byte[pad], 0, pad);
        }
    }

    static void WriteOctal(byte[] buf, int offset, int length, long value)
    {
        var s = Convert.ToString(value, 8).PadLeft(length - 1, '0') + "\0";
        var bytes = Encoding.ASCII.GetBytes(s);
        Array.Copy(bytes, 0, buf, offset, Math.Min(bytes.Length, length));
    }

    static void WriteChecksum(byte[] buf)
    {
        for (int i = 148; i < 156; i++) buf[i] = (byte)' ';
        long sum = 0;
        for (int i = 0; i < 512; i++) sum += (byte)buf[i];
        var chk = Convert.ToString(sum, 8).PadLeft(6, '0') + "\0 ";
        Encoding.ASCII.GetBytes(chk).CopyTo(buf, 148);
    }
}
'@ -ErrorAction Stop
}

function New-ZipArchive {
    param(
        [Parameter(Mandatory)] [string]$SourceDir,
        [Parameter(Mandatory)] [string]$ZipPath
    )
    $out = Resolve-ArchiveOutputPath $ZipPath
    if (Test-Path $out) {
        try { Remove-Item $out -Force -ErrorAction Stop }
        catch {
            $out = $out -replace '\.zip$', "_$(Get-Date -Format 'yyyyMMddHHmmss').zip"
            Write-Host "  [WARN] zip in use -> $out" -ForegroundColor Yellow
        }
    }
    $src = (Resolve-Path $SourceDir).Path
    Compress-Archive -Path (Join-Path $src "*") -DestinationPath $out -CompressionLevel Optimal
    Write-Host "  ZIP -> $out" -ForegroundColor Green
}

function New-TarGzVia7Zip {
    param([string]$SourceDir, [string]$TarGzPath, [string]$SevenZipPath)
    $src = (Resolve-Path $SourceDir).Path
    $out = Resolve-ArchiveOutputPath $TarGzPath
    $tempTar = [IO.Path]::Combine([IO.Path]::GetTempPath(), "zestflow-pack-$(Get-Random).tar")
    try {
        if (Invoke-Native -FilePath $SevenZipPath -ArgumentList @('a', '-ttar', $tempTar, "$src\*") -ne 0) {
            throw '7z tar step failed'
        }
        if (Invoke-Native -FilePath $SevenZipPath -ArgumentList @('a', '-tgzip', $out, $tempTar) -ne 0) {
            throw '7z gzip step failed'
        }
    } finally {
        if (Test-Path $tempTar) { Remove-Item $tempTar -Force -ErrorAction SilentlyContinue }
    }
}

function New-TarGzViaGitBash {
    param([string]$SourceDir, [string]$TarGzPath, [string]$BashPath, [string]$TarPath)
    $src = (Resolve-Path $SourceDir).Path.Replace('\', '/')
    $out = (Resolve-Path (Split-Path $TarGzPath -Parent) -ErrorAction SilentlyContinue)
    if (-not $out) {
        New-Item -ItemType Directory -Force -Path (Split-Path $TarGzPath -Parent) | Out-Null
    }
    $outFile = (Resolve-ArchiveOutputPath $TarGzPath).Replace('\', '/')
    $tarBin = $TarPath.Replace('\', '/')
    $cmd = "& '$tarBin' -czf '$outFile' -C '$src' ."
    if (Invoke-Native -FilePath $BashPath -ArgumentList @('-lc', $cmd) -ne 0) {
        throw 'git bash tar failed'
    }
}

function New-TarGzViaTar {
    param([string]$SourceDir, [string]$TarGzPath, [string]$TarPath)
    $src = (Resolve-Path $SourceDir).Path
    $out = Resolve-ArchiveOutputPath $TarGzPath
    if (Invoke-Native -FilePath $TarPath -ArgumentList @('-czf', $out, '-C', $src, '.') -ne 0) {
        throw 'tar -czf failed'
    }
}

function New-TarGzViaDotNet {
    param([string]$SourceDir, [string]$TarGzPath)
    Ensure-DotNetTarGzHelper
    $src = (Resolve-Path $SourceDir).Path
    $out = Resolve-ArchiveOutputPath $TarGzPath
    [ZestFlowTarGz]::CreateFromDirectory($src, $out)
}

function New-TarGzArchive {
    param(
        [Parameter(Mandatory)] [string]$SourceDir,
        [Parameter(Mandatory)] [string]$TarGzPath
    )
    $out = Resolve-ArchiveOutputPath $TarGzPath
    if (Test-Path $out) {
        try { Remove-Item $out -Force -ErrorAction Stop }
        catch {
            $TarGzPath = $out -replace '\.tar\.gz$', "_$(Get-Date -Format 'yyyyMMddHHmmss').tar.gz"
            Write-Host "  [WARN] tar.gz in use -> $TarGzPath" -ForegroundColor Yellow
            $out = Resolve-ArchiveOutputPath $TarGzPath
        }
    }

    $attempts = @(
        {
            $7z = Get-SevenZipExecutable
            if (-not $7z) { throw 'skip' }
            New-TarGzVia7Zip -SourceDir $SourceDir -TarGzPath $TarGzPath -SevenZipPath $7z
            '7z'
        },
        {
            $bash = Get-GitBashExecutable
            $tar = Get-TarExecutable
            if (-not $bash -or -not $tar) { throw 'skip' }
            New-TarGzViaGitBash -SourceDir $SourceDir -TarGzPath $TarGzPath -BashPath $bash -TarPath $tar
            'git-bash-tar'
        },
        {
            $tar = Get-TarExecutable
            if (-not $tar) { throw 'skip' }
            New-TarGzViaTar -SourceDir $SourceDir -TarGzPath $TarGzPath -TarPath $tar
            'tar'
        },
        {
            New-TarGzViaDotNet -SourceDir $SourceDir -TarGzPath $TarGzPath
            'dotnet'
        }
    )

    $errors = @()
    foreach ($attempt in $attempts) {
        try {
            $label = & $attempt
            Write-Host "  TAR.GZ ($label) -> $out" -ForegroundColor Green
            return
        } catch {
            if ($_.Exception.Message -ne 'skip') {
                $errors += $_.Exception.Message
            }
        }
    }

    throw "Failed to create tar.gz. $($errors -join '; ')"
}

# 打包前清理 deploy/ 下同系列旧产物（目录 + 压缩包）
function Clear-DeployArtifacts {
    param(
        [Parameter(Mandatory)] [string]$DeployRoot,
        [Parameter(Mandatory)] [string[]]$IncludePatterns,
        [string[]]$ExcludePatterns = @()
    )
    if (-not (Test-Path $DeployRoot)) { return }

    Write-Host "--- Cleaning stale deploy artifacts ---" -ForegroundColor Cyan
    Get-ChildItem $DeployRoot -Force -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_.Name -eq '.gitignore') { return }

        $matched = $false
        foreach ($pat in $IncludePatterns) {
            if ($_.Name -like $pat) { $matched = $true; break }
        }
        if (-not $matched) { return }

        foreach ($ex in $ExcludePatterns) {
            if ($_.Name -like $ex) { return }
        }

        try {
            Remove-Item $_.FullName -Recurse -Force -ErrorAction Stop
            Write-Host "  removed $($_.Name)" -ForegroundColor DarkGray
        } catch {
            Write-Host "  [WARN] skip locked: $($_.Name)" -ForegroundColor Yellow
        }
    }
}

function Reset-DeployDir([string]$TargetDir) {
    if (Test-Path $TargetDir) {
        Remove-Item $TargetDir -Recurse -Force -ErrorAction Stop
    }
    New-Item -ItemType Directory -Force -Path $TargetDir | Out-Null
}
