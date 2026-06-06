# 兼容入口 �?请优先使�?run-security-token-e2e.ps1
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$ValidToken = "",
    [string]$RegistryPath = "/api/zestflow/registry/executor/heartbeat",
    [switch]$AllowSkip
)

$token = if ([string]::IsNullOrWhiteSpace($ValidToken)) { "e2e-security-registry-token" } else { $ValidToken }
& "$PSScriptRoot\run-security-token-e2e.ps1" `
    -BaseAdmin $BaseAdmin `
    -RegistryToken $token `
    -RegistryPath $RegistryPath `
    -AllowSkip:$AllowSkip `
    -SkipExecutorTests
exit $LASTEXITCODE
