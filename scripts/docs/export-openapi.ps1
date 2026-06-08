# 导出 Admin OpenAPI 3 JSON
# 用法：Admin 已启动（local profile）后执行
#   powershell -File scripts/docs/export-openapi.ps1
#   powershell -File scripts/docs/export-openapi.ps1 -BaseUrl http://127.0.0.1:8080

param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Output = "docs/openapi/admin-api.json"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

$docsUrl = "$BaseUrl/v3/api-docs"
Write-Host "Fetching OpenAPI from $docsUrl ..."

try {
    $response = Invoke-WebRequest -Uri $docsUrl -UseBasicParsing -TimeoutSec 30
} catch {
    Write-Error "无法获取 OpenAPI。请先启动 Admin（mvn spring-boot:run -pl zestflow-admin -Dspring-boot.run.profiles=local）。原始错误: $_"
}

$outDir = Split-Path -Parent $Output
if ($outDir -and -not (Test-Path $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
}

# 格式化 JSON 便于 git diff
$json = $response.Content | ConvertFrom-Json | ConvertTo-Json -Depth 100
[System.IO.File]::WriteAllText((Join-Path $root $Output), $json, [System.Text.UTF8Encoding]::new($false))

Write-Host "Written: $Output ($($response.RawContentLength) bytes raw)"
Write-Host "Swagger UI: $BaseUrl/swagger-ui.html"
