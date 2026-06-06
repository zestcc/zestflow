# AI Copilot API E2E（需 Admin :8080 已启动）
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [switch]$AllowSkip
)

$ErrorActionPreference = "Continue"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$ReportJson = Join-Path $OutDir ("ai-copilot-e2e-{0}.json" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
$checks = New-Object System.Collections.Generic.List[object]

function Add-Check($name, $ok, $note) {
    $checks.Add([pscustomobject]@{ name=$name; ok=$ok; note=$note }) | Out-Null
}
function Save-Report {
    $out = @{ timestamp=(Get-Date).ToString("o"); checks=$checks }
    Set-Content -Path $ReportJson -Value ($out | ConvertTo-Json -Depth 6) -Encoding UTF8
    Write-Host "Saved: $ReportJson"
}

function Invoke-Api($method, $url, $body, $headers) {
    try {
        $p = @{ Uri=$url; Method=$method; TimeoutSec=30; UseBasicParsing=$true }
        if ($headers) { $p.Headers = $headers }
        if ($null -ne $body) { $p.Body = $body; $p.ContentType = "application/json" }
        $r = Invoke-WebRequest @p
        return @{ ok=$true; status=[int]$r.StatusCode; body=$r.Content }
    } catch {
        $st = 0; $b = ""
        if ($_.Exception.Response) {
            $st = [int]$_.Exception.Response.StatusCode.value__
            try { $b = (New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())).ReadToEnd() } catch {}
        }
        return @{ ok=$false; status=$st; body=$b }
    }
}

Write-Host "=== AI Copilot E2E ===" -ForegroundColor Cyan

$login = Invoke-Api POST "$BaseAdmin/api/zestflow/auth/login" '{"username":"admin","password":"admin123"}' $null
$token = $null
if ($login.ok) { try { $token = (ConvertFrom-Json $login.body).data.token } catch {} }
if (-not $token) {
    Add-Check "login" $false "cannot login"
    Save-Report
    if (-not $AllowSkip) { exit 1 }
    exit 2
}
Add-Check "login" $true ""
$h = @{ Authorization = "Bearer $token" }

$feat = Invoke-Api GET "$BaseAdmin/api/zestflow/system/features" $null $h
$copilotGlobal = $false
if ($feat.ok) {
    try {
        $fj = ConvertFrom-Json $feat.body
        if ($fj.copilot) { $copilotGlobal = [bool]$fj.copilot.globallyEnabled }
    } catch {}
}
Add-Check "copilot-globally-enabled" $copilotGlobal "globallyEnabled=$copilotGlobal"

$cfg = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/config" $null $h
Add-Check "ai-config" ($cfg.ok -and $cfg.status -eq 200) "status=$($cfg.status)"

$providers = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/providers" $null $h
$providerCount = 0
if ($providers.ok) {
    try { $providerCount = @((ConvertFrom-Json $providers.body).data).Count } catch {}
}
Add-Check "ai-providers-list" ($providerCount -ge 20) "count=$providerCount"

$saveCfg = Invoke-Api PUT "$BaseAdmin/api/zestflow/ai/tenant-config" '{"enabled":true,"preset":"ollama","model":"qwen2.5:7b","apiKey":"ollama"}' $h
Add-Check "ai-tenant-config-save" ($saveCfg.ok -and $saveCfg.status -eq 200) "status=$($saveCfg.status)"

$validateBody = '{"appCode":"demo-app","chainCode":"CHN_TEST","chainData":"{\"code\":\"CHN_TEST\",\"version\":1,\"nodes\":[{\"id\":\"n1\",\"label\":\"test\",\"type\":\"TASK\",\"component\":\"validateUser\"}],\"edges\":[]}"}'
$val = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/design/validate" $validateBody $h
$valOk = $false
$valValid = $null
if ($val.ok) {
    try {
        $valData = (ConvertFrom-Json $val.body).data
        $valValid = $valData.valid
        $valOk = ($null -ne $valValid) -and ($null -ne $valData.errors)
    } catch {}
}
Add-Check "ai-validate-chain" ($val.ok -and $valOk) "status=$($val.status) valid=$valValid"

$scaffold = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/component/scaffold" '{"appCode":"demo-app","componentId":"deductStock","componentType":"EXECUTOR","groupName":"order","description":"deduct stock"}' $h
$hasJava = $false
if ($scaffold.ok) {
    try { $hasJava = [bool](ConvertFrom-Json $scaffold.body).data.fullJavaCode } catch {}
}
Add-Check "ai-component-scaffold" ($scaffold.ok -and $hasJava) "status=$($scaffold.status)"

$chainKeys = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/context/chain-keys?appCode=demo-app" $null $h
$chainKeysOk = $false
if ($chainKeys.ok) {
    try {
        $ck = (ConvertFrom-Json $chainKeys.body).data
        $chainKeysOk = ($null -ne $ck.declaredKeys) -and ($null -ne $ck.adminKeys)
    } catch {}
}
Add-Check "ai-chain-key-hints" ($chainKeys.ok -and $chainKeysOk) "status=$($chainKeys.status)"

$tplList = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/templates?appCode=demo-app" $null $h
Add-Check "ai-templates-list" ($tplList.ok -and $tplList.status -eq 200) "status=$($tplList.status)"

$tplSave = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/templates" '{"name":"E2E-TPL","appCode":"demo-app","description":"e2e","chainData":"{\"nodes\":[],\"edges\":[]}"}' $h
$tplId = $null
if ($tplSave.ok) {
    try { $tplId = (ConvertFrom-Json $tplSave.body).data.id } catch {}
}
Add-Check "ai-templates-save" ($tplSave.ok -and $tplId) "status=$($tplSave.status) id=$tplId"

if ($tplId) {
    $tplDel = Invoke-Api DELETE "$BaseAdmin/api/zestflow/ai/templates/$tplId" $null $h
    Add-Check "ai-templates-delete" ($tplDel.ok -and $tplDel.status -eq 200) "status=$($tplDel.status)"
} else {
    Add-Check "ai-templates-delete" $false "no template id"
}

$diag = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/logs/diagnose" '{"appCode":"demo-app","executionId":"e2e-missing-exec","errorSummary":"E2E synthetic failure"}' $h
$hasDiagnosis = $false
if ($diag.ok) {
    try { $hasDiagnosis = [bool](ConvertFrom-Json $diag.body).data.diagnosis } catch {}
}
Add-Check "ai-logs-diagnose" ($diag.ok -and $hasDiagnosis) "status=$($diag.status)"

$rag = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/rag/search?q=Aviator+chainCtx&limit=2" $null $h
$ragOk = $false
if ($rag.ok) {
    try {
        $ragOk = @((ConvertFrom-Json $rag.body).data).Count -gt 0
    } catch {}
}
Add-Check "ai-rag-search" ($rag.ok -and $ragOk) "status=$($rag.status)"

$fail = @($checks | Where-Object { -not $_.ok }).Count
Write-Host "Checks: $($checks.Count - $fail)/$($checks.Count) passed" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
Save-Report
if ($fail -gt 0) { exit 1 }
exit 0
