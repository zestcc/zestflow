# Dev Copilot (zestflow-mcp) 黑盒：CLI 任务包 + Executor 对齐 + demo 集成
param(
    [string]$BaseExecutor = "http://127.0.0.1:20550",
    [string]$DemoRoot = "",
    [string]$McpJar = "",
    [string]$AppCode = "demo-app"
)

$ErrorActionPreference = "Continue"
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
if (-not $DemoRoot) { $DemoRoot = Join-Path $Root "zestflow-demo" }
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("ai-mcp-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$checks = New-Object System.Collections.Generic.List[object]

function Add-Check($name, $ok, $note) {
    $checks.Add([pscustomobject]@{ name = $name; ok = $ok; note = $note }) | Out-Null
}
function Save-Report {
    $out = @{ timestamp = (Get-Date).ToString("o"); checks = $checks }
    Set-Content -Path $ReportJson -Value ($out | ConvertTo-Json -Depth 6) -Encoding UTF8
    Write-Host "Saved: $ReportJson"
}

function Invoke-Api($method, $url, $body, [int]$TimeoutSec = 30) {
    try {
        $p = @{ Uri = $url; Method = $method; TimeoutSec = $TimeoutSec; UseBasicParsing = $true }
        if ($null -ne $body) { $p.Body = $body; $p.ContentType = "application/json" }
        $r = Invoke-WebRequest @p
        return @{ ok = $true; status = [int]$r.StatusCode; body = $r.Content }
    } catch {
        $st = 0; $b = ""
        if ($_.Exception.Response) {
            $st = [int]$_.Exception.Response.StatusCode.value__
            try { $b = (New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch {}
        }
        return @{ ok = $false; status = $st; body = $b }
    }
}

function Resolve-McpJar {
    param([string]$Explicit)
    if ($Explicit -and (Test-Path $Explicit)) { return (Resolve-Path $Explicit).Path }
    $userHome = if ($env:USERPROFILE) { $env:USERPROFILE } else { $env:HOME }
    $candidates = @(
        (Join-Path $userHome ".zestflow/tools/zestflow-mcp.jar"),
        $env:ZESTFLOW_MCP_JAR,
        (Join-Path $Root "zestflow-mcp/target/zestflow-mcp-0.1.0-all.jar"),
        (Join-Path $DemoRoot "dev-tools/zestflow-mcp-0.1.0-all.jar")
    )
    foreach ($c in $candidates) {
        if ($c -and (Test-Path $c)) { return (Resolve-Path $c).Path }
    }
    return $null
}

Write-Host "=== AI MCP E2E ===" -ForegroundColor Cyan

$jar = Resolve-McpJar $McpJar
Add-Check "mcp-jar-present" ([bool]$jar) $(if ($jar) { $jar } else { "run install-mcp.ps1" })

# demo Cursor 集成
$mcpJson = Join-Path $DemoRoot ".cursor/mcp.json"
$mcpCfgOk = $false
if (Test-Path $mcpJson) {
    $raw = Get-Content $mcpJson -Raw
    $mcpCfgOk = ($raw -match '\.zestflow/tools/zestflow-mcp\.jar|userHome') -and ($raw -match 'workspaceFolder') -and ($raw -match 'demo-app') -and ($raw -match '20550')
}
Add-Check "mcp-demo-cursor-config" $mcpCfgOk "path=$mcpJson"

$rulesMd = Join-Path $DemoRoot ".zestflow/rules/project.md"
Add-Check "mcp-demo-project-rules" (Test-Path $rulesMd) "path=$rulesMd"

$learningGitignore = Join-Path $DemoRoot ".zestflow/learning/.gitignore"
Add-Check "mcp-demo-learning-dir" (Test-Path $learningGitignore) "path=$learningGitignore"

# JAR 内平台 Pattern（L0）
$platformPatternsOk = $false
if ($jar) {
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin/jar.exe" } else { "jar" }
    if (-not (Get-Command $java -ErrorAction SilentlyContinue)) { $java = "jar" }
    try {
        $listing = & $java tf $jar 2>$null
        $platformPatternsOk = ($listing -match 'zestflow/patterns/platform/index.json') -and
            ($listing -match 'zestflow/patterns/platform/http-three-mode')
    } catch {}
}
Add-Check "mcp-platform-patterns" $platformPatternsOk $(if ($jar) { "jar=$jar" } else { "jar-missing" })

# 学习单测（AccuracyGate + ChainPlan）
Push-Location $Root
& mvn -Pdev-mcp -q test -pl zestflow-mcp "-Dtest=AccuracyGateTest,ChainPlanServiceTest"
$learningUnitOk = ($LASTEXITCODE -eq 0)
Pop-Location
Add-Check "mcp-learning-unit-tests" $learningUnitOk "exit=$LASTEXITCODE"

# Executor：list_components（等价 list_components Tool）
$list = Invoke-Api GET "$BaseExecutor/api/components?page=1&size=500" $null
$hasValidateUser = $false
if ($list.ok) {
    try { $hasValidateUser = $list.body -match 'validateUser' } catch {}
}
Add-Check "mcp-executor-list-components" ($list.ok -and $hasValidateUser) "status=$($list.status)"

# validate 合法链
$validChain = '{"code":"MCP_E2E","version":1,"nodes":[{"id":"n1","label":"v","type":"TASK","component":"validateUser"}],"edges":[]}'
$validBody = (@{ chainCode = "MCP_E2E"; version = 1; chainData = $validChain } | ConvertTo-Json -Compress -Depth 6)
$valOk = Invoke-Api POST "$BaseExecutor/api/chains/validate-definition" $validBody
$validResult = $false
if ($valOk.ok) {
    try {
        $vj = ConvertFrom-Json $valOk.body
        $validResult = ($vj.valid -eq $true) -or ($vj.data.valid -eq $true)
    } catch {
        $validResult = $valOk.body -match '"valid"\s*:\s*true'
    }
}
Add-Check "mcp-executor-validate-valid" ($valOk.ok -and $validResult) "status=$($valOk.status)"

# validate 非法 componentId
$badChain = '{"code":"MCP_BAD","version":1,"nodes":[{"id":"n1","label":"x","type":"TASK","component":"__NOT_REGISTERED__"}],"edges":[]}'
$badBody = (@{ chainCode = "MCP_BAD"; version = 1; chainData = $badChain } | ConvertTo-Json -Compress -Depth 6)
$valBad = Invoke-Api POST "$BaseExecutor/api/chains/validate-definition" $badBody
$invalidResult = $false
if ($valBad.ok) {
    try {
        $bj = ConvertFrom-Json $valBad.body
        $invalidResult = ($bj.valid -eq $false) -or ($bj.data.valid -eq $false)
    } catch {
        $invalidResult = $valBad.body -match '"valid"\s*:\s*false'
    }
}
Add-Check "mcp-executor-validate-invalid" ($valBad.ok -and $invalidResult) "status=$($valBad.status)"

# CLI 任务包导出
if ($jar) {
    $exportMd = Join-Path $OutDir ("mcp-task-package-{0}.md" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin/java.exe" } else { "java" }
    $cliArgs = @(
        "-jar", $jar,
        "--export-task-package",
        "--project", $DemoRoot,
        "--app-code", $AppCode,
        "--executor-url", $BaseExecutor,
        "-o", $exportMd
    )
    $proc = Start-Process -FilePath $java -ArgumentList $cliArgs -Wait -PassThru -NoNewWindow -RedirectStandardError (Join-Path $OutDir "mcp-cli-stderr.txt")
    $exportOk = ($proc.ExitCode -eq 0) -and (Test-Path $exportMd)
    $mdRich = $false
    if ($exportOk) {
        $md = Get-Content $exportMd -Raw
        $mdRich = ($md -match 'component') -and ($md.Length -gt 500)
    }
    Add-Check "mcp-cli-export" ($exportOk -and $mdRich) "exit=$($proc.ExitCode) file=$exportMd"
} else {
    Add-Check "mcp-cli-export" $false "jar-missing"
}

# Maven MCP 单测（无运行时依赖）
Push-Location $Root
& mvn -Pdev-mcp -q test -pl zestflow-mcp
$mvnOk = ($LASTEXITCODE -eq 0)
Pop-Location
Add-Check "mcp-maven-unit-tests" $mvnOk "exit=$LASTEXITCODE"

$fail = @($checks | Where-Object { -not $_.ok }).Count
Write-Host "Checks: $($checks.Count - $fail)/$($checks.Count) passed" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
Save-Report
if ($fail -gt 0) { exit 1 }
exit 0
