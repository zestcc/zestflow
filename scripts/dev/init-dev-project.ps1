#Requires -Version 5.1
<#
.SYNOPSIS
  初始化业务工程的 Dev Copilot 文件（project.md、IDE MCP 配置等）。

.DESCRIPTION
  包装 zestflow-mcp --init-dev；需先 install-mcp.ps1 或本地构建 mcp JAR。

.USAGE
  powershell -File scripts/dev/init-dev-project.ps1 -ProjectRoot D:/work/my-app
  powershell -File scripts/dev/init-dev-project.ps1 -ProjectRoot . -AppCode my-app -Ide cursor
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectRoot,
    [string]$AppCode = "",
    [string]$ExecutorUrl = "",
    [string]$BasePackage = "",
    [ValidateSet("cursor", "vscode", "claude", "all")]
    [string]$Ide = "all",
    [switch]$Force,
    [switch]$NoGitignore
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ProjectPath = (Resolve-Path $ProjectRoot).Path

$ToolsJar = Join-Path $env:USERPROFILE ".zestflow\tools\zestflow-mcp.jar"
if (-not (Test-Path $ToolsJar)) {
    $pom = Join-Path $Root "pom.xml"
    [xml]$xml = Get-Content $pom -Raw
    $ver = $xml.project.properties.'zestflow-mcp.version'
    if (-not $ver) { $ver = $xml.project.version }
    $built = Join-Path $Root "zestflow-mcp/target/zestflow-mcp-$ver-all.jar"
    if (Test-Path $built) {
        $ToolsJar = $built
    } else {
        throw "MCP JAR not found. Run: powershell -File scripts/dev/install-mcp.ps1"
    }
}

$argsList = @(
    "-jar", $ToolsJar,
    "--init-dev",
    "--project", $ProjectPath,
    "--ide", $Ide
)
if ($AppCode) { $argsList += @("--app-code", $AppCode) }
if ($ExecutorUrl) { $argsList += @("--executor-url", $ExecutorUrl) }
if ($BasePackage) { $argsList += @("--base-package", $BasePackage) }
if ($Force) { $argsList += "--force" }
if ($NoGitignore) { $argsList += "--no-gitignore" }

& java @argsList
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
