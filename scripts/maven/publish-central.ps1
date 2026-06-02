# Maven Central 正式发布（需 ~/.m2/settings.xml + GPG 私钥）
# 用法: powershell -File scripts/maven/publish-central.ps1

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $Root

function Resolve-Java17 {
    $candidates = @(
        "D:\IT\JDK17\jdk-17.0.19+10",
        "D:\IT\JDK17",
        "D:\IT\JAVA\JAVA17",
        "C:\Program Files\Java\jdk-17",
        $env:JAVA_HOME
    ) | Where-Object { $_ }

    foreach ($c in $candidates) {
        $java = Join-Path $c "bin\java.exe"
        if (-not (Test-Path $java) -and (Test-Path $c)) {
            $nested = Get-ChildItem $c -Directory -ErrorAction SilentlyContinue |
                Where-Object { Test-Path (Join-Path $_.FullName "bin\java.exe") } |
                Sort-Object Name -Descending |
                Select-Object -First 1
            if ($nested) { $java = Join-Path $nested.FullName "bin\java.exe" }
        }
        if (Test-Path $java) {
            $ver = cmd /c "`"$java`" -version 2>&1"
            if ($ver -match 'version "17') { return (Split-Path (Split-Path $java -Parent) -Parent) }
        }
    }
    return $null
}

function Resolve-Gpg {
    $paths = @(
        "gpg",
        "$env:ProgramFiles\GnuPG\bin\gpg.exe",
        "$env:ProgramFiles\Gpg4win\bin\gpg.exe"
    )
    foreach ($p in $paths) {
        if ($p -eq "gpg") {
            $cmd = Get-Command gpg -ErrorAction SilentlyContinue
            if ($cmd) { return $cmd.Source }
        } elseif (Test-Path $p) { return $p }
    }
    return $null
}

$javaHome = Resolve-Java17
if (-not $javaHome) {
    Write-Error "未找到 JDK 17。请设置 JAVA_HOME 后重试。"
}
$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"

$gpg = Resolve-Gpg
if (-not $gpg) {
    Write-Error @"
未找到 gpg。请安装 Gpg4win: https://www.gpg4win.org/download.html
私钥导入示例:
  gpg --import your-private-key.asc
  gpg --list-secret-keys --keyid-format LONG
"@
}

$secretKeys = & $gpg --list-secret-keys --keyid-format LONG 2>&1 | Out-String
if ($secretKeys -notmatch "5B28B71AF1128C97|sec") {
    Write-Error @"
本机未找到可用的 GPG 私钥（期望 Key ID 5B28B71AF1128C97）。
请从生成密钥的机器导出后导入:
  gpg --export-secret-keys 5B28B71AF1128C97 > zestflow-secret.asc
  gpg --import zestflow-secret.asc
"@
}

$settings = Join-Path $env:USERPROFILE ".m2\settings.xml"
if (-not (Test-Path $settings)) {
    Write-Error "缺少 $settings ，请复制 maven/settings.xml.example 并填入 Central Portal User Token 与 GPG 口令。"
}
if ((Get-Content $settings -Raw) -notmatch '<id>central</id>') {
    Write-Warning "settings.xml 中未找到 server id 'central'，请对照 maven/settings.xml.example 更新（OSSRH 已下线）。"
}

Write-Host "JAVA_HOME=$env:JAVA_HOME" -ForegroundColor Cyan
Write-Host "GPG=$gpg" -ForegroundColor Cyan
Write-Host "`n>>> mvn clean deploy -Prelease -DskipTests" -ForegroundColor Green
Write-Host "发布 9 个 artifact（admin / executor-test 已 skip deploy）`n" -ForegroundColor Gray

mvn clean deploy -Prelease -DskipTests

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host @"

[OK] 已上传到 Sonatype staging。
请在 Central Portal 确认 Release:
  https://central.sonatype.com/publishing

Central 索引（数分钟~2小时）:
  https://central.sonatype.com/namespace/cn.zestflow.www
  https://search.maven.org/search?q=g:cn.zestflow.www
"@ -ForegroundColor Green
