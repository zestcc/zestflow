# Copilot 全量部署验收：单测 + Admin API 黑盒 + MCP 黑盒 + 压测
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$BaseExecutor = "http://127.0.0.1:20550",
    [switch]$SkipMavenTest,
    [switch]$SkipMcpE2e,
    [switch]$SkipPerf,
    [switch]$RequireLlm,
    [switch]$UseMockLlm
)

$ErrorActionPreference = "Continue"
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("ai-copilot-acceptance-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$phases = New-Object System.Collections.Generic.List[object]
$script:exitCode = 0

function Add-Phase($name, $ok, $note) {
    $phases.Add([pscustomobject]@{ phase = $name; ok = $ok; note = $note }) | Out-Null
    if (-not $ok) { $script:exitCode = 1 }
    Write-Host ("[{0}] {1} — {2}" -f $(if ($ok) { 'PASS' } else { 'FAIL' }), $name, $note) -ForegroundColor $(if ($ok) { 'Green' } else { 'Red' })
}

Write-Host "=== Copilot Full Acceptance ===" -ForegroundColor Cyan
Write-Host "Spec: docs/AI_COPILOT_ACCEPTANCE.md" -ForegroundColor DarkGray

if (-not $SkipMavenTest) {
    Push-Location $Root
    & mvn test -pl zestflow-admin -q "-Dtest=AiRagServiceTest,AiCopilotControllerTest,AiCopilotServiceTest,TenantAiConfigServiceTest,AiProviderPresetRegistryTest"
    $adminMvnOk = ($LASTEXITCODE -eq 0)
    Pop-Location
    Add-Phase "maven-admin-ai-unit-tests" $adminMvnOk "exit=$LASTEXITCODE"

    Push-Location $Root
    & mvn -Pdev-mcp test -pl zestflow-mcp -q
    $mcpMvnOk = ($LASTEXITCODE -eq 0)
    Pop-Location
    Add-Phase "maven-mcp-unit-tests" $mcpMvnOk "exit=$LASTEXITCODE"
}

$e2eArgs = @{ BaseAdmin = $BaseAdmin }
if ($RequireLlm) { $e2eArgs.RequireLlm = $true; $e2eArgs.UseMockLlm = $true }
elseif ($UseMockLlm) { $e2eArgs.UseMockLlm = $true }
else { $e2eArgs.AllowLlmSkip = $true }
& "$PSScriptRoot\run-ai-copilot-e2e.ps1" @e2eArgs
Add-Phase "ai-copilot-api-e2e" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"

if (-not $SkipMcpE2e) {
    & "$PSScriptRoot\run-ai-mcp-e2e.ps1" -BaseExecutor $BaseExecutor
    Add-Phase "ai-mcp-e2e" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"
}

if (-not $SkipPerf) {
    $perfArgs = @{}
    if ($UseMockLlm -or $RequireLlm) { $perfArgs.UseMockLlm = $true }
    & "$PSScriptRoot\run-ai-copilot-perf.ps1" @perfArgs
    Add-Phase "ai-copilot-perf" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"
}

$report = @{
    timestamp = (Get-Date).ToString("o")
    exitCode  = $script:exitCode
    phases    = $phases
    spec      = "docs/AI_COPILOT_ACCEPTANCE.md"
    matrix    = @{
        adminChainCopilot = "explain/suggest/validate/expression/diagnose"
        devComponentAi    = "MCP scaffold_component + CLI export (Admin scaffold removed)"
        ragUsage          = "rag CRUD + import/export + usage quota"
        perf              = "validate/rag concurrent load (+ optional mock explain)"
    }
}
Set-Content -Path $ReportJson -Value ($report | ConvertTo-Json -Depth 6) -Encoding UTF8
Write-Host "Acceptance report: $ReportJson" -ForegroundColor Cyan
exit $script:exitCode
