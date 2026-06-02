# 验证 Maven Central release 构件（无需 GPG / Sonatype Token）
# 用法: powershell -File scripts/maven/verify-release.ps1

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $Root

function Resolve-Java17 {
    $candidates = @(
        "D:\IT\JDK17\jdk-17.0.19+10",
        "D:\IT\JDK17",
        "D:\IT\JAVA\JAVA17",
        "C:\Program Files\Java\jdk-17",
        "C:\Program Files\Eclipse Adoptium\jdk-17*",
        $env:JAVA_HOME
    ) | Where-Object { $_ }

    foreach ($c in $candidates) {
        if ($c -like "*`*") {
            $parent = Split-Path $c -Parent
            $pattern = Split-Path $c -Leaf
            if (Test-Path $parent) {
                $found = Get-ChildItem $parent -Directory -ErrorAction SilentlyContinue |
                    Where-Object { $_.Name -like $pattern } |
                    Sort-Object Name -Descending |
                    Select-Object -First 1
                if ($found) { $c = $found.FullName }
            }
        }
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

$javaHome = Resolve-Java17
if (-not $javaHome) {
    Write-Error "未找到 JDK 17。请安装 JDK 17 并设置 JAVA_HOME，或安装到 D:\IT\JAVA\JAVA17"
}
$env:JAVA_HOME = $javaHome
$env:PATH = "$javaHome\bin;$env:PATH"

Write-Host "JAVA_HOME=$env:JAVA_HOME" -ForegroundColor Cyan
& java -version

Write-Host "`n>>> mvn clean verify -Prelease -DskipTests -Dgpg.skip=true" -ForegroundColor Green
mvn clean verify -Prelease -DskipTests "-Dgpg.skip=true"

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "`n[OK] release 构件验证通过（jar / sources / javadoc 已生成，未签名）" -ForegroundColor Green
Write-Host "明日私钥就绪后执行: powershell -File scripts/maven/publish-central.ps1" -ForegroundColor Yellow
