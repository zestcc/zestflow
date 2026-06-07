#Requires -Version 5.1
<#
  校验 zestflow-mcp fat JAR 是否包含 --init-dev 所需的 dev-templates（跨 IDE 架构模板）。
#>

$script:RequiredMcpDevTemplates = @(
    "META-INF/zestflow/dev-templates/rules/architecture.md.template"
    "META-INF/zestflow/dev-templates/rules/project.md.template"
    "META-INF/zestflow/dev-templates/ide/cursor-rules.md.template"
    "META-INF/zestflow/dev-templates/ide/copilot-instructions.md.template"
    "META-INF/zestflow/dev-templates/ide/claude.md.template"
)

function Get-McpJarExe {
    if ($env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME "bin\jar.exe"
        if (Test-Path $candidate) { return $candidate }
    }
    return "jar"
}

function Get-McpJarListing {
    param([Parameter(Mandatory = $true)][string]$JarPath)
    $jarExe = Get-McpJarExe
    $output = & $jarExe tf $JarPath 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "无法读取 JAR 清单: $JarPath`n$output"
    }
    return [System.Collections.Generic.HashSet[string]]::new([string[]]$output, [StringComparer]::OrdinalIgnoreCase)
}

function Test-McpJarDevTemplates {
    param([Parameter(Mandatory = $true)][string]$JarPath)
    if (-not (Test-Path $JarPath)) {
        return @{ Ok = $false; Missing = @("(file not found)"); JarPath = $JarPath }
    }
    $listing = Get-McpJarListing -JarPath $JarPath
    $missing = @()
    foreach ($entry in $script:RequiredMcpDevTemplates) {
        if (-not $listing.Contains($entry)) {
            $missing += $entry
        }
    }
    return @{ Ok = ($missing.Count -eq 0); Missing = $missing; JarPath = $JarPath }
}

function Assert-McpJarDevTemplates {
    param([Parameter(Mandatory = $true)][string]$JarPath)
    $result = Test-McpJarDevTemplates -JarPath $JarPath
    if ($result.Ok) { return }
    $missingLines = ($result.Missing | ForEach-Object { "  - $_" }) -join [Environment]::NewLine
    throw @"
MCP JAR 过旧或不完整，缺少 --init-dev 模板:
$missingLines

请在 zestflow 仓库根目录重新安装平台 JAR:
  cd <zestflow-root>
  powershell -File scripts/dev/install-mcp.ps1

（需 mvn -pl zestflow-mcp -am package，不要仅用 -SkipBuild 复制旧 JAR）

当前 JAR: $($result.JarPath)
"@
}
