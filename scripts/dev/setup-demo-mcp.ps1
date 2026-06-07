#Requires -Version 5.1
<#
.SYNOPSIS
  兼容旧命令：转发到 install-mcp.ps1（平台级安装，不再复制 JAR 到 demo/dev-tools）。

.USAGE
  powershell -File scripts/dev/setup-demo-mcp.ps1
#>
Write-Host "[NOTE] setup-demo-mcp.ps1 已合并为 install-mcp.ps1（JAR 安装到 ~/.zestflow/tools/）" -ForegroundColor Yellow
& (Join-Path $PSScriptRoot "install-mcp.ps1") @args
