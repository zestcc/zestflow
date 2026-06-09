#Requires -Version 5.1
<#
.SYNOPSIS
  打包 ZestFlow Admin 生产部署目录（Linux + Windows）及压缩包。

.USAGE
  powershell -File scripts/deploy/package-admin.ps1
  powershell -File scripts/deploy/package-admin.ps1 -SkipBuild
  mvn package -pl zestflow-admin -Pdist -DskipTests
#>
param(
    [switch]$SkipBuild,
    [string]$Version = "",
    [string]$OutputDir = ""
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$AdminModule = Join-Path $Root "zestflow-admin"
$Templates = Join-Path $PSScriptRoot "templates"
$DeployRoot = if ($OutputDir) { $OutputDir } else { Join-Path $Root "deploy" }

function Get-RootPomProperty([string]$Name) {
    $pom = Join-Path $Root "pom.xml"
    if (-not (Test-Path $pom)) { throw "pom.xml not found" }
    $xml = [xml](Get-Content $pom -Raw)
    $v = $xml.project.properties.$Name
    if ($v) { return $v.Trim() }
    return $null
}

# Admin 模块版本独立于根 POM（根 0.1.0，admin 常为 ${zestflow-admin.version} → 0.2.0）
function Get-AdminArtifactVersion {
    if ($Version) { return $Version.Trim() }
    $v = Get-RootPomProperty 'zestflow-admin.version'
    if ($v) { return $v }
    $adminPom = Join-Path $AdminModule "pom.xml"
    if (Test-Path $adminPom) {
        $xml = [xml](Get-Content $adminPom -Raw)
        $raw = $xml.project.version
        if ($raw -match '\$\{(.+)\}') {
            $resolved = Get-RootPomProperty $Matches[1]
            if ($resolved) { return $resolved }
        } elseif ($raw) {
            return $raw.Trim()
        }
    }
    throw "Cannot resolve zestflow-admin artifact version"
}

function New-RandomToken([int]$ByteLength = 24) {
    $bytes = New-Object byte[] $ByteLength
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $s = [Convert]::ToBase64String($bytes) -replace '[+/=]', ''
    while ($s.Length -lt 24) {
        $s += 'Z'
    }
    return $s.Substring(0, [Math]::Max(24, [Math]::Min(48, $s.Length)))
}

function New-AdminBootstrapPassword {
    $chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#"
    $bytes = New-Object byte[] 14
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $sb = New-Object System.Text.StringBuilder
    foreach ($b in $bytes) {
        [void]$sb.Append($chars[$b % $chars.Length])
    }
    return $sb.ToString()
}

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    $enc = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($Path, $Content, $enc)
}

function New-SecretsYaml {
    param(
        [string]$JwtSecret,
        [string]$RegistryToken,
        [string]$ExecutorToken,
        [string]$CollectorToken,
        [string]$AdminPassword
    )
    @"
# Auto-generated — keep private; mirrors standalone token files in this directory
zestflow:
  jwt:
    secret: $JwtSecret
  admin:
    registry-token: $RegistryToken
    executor-access-token: $ExecutorToken
    default-user:
      password: $AdminPassword
  collector:
    access-token: $CollectorToken
"@.TrimEnd() + "`n"
}

function Prepare-DeployDir([string]$TargetDir) {
    Reset-DeployDir $TargetDir
}

function Copy-AdminBundle {
    param(
        [string]$TargetDir,
        [string]$JarPath,
        [string]$PlatformLabel,
        [hashtable]$Secrets
    )

    $ver = Get-AdminArtifactVersion
    $configDir = Join-Path $TargetDir "config"
    $logDir = Join-Path $TargetDir "log"
    New-Item -ItemType Directory -Force -Path $configDir, $logDir | Out-Null

    Copy-Item (Join-Path $Templates "application-prod.bundle.yml") (Join-Path $configDir "application-prod.yml") -Force
    Copy-Item (Join-Path $Templates "start-admin.env.bundle") (Join-Path $configDir "start-admin.env") -Force
    Copy-Item (Join-Path $Templates "DEPLOY_README.txt") (Join-Path $TargetDir "README.txt") -Force

    Write-Utf8NoBom (Join-Path $configDir "application-secrets.yml") (New-SecretsYaml `
        $Secrets.Jwt $Secrets.Registry $Secrets.Executor $Secrets.Collector $Secrets.AdminPassword)
    Write-Utf8NoBom (Join-Path $configDir "secret") $Secrets.Jwt
    Write-Utf8NoBom (Join-Path $configDir "registry-token") $Secrets.Registry
    Write-Utf8NoBom (Join-Path $configDir "executor-access-token") $Secrets.Executor
    Write-Utf8NoBom (Join-Path $configDir "collector.access-token") $Secrets.Collector
    Write-Utf8NoBom (Join-Path $configDir "bootstrap-admin.password") $Secrets.AdminPassword

    $jarName = "zestflow-admin-$ver.jar"
    $jarDest = Join-Path $TargetDir $jarName
    try {
        Copy-Item $JarPath $jarDest -Force
    } catch {
        throw "Cannot overwrite $jarDest (jar may be locked by running Admin). Stop Admin and retry."
    }

    if ($PlatformLabel -eq "linux") {
        Copy-Item (Join-Path $AdminModule "start-admin.sh") (Join-Path $TargetDir "start-admin.sh") -Force
    } else {
        Copy-Item (Join-Path $AdminModule "start-admin.bat") (Join-Path $TargetDir "start-admin.bat") -Force
    }

    Write-Host "  OK $PlatformLabel -> $TargetDir" -ForegroundColor Green
}

