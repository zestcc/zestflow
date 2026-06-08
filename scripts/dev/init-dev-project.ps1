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
    [ValidateSet("cursor", "vscode", "cline", "claude", "claude-code", "windsurf", "all")]
    [string]$Ide = "all",
    [ValidateSet("full", "hybrid")]
    [string]$Componentization = "full",
    [string]$ComponentPackage = "component",
    [ValidateSet("1", "2", "3", "execute", "chain-route", "controller")]
    [string]$HttpMode = "3",
    [switch]$Force,
    [switch]$NoGitignore,
    [switch]$NoBootstrapConfig
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "mcp-jar-verify.ps1")
$Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$ProjectPath = (Resolve-Path $ProjectRoot).Path

$ToolsJar = Join-Path $env:USERPROFILE ".zestflow\tools\zestflow-dev-init.jar"
if (-not (Test-Path $ToolsJar)) {
    $built = Join-Path $Root "zestflow-dev-init/target/zestflow-dev-init-0.1.0-all.jar"
    if (Test-Path $built) {
        $ToolsJar = $built
    } else {
        throw "Dev-init JAR not found. Run from zestflow root: powershell -File scripts/dev/install-mcp.ps1"
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
if ($Componentization) { $argsList += @("--componentization", $Componentization) }
if ($ComponentPackage) { $argsList += @("--component-package", $ComponentPackage) }
if ($HttpMode) { $argsList += @("--http-mode", $HttpMode) }
if ($Force) { $argsList += "--force" }
if ($NoGitignore) { $argsList += "--no-gitignore" }
if ($NoBootstrapConfig) { $argsList += "--no-bootstrap-config" }

& java @argsList
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
