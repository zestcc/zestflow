# Copilot P5 部署验收：单元测试 + API 黑盒 +（可选）LLM 全链路
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [switch]$SkipMavenTest,
    [switch]$RequireLlm
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

Write-Host "=== Copilot Deployment Acceptance ===" -ForegroundColor Cyan

if (-not $SkipMavenTest) {
    Push-Location $Root
    & mvn test -pl zestflow-admin -q "-Dtest=AiRagServiceTest,AiCopilotControllerTest"
    $mvnOk = ($LASTEXITCODE -eq 0)
    Pop-Location
    Add-Phase "maven-ai-unit-tests" $mvnOk "exit=$LASTEXITCODE"
}

if ($RequireLlm) {
    & "$PSScriptRoot\run-ai-copilot-e2e.ps1" -BaseAdmin $BaseAdmin -UseMockLlm
} else {
    & "$PSScriptRoot\run-ai-copilot-e2e.ps1" -BaseAdmin $BaseAdmin -AllowLlmSkip
}
Add-Phase "ai-copilot-api-e2e" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE requireLlm=$RequireLlm"

$report = @{
    timestamp = (Get-Date).ToString("o")
    exitCode  = $script:exitCode
    phases    = $phases
    matrix    = @{
        flywayV5        = "ai-flyway-v5-rag-table / rag CRUD in e2e"
        settingsTabs    = "ai-settings-tab-*-api + ai-settings-ui-bundle"
        designerCopilot = "ai-design-explain/suggest + ai-expression-suggest (-RequireLlm uses mock-llm-server)"
        p6RagUsage      = "rag import/export + usage quota fields"
    }
}
Set-Content -Path $ReportJson -Value ($report | ConvertTo-Json -Depth 6) -Encoding UTF8
Write-Host "Acceptance report: $ReportJson" -ForegroundColor Cyan
exit $script:exitCode
