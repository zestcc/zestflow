#Requires -Version 5.1
<#
.SYNOPSIS
  构建 zestflow-mcp 并安装到用户目录（平台级，全项目共用）。

.DESCRIPTION
  对标 Stripe/Supabase MCP：JAR 装一次到 ~/.zestflow/tools/；
  各业务工程仅在 .cursor/mcp.json 里配置 --project / app-code / executor-url。

.USAGE
  powershell -File scripts/dev/install-mcp.ps1
  powershell -File scripts/dev/install-mcp.ps1 -SkipBuild   # 仅重装已有 target 产物
#>
param(
    [string]$Version = "",
    [switch]$SkipBuild,
    [switch]$SetUserEnv
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$McpModule = Join-Path $Root "zestflow-mcp"
$ToolsDir = Join-Path $env:USERPROFILE ".zestflow\tools"

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
$stableJar = Join-Path $ToolsDir "zestflow-mcp.jar"
$versionedJar = Join-Path $ToolsDir $jarName

if (-not $SkipBuild) {
    Write-Host "--- Build zestflow-mcp (profile dev-mcp) ---" -ForegroundColor Cyan
    Push-Location $Root
    try {
        & mvn -Pdev-mcp -pl zestflow-mcp package -DskipTests
        if ($LASTEXITCODE -ne 0) { throw "mvn package failed" }
    } finally {
        Pop-Location
    }
}

if (-not (Test-Path $srcJar)) {
    throw "Jar not found: $srcJar`nRun without -SkipBuild, or mvn -Pdev-mcp -pl zestflow-mcp package"
}

New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
Copy-Item $srcJar $versionedJar -Force
Copy-Item $srcJar $stableJar -Force
Set-Content -Path (Join-Path $ToolsDir "zestflow-mcp.version") -Value $ver -Encoding UTF8

if ($SetUserEnv) {
    [Environment]::SetEnvironmentVariable("ZESTFLOW_MCP_JAR", $stableJar, "User")
    Write-Host "[OK] Set user env ZESTFLOW_MCP_JAR=$stableJar" -ForegroundColor Green
}

Write-Host "[OK] Installed platform MCP JAR" -ForegroundColor Green
Write-Host "  stable : $stableJar"
Write-Host "  version: $versionedJar ($ver)"
Write-Host ""
Write-Host "Next steps:" -ForegroundColor Yellow
Write-Host "  1. Init Dev Copilot files in your business project:"
Write-Host "       powershell -File scripts/dev/init-dev-project.ps1 -ProjectRoot D:/work/my-app"
Write-Host "     Or: java -jar `$env:USERPROFILE\.zestflow\tools\zestflow-mcp.jar --init-dev --project D:/work/my-app"
Write-Host "  2. Open business project in Cursor (workspaceFolder = --project)"
Write-Host "  3. Start local Executor; refresh MCP in Cursor settings"
Write-Host ""
Write-Host "Demo: open zestflow-demo folder (includes .cursor/mcp.json already)."