# --- main ---
. (Join-Path $PSScriptRoot "archive-utils.ps1")

$ver = Get-AdminArtifactVersion
$jar = Join-Path $AdminModule "target/zestflow-admin-$ver.jar"

Write-Host "--- ZestFlow Admin deploy packager v$ver ---" -ForegroundColor Cyan

if (-not $SkipBuild) {
    Write-Host "--- mvn package -pl zestflow-admin -DskipTests ---" -ForegroundColor Cyan
    Push-Location $Root
    try {
        & mvn package -pl zestflow-admin -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "mvn package failed with exit $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $jar)) {
    throw "Jar not found: $jar (run mvn package first)"
}

New-Item -ItemType Directory -Force -Path $DeployRoot | Out-Null

Clear-DeployArtifacts -DeployRoot $DeployRoot -IncludePatterns @(
    'zestflow_admin_*_linux',
    'zestflow_admin_*_win',
    'zestflow_admin_*_linux.tar.gz',
    'zestflow_admin_*_linux.zip',
    'zestflow_admin_*_win.zip'
) -ExcludePatterns @('zestflow_admin_demo_*')

$linuxDir = Join-Path $DeployRoot "zestflow_admin_${ver}_linux"
$winDir = Join-Path $DeployRoot "zestflow_admin_${ver}_win"

Prepare-DeployDir $linuxDir
Prepare-DeployDir $winDir

Write-Host "--- Generating Linux bundle ---" -ForegroundColor Cyan
$sharedSecrets = @{
    Jwt            = (New-RandomToken 32)
    Registry       = (New-RandomToken 24)
    Executor       = (New-RandomToken 24)
    Collector      = (New-RandomToken 24)
    AdminPassword  = (New-AdminBootstrapPassword)
}
Copy-AdminBundle -TargetDir $linuxDir -JarPath $jar -PlatformLabel "linux" -Secrets $sharedSecrets

Write-Host "--- Generating Windows bundle ---" -ForegroundColor Cyan
Copy-AdminBundle -TargetDir $winDir -JarPath $jar -PlatformLabel "win" -Secrets $sharedSecrets

$linuxTarGz = Join-Path $DeployRoot "zestflow_admin_${ver}_linux.tar.gz"
$winZip = Join-Path $DeployRoot "zestflow_admin_${ver}_win.zip"
New-TarGzArchive -SourceDir $linuxDir -TarGzPath $linuxTarGz
New-ZipArchive -SourceDir $winDir -ZipPath $winZip

Write-Host ""
Write-Host "--- Deploy artifacts ready ---" -ForegroundColor Green
Write-Host "  $linuxDir"
Write-Host "  $linuxTarGz"
Write-Host "  $winDir"
Write-Host "  $winZip"
Write-Host ""
Write-Host "Bootstrap admin password (also in config/bootstrap-admin.password):" -ForegroundColor Yellow
Write-Host "  $($sharedSecrets.AdminPassword)"
Write-Host "DB: 自行 CREATE DATABASE zestflow_admin；改 config/application-prod.yml 中 datasource 口令" -ForegroundColor Gray
Write-Host "     启动 Admin 后 Flyway 自动建表 | profile=prod | mail=disabled" -ForegroundColor Gray
