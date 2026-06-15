# 创建 Gitee Release 并上传 Admin 部署包（v1.0.0）
# 用法:
#   $env:GITEE_TOKEN = "<私人令牌>"
#   powershell -File scripts/deploy/publish-gitee-release.ps1
#   powershell -File scripts/deploy/publish-gitee-release.ps1 -Tag v1.0.0 -SkipUpload

param(
    [string]$Owner = "zestcc",
    [string]$Repo = "zestflow",
    [string]$Tag = "v1.0.0",
    [string]$Token = $(if ($env:GITEE_TOKEN) { $env:GITEE_TOKEN } else { $env:GITEE_ACCESS_TOKEN }),
    [string]$BodyFile = "",
    [switch]$SkipUpload
)

$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$DeployRoot = Join-Path $Root "deploy"

function Get-VersionFromTag([string]$t) {
    if ($t -match '^v?(\d+\.\d+\.\d+)$') { return $Matches[1] }
    throw "Invalid tag: $t"
}

function Invoke-GiteeApi {
    param([string]$Method, [string]$Path, [hashtable]$Query = @{}, [object]$JsonBody = $null)
    if (-not $Token) { throw "Set GITEE_TOKEN or pass -Token" }
    $q = @($Query.GetEnumerator() | ForEach-Object { "{0}={1}" -f $_.Key, [uri]::EscapeDataString([string]$_.Value) })
    $q += "access_token=$Token"
    $uri = "https://gitee.com/api/v5$Path?" + ($q -join "&")
    $p = @{ Uri = $uri; Method = $Method; UseBasicParsing = $true }
    if ($null -ne $JsonBody) {
        $p.ContentType = "application/json;charset=UTF-8"
        $p.Body = ($JsonBody | ConvertTo-Json -Depth 6 -Compress)
    }
    return Invoke-RestMethod @p
}

$ver = Get-VersionFromTag $Tag
$linuxTar = Join-Path $DeployRoot "zestflow_admin_${ver}_linux.tar.gz"
$winZip = Join-Path $DeployRoot "zestflow_admin_${ver}_win.zip"
if (-not $BodyFile) { $BodyFile = Join-Path $Root "docs/RELEASE_v1.0.0.md" }
if (-not (Test-Path $BodyFile)) { throw "Release notes not found: $BodyFile" }
if (-not (Test-Path $linuxTar)) { throw "Missing $linuxTar — run scripts/deploy/package-admin.ps1 first" }
if (-not (Test-Path $winZip)) { throw "Missing $winZip — run scripts/deploy/package-admin.ps1 first" }

$body = Get-Content $BodyFile -Raw -Encoding UTF8
$name = "ZestFlow $ver"

Write-Host "Checking existing release tag=$Tag ..." -ForegroundColor Cyan
$existing = $null
try {
    $existing = Invoke-GiteeApi GET "/repos/$Owner/$Repo/releases/tags/$Tag"
} catch {}

if ($existing -and $existing.id) {
    $releaseId = $existing.id
    Write-Host "Release already exists id=$releaseId" -ForegroundColor Yellow
} else {
    Write-Host "Creating release $Tag ..." -ForegroundColor Cyan
    $created = Invoke-GiteeApi POST "/repos/$Owner/$Repo/releases" @{} @{
        tag_name = $Tag
        name     = $name
        body     = $body
        prerelease = $false
    }
    $releaseId = $created.id
    Write-Host "Created release id=$releaseId" -ForegroundColor Green
}

if ($SkipUpload) {
    Write-Host "SkipUpload set — done." -ForegroundColor Yellow
    exit 0
}

function Upload-Asset([string]$FilePath) {
    $fileName = Split-Path $FilePath -Leaf
    Write-Host "Uploading $fileName ..." -ForegroundColor DarkGray
    $uri = "https://gitee.com/api/v5/repos/$Owner/$Repo/releases/$releaseId/attach_files?access_token=$Token"
    Add-Type -AssemblyName System.Net.Http
    $client = New-Object System.Net.Http.HttpClient
    $content = New-Object System.Net.Http.MultipartFormDataContent
    $bytes = [System.IO.File]::ReadAllBytes($FilePath)
    $byteContent = New-Object System.Net.Http.ByteArrayContent($bytes)
    $byteContent.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::Parse("application/octet-stream")
    $content.Add($byteContent, "file", $fileName)
    $resp = $client.PostAsync($uri, $content).GetAwaiter().GetResult()
    $text = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
    if (-not $resp.IsSuccessStatusCode) {
        throw "Upload failed $fileName status=$($resp.StatusCode) body=$text"
    }
    Write-Host "  OK $fileName" -ForegroundColor Green
}

Upload-Asset $linuxTar
Upload-Asset $winZip

Write-Host ""
Write-Host "Gitee Release ready:" -ForegroundColor Green
Write-Host "  https://gitee.com/$Owner/$Repo/releases/tag/$Tag"
