# 试验场全量场景批量执行 — 委托 run-full-e2e.ps1 场景段
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [ValidateSet('fullGreen', 'partialGreen', 'skipOnError')]
    [string]$E2eProfile = 'fullGreen',
    [int]$SceneTimeoutSec = 120,
    [switch]$AllowSkip,
    [switch]$SkipHeavyScenes
)

$ErrorActionPreference = "Continue"

Write-Host "=== Playground All Scenes E2E (profile=$E2eProfile) ===" -ForegroundColor Cyan

$params = @{
    BaseAdmin = $BaseAdmin
    E2eProfile = $E2eProfile
    SceneTimeoutSec = $SceneTimeoutSec
}
if ($SkipHeavyScenes) { $params.SkipHeavyScenes = $true }

& "$PSScriptRoot\run-full-e2e.ps1" @params
exit $LASTEXITCODE
