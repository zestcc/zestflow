#Requires -Version 5.1
<#
.SYNOPSIS
  构建 zestflow-mcp 并复制到 zestflow-demo/dev-tools（开发专用，不进 demo 试玩包）。

.USAGE
  powershell -File scripts/dev/setup-demo-mcp.ps1
#>
param(
    [string]$Version = ""
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$McpModule = Join-Path $Root "zestflow-mcp"
$DemoTools = Join-Path $Root "zestflow-demo/dev-tools"

function Get-ProjectVersion {
    if ($Version) { return $Version.Trim() }
    $pom = Join-Path $Root "pom.xml"
    [xml]$xml = Get-Content $pom -Raw
    $v = $xml.project.properties.'zestflow-mcp.version'
    if (-not $v) { $v = $xml.project.version }
    return $v
}

$ver = Get-ProjectVersion
$jarName = "zestflow-mcp-$ver-all.jar"
$srcJar = Join-Path $McpModule "target/$jarName"

Write-Host "--- Build zestflow-mcp (profile dev-mcp) ---" -ForegroundColor Cyan
Push-Location $Root
try {
    & mvn -Pdev-mcp -pl zestflow-mcp package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }
} finally {
    Pop-Location
}

if (-not (Test-Path $srcJar)) { throw "Jar not found: $srcJar" }

New-Item -ItemType Directory -Force -Path $DemoTools | Out-Null
Copy-Item $srcJar (Join-Path $DemoTools $jarName) -Force

Write-Host "[OK] Copied to $DemoTools\$jarName" -ForegroundColor Green
Write-Host "Open zestflow-demo in Cursor and use .cursor/mcp.json" -ForegroundColor Yellow
