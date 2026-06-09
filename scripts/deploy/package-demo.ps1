#Requires -Version 5.1
<#
.SYNOPSIS
  打包 ZestFlow 公网试玩部署目录（Admin + Demo，仅 Linux）。

.USAGE
  powershell -File scripts/deploy/package-demo.ps1
  powershell -File scripts/deploy/package-demo.ps1 -SkipBuild
  mvn package -pl zestflow-admin,zestflow-demo -am -Pdemo-dist -DskipTests
#>
param(
    [switch]$SkipBuild,
    [string]$Version = "",
    [string]$OutputDir = ""
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$AdminModule = Join-Path $Root "zestflow-admin"
$DemoModule = Join-Path $Root "zestflow-demo"
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

function Get-ModuleArtifactVersion([string]$ModuleDir) {
    $modulePom = Join-Path $ModuleDir "pom.xml"
    if (-not (Test-Path $modulePom)) { throw "Module pom not found: $modulePom" }
    $xml = [xml](Get-Content $modulePom -Raw)
    $raw = $xml.project.version
    if ($raw -match '\$\{(.+)\}') {
        $resolved = Get-RootPomProperty $Matches[1]
        if ($resolved) { return $resolved }
        throw "Cannot resolve property $($Matches[1]) for $ModuleDir"
    }
    return $raw.Trim()
}

function Get-AdminArtifactVersion {
    if ($Version) { return $Version.Trim() }
    $v = Get-RootPomProperty 'zestflow-admin.version'
    if ($v) { return $v }
    return Get-ModuleArtifactVersion $AdminModule
}

function Write-Utf8NoBom([string]$Path, [string]$Content) {
    $enc = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($Path, $Content, $enc)
}

function Prepare-DeployDir([string]$TargetDir) {
    Reset-DeployDir $TargetDir
}

function Copy-AdminDemoBundle {
    param(
        [string]$TargetDir,
        [string]$JarPath
    )
    $ver = Get-AdminArtifactVersion
    $configDir = Join-Path $TargetDir "config"
    $logDir = Join-Path $TargetDir "log"
    New-Item -ItemType Directory -Force -Path $configDir, $logDir | Out-Null

    Copy-Item (Join-Path $Templates "application-demo-admin.bundle.yml") (Join-Path $configDir "application-demo.yml") -Force
    Copy-Item (Join-Path $Templates "application-demo-secrets.bundle.yml") (Join-Path $configDir "application-secrets.yml") -Force
    Copy-Item (Join-Path $Templates "start-demo-admin.env.bundle") (Join-Path $configDir "start-admin.env") -Force
    Copy-Item (Join-Path $Templates "DEPLOY_DEMO_README.txt") (Join-Path $TargetDir "README.txt") -Force
    Copy-Item (Join-Path $Templates "repair-flyway-admin.sql") (Join-Path $configDir "repair-flyway-admin.sql") -Force

    Write-Utf8NoBom (Join-Path $configDir "secret") "RtJ0yZ8NdCW3CiwsFjEyNkxKt7AuGPnq"
    Write-Utf8NoBom (Join-Path $configDir "registry-token") "J6KkPU0emKkeUaZZlvWuRjAXkh6Y4SZc"
    Write-Utf8NoBom (Join-Path $configDir "executor-access-token") "OOFRAuecFZ0YiW4I6Fsqy4Utg02zTAIA"
    Write-Utf8NoBom (Join-Path $configDir "collector.access-token") "MGKcqBQpwFBtnsmNnUnNfVLHS1lM6G7z"
    Write-Utf8NoBom (Join-Path $configDir "bootstrap-admin.password") "zestflow"

    Copy-Item $JarPath (Join-Path $TargetDir "zestflow-admin-$ver.jar") -Force
    Copy-Item (Join-Path $AdminModule "start-admin.sh") (Join-Path $TargetDir "start-admin.sh") -Force
    Write-Host "  OK admin demo -> $TargetDir" -ForegroundColor Green
}

function Copy-DemoDemoBundle {
    param(
        [string]$TargetDir,
        [string]$JarPath
    )
    $ver = Get-ModuleArtifactVersion $DemoModule
    $configDir = Join-Path $TargetDir "config"
    $logDir = Join-Path $TargetDir "log"
    New-Item -ItemType Directory -Force -Path $configDir, $logDir | Out-Null

    Copy-Item (Join-Path $Templates "application-demo-demo.bundle.yml") (Join-Path $configDir "application-demo.yml") -Force
    Copy-Item (Join-Path $Templates "application-demo-secrets.bundle.yml") (Join-Path $configDir "application-secrets.yml") -Force
    Copy-Item (Join-Path $Templates "start-demo.env.bundle") (Join-Path $configDir "start-demo.env") -Force
    Copy-Item (Join-Path $Templates "DEPLOY_DEMO_README.txt") (Join-Path $TargetDir "README.txt") -Force

    Write-Utf8NoBom (Join-Path $configDir "registry-token") "J6KkPU0emKkeUaZZlvWuRjAXkh6Y4SZc"
    Write-Utf8NoBom (Join-Path $configDir "executor-access-token") "OOFRAuecFZ0YiW4I6Fsqy4Utg02zTAIA"
    Write-Utf8NoBom (Join-Path $configDir "collector.access-token") "MGKcqBQpwFBtnsmNnUnNfVLHS1lM6G7z"

    Copy-Item $JarPath (Join-Path $TargetDir "zestflow-demo-$ver.jar") -Force
    Copy-Item (Join-Path $DemoModule "start-demo.sh") (Join-Path $TargetDir "start-demo.sh") -Force
    Write-Host "  OK demo demo -> $TargetDir" -ForegroundColor Green
}

# --- main ---
. (Join-Path $PSScriptRoot "archive-utils.ps1")

$adminVer = Get-AdminArtifactVersion
$demoVer = Get-ModuleArtifactVersion $DemoModule
$adminJar = Join-Path $AdminModule "target/zestflow-admin-$adminVer.jar"
$demoJar = Join-Path $DemoModule "target/zestflow-demo-$demoVer.jar"

Write-Host "--- ZestFlow Demo deploy packager (admin $adminVer / demo $demoVer) ---" -ForegroundColor Cyan

if (-not $SkipBuild) {
    Write-Host "--- mvn package -pl zestflow-admin,zestflow-demo -am -DskipTests ---" -ForegroundColor Cyan
    Push-Location $Root
    try {
        & mvn package -pl zestflow-admin,zestflow-demo -am -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "mvn package failed with exit $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $adminJar)) { throw "Jar not found: $adminJar" }
if (-not (Test-Path $demoJar)) { throw "Jar not found: $demoJar" }

New-Item -ItemType Directory -Force -Path $DeployRoot | Out-Null

Clear-DeployArtifacts -DeployRoot $DeployRoot -IncludePatterns @(
    'zestflow_admin_demo_*',
    'zestflow_demo_demo_*'
)

$adminDir = Join-Path $DeployRoot "zestflow_admin_demo_${adminVer}_linux"
$demoDir = Join-Path $DeployRoot "zestflow_demo_demo_${demoVer}_linux"

Prepare-DeployDir $adminDir
Prepare-DeployDir $demoDir

Write-Host "--- Generating Linux demo bundles ---" -ForegroundColor Cyan
Copy-AdminDemoBundle -TargetDir $adminDir -JarPath $adminJar
Copy-DemoDemoBundle -TargetDir $demoDir -JarPath $demoJar

$adminTarGz = Join-Path $DeployRoot "zestflow_admin_demo_${adminVer}_linux.tar.gz"
$demoTarGz = Join-Path $DeployRoot "zestflow_demo_demo_${demoVer}_linux.tar.gz"
New-TarGzArchive -SourceDir $adminDir -TarGzPath $adminTarGz
New-TarGzArchive -SourceDir $demoDir -TarGzPath $demoTarGz

Write-Host ""
Write-Host "--- Demo deploy artifacts ready ---" -ForegroundColor Green
Write-Host "  $adminDir"
Write-Host "  $adminTarGz"
Write-Host "  $demoDir"
Write-Host "  $demoTarGz"
Write-Host ""
Write-Host "Login: zestflow / zestflow (demo profile, no forced password change)" -ForegroundColor Yellow
Write-Host "MySQL: 127.0.0.1:2882 root — see application-demo.yml in each bundle" -ForegroundColor Gray
Write-Host "Start: Admin first, then Demo | profile=demo | see README.txt" -ForegroundColor Gray
