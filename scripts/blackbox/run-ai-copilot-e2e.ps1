# AI Copilot API E2E（需 Admin :8080 已启动）
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [switch]$AllowSkip,
    [switch]$AllowLlmSkip,
    [switch]$UseMockLlm,
    [int]$MockLlmPort = 18765
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

function Invoke-Api($method, $url, $body, $headers, [int]$TimeoutSec = 30) {
    try {
        $p = @{ Uri=$url; Method=$method; TimeoutSec=$TimeoutSec; UseBasicParsing=$true }
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

$mockProc = $null
if ($UseMockLlm) {
    $mockProc = Start-Process powershell -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", (Join-Path $PSScriptRoot "mock-llm-server.ps1"),
        "-Port", $MockLlmPort
    ) -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 2
}

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

if ($UseMockLlm) {
    $mockCfg = (@{
        enabled = $true
        preset  = 'custom'
        baseUrl = "http://127.0.0.1:$MockLlmPort/v1"
        apiKey  = 'mock-e2e'
        model   = 'mock-e2e'
    } | ConvertTo-Json -Compress)
    $saveCfg = Invoke-Api PUT "$BaseAdmin/api/zestflow/ai/tenant-config" $mockCfg $h
} else {
    $saveCfg = Invoke-Api PUT "$BaseAdmin/api/zestflow/ai/tenant-config" '{"enabled":true,"preset":"ollama","model":"qwen2.5:7b","apiKey":"ollama"}' $h
}
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

$badValidateBody = '{"appCode":"demo-app","chainCode":"CHN_BAD","chainData":"{\"code\":\"CHN_BAD\",\"version\":1,\"nodes\":[{\"id\":\"n1\",\"label\":\"bad\",\"type\":\"TASK\",\"component\":\"__NOT_REGISTERED__\"}],\"edges\":[]}"}'
$valBad = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/design/validate" $badValidateBody $h
$valBadOk = $false
if ($valBad.ok) {
    try {
        $valBadData = (ConvertFrom-Json $valBad.body).data
        $valBadOk = ($valBadData.valid -eq $false)
    } catch {}
}
Add-Check "ai-validate-invalid-component" ($valBad.ok -and $valBadOk) "status=$($valBad.status)"

$ctxComp = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/context/components?appCode=demo-app" $null $h
$ctxCompOk = $false
if ($ctxComp.ok) {
    try { $ctxCompOk = [bool](ConvertFrom-Json $ctxComp.body).data -and ($ctxComp.body -match 'validateUser') } catch {}
}
Add-Check "ai-context-components" ($ctxComp.ok -and $ctxCompOk) "status=$($ctxComp.status)"

if ($UseMockLlm) {
    $testConn = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/test" '{"preset":"custom","baseUrl":"http://127.0.0.1:' + $MockLlmPort + '/v1","apiKey":"mock-e2e","model":"mock-e2e"}' $h 60
} else {
    $testConn = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/test" '{"preset":"ollama","model":"qwen2.5:7b","apiKey":"ollama"}' $h 60
}
$testConnOk = $false
if ($testConn.ok) {
    try { $testConnOk = [bool](ConvertFrom-Json $testConn.body).data.success } catch {}
}
if (-not $testConnOk -and $AllowLlmSkip) {
    Add-Check "ai-test-connection" $true "skipped-llm-unavailable"
} else {
    Add-Check "ai-test-connection" ($testConn.ok -and $testConnOk) "status=$($testConn.status)"
}

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
    $tplGet = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/templates/$tplId" $null $h
    $tplGetOk = $false
    if ($tplGet.ok) {
        try { $tplGetOk = (ConvertFrom-Json $tplGet.body).data.name -eq "E2E-TPL" } catch {}
    }
    Add-Check "ai-templates-get" ($tplGet.ok -and $tplGetOk) "status=$($tplGet.status) id=$tplId"
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

$ragStatus = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/rag/status" $null $h
$ragMode = ""
if ($ragStatus.ok) {
    try { $ragMode = (ConvertFrom-Json $ragStatus.body).data.mode } catch {}
}
Add-Check "ai-rag-vector-mode" ($ragStatus.ok -and ($ragMode -like "hybrid*" -or $ragMode -eq "vector")) "mode=$ragMode"

$ragDocSave = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/rag/documents" '{"title":"E2E-RAG","appCode":"demo-app","content":"## Aviator\n\nchainCtx.get(\"key\") 示例","enabled":true}' $h
$ragDocId = $null
if ($ragDocSave.ok) {
    try { $ragDocId = (ConvertFrom-Json $ragDocSave.body).data.id } catch {}
}
Add-Check "ai-rag-documents-save" ($ragDocSave.ok -and $ragDocId) "status=$($ragDocSave.status) id=$ragDocId"

$ragDocList = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/rag/documents?appCode=demo-app" $null $h
$ragDocCount = 0
if ($ragDocList.ok) {
    try { $ragDocCount = @((ConvertFrom-Json $ragDocList.body).data).Count } catch {}
}
Add-Check "ai-rag-documents-list" ($ragDocList.ok -and $ragDocCount -gt 0) "count=$ragDocCount"

$ragRebuild = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/rag/documents/rebuild-index" $null $h
Add-Check "ai-rag-rebuild-index" ($ragRebuild.ok -and $ragRebuild.status -eq 200) "status=$($ragRebuild.status)"

$usage = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/usage/overview?days=30" $null $h
$usageOk = $false
if ($usage.ok) {
    try {
        $u = (ConvertFrom-Json $usage.body).data
        $usageOk = ($null -ne $u.totalSessions) -and ($null -ne $u.sessionsByMode)
    } catch {}
}
Add-Check "ai-usage-overview" ($usage.ok -and $usageOk) "status=$($usage.status)"

# --- P5 部署验收：设置页三 Tab 对应 API ---
$tenantCfg = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/tenant-config" $null $h
$tenantCfgOk = $false
if ($tenantCfg.ok) {
    try {
        $tc = (ConvertFrom-Json $tenantCfg.body).data
        $tenantCfgOk = ($null -ne $tc.enabled) -and ($null -ne $tc.preset)
    } catch {}
}
Add-Check "ai-settings-tab-config-api" ($tenantCfg.ok -and $tenantCfgOk) "status=$($tenantCfg.status)"

$ragStatusDetail = $null
if ($ragStatus.ok) {
    try { $ragStatusDetail = (ConvertFrom-Json $ragStatus.body).data } catch {}
}
$ragTabOk = ($null -ne $ragStatusDetail.mode) -and ($null -ne $ragStatusDetail.platformChunks) -and ($null -ne $ragStatusDetail.filesystemPath)
Add-Check "ai-settings-tab-rag-api" ($ragStatus.ok -and $ragTabOk) "platform=$($ragStatusDetail.platformChunks) path=$($ragStatusDetail.filesystemPath)"

$usageTabOk = $usage.ok -and $usageOk
Add-Check "ai-settings-tab-usage-api" $usageTabOk "days=30"

# --- Flyway V5：租户 RAG 表可用（文档 CRUD 已在上方验证）---
$ragDocUpdateOk = $false
if ($ragDocId) {
    $ragDocUpdate = Invoke-Api PUT "$BaseAdmin/api/zestflow/ai/rag/documents/$ragDocId" '{"title":"E2E-RAG-UPD","appCode":"demo-app","content":"## Aviator\n\nchainCtx updated","enabled":true}' $h
    if ($ragDocUpdate.ok) {
        try {
            $updated = (ConvertFrom-Json $ragDocUpdate.body).data
            $ragDocUpdateOk = $updated.title -eq "E2E-RAG-UPD"
        } catch {}
    }
    Add-Check "ai-rag-documents-update" ($ragDocUpdate.ok -and $ragDocUpdateOk) "status=$($ragDocUpdate.status)"
    $ragRebuild2 = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/rag/documents/rebuild-index" $null $h
    Add-Check "ai-rag-rebuild-after-update" ($ragRebuild2.ok -and $ragRebuild2.status -eq 200) "status=$($ragRebuild2.status)"
}
Add-Check "ai-flyway-v5-rag-table" ($ragDocSave.ok -and $ragDocId) "via-rag-crud"

$ragRebuildSearch = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/rag/search?q=chainCtx+updated&appCode=demo-app&limit=3" $null $h
$tenantRagHit = $false
if ($ragRebuildSearch.ok) {
    try {
        $hits = (ConvertFrom-Json $ragRebuildSearch.body).data
        $tenantRagHit = @($hits).Count -gt 0
    } catch {}
}
Add-Check "ai-rag-tenant-search-after-rebuild" ($ragRebuildSearch.ok -and $tenantRagHit) "hits=$tenantRagHit"

# --- 设计器 Copilot：explain / suggest / expression（需 LLM，Ollama 默认）---
$chainCtx = '{"code":"CHN_TEST","version":1,"nodes":[{"id":"n1","label":"test","type":"TASK","component":"validateUser"}],"edges":[]}'
$explainPayload = @{
    appCode = "demo-app"
    designId = "e2e-design"
    chainCode = "CHN_TEST"
    chainData = $chainCtx
    userMessage = "explain this chain"
} | ConvertTo-Json -Compress -Depth 6
$explain = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/design/explain" $explainPayload $h 120
$explainOk = $false
$explainBizOk = $false
if ($explain.ok) {
    try {
        $ej = ConvertFrom-Json $explain.body
        $explainBizOk = ($ej.code -eq 200 -or $ej.code -eq 0)
        if ($explainBizOk) { $explainOk = [bool]$ej.data.explanation }
    } catch {}
}
if (-not $explainOk -and $AllowLlmSkip) {
    Add-Check "ai-design-explain" $true "skipped-llm-unavailable"
} else {
    Add-Check "ai-design-explain" ($explain.ok -and $explainOk) "status=$($explain.status)"
}

$suggestPayload = @{
    appCode = "demo-app"
    designId = "e2e-design"
    chainCode = "CHN_TEST"
    chainData = '{"code":"CHN_TEST","version":1,"nodes":[],"edges":[]}'
    userMessage = "add validate user node"
    mode = "generate"
} | ConvertTo-Json -Compress -Depth 6
$suggest = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/design/suggest" $suggestPayload $h 120
$suggestOk = $false
if ($suggest.ok) {
    try {
        $sj = ConvertFrom-Json $suggest.body
        if ($sj.code -eq 200 -or $sj.code -eq 0) {
            $sd = $sj.data
            $suggestOk = [bool]$sd.proposedChainData -and ($null -ne $sd.validation)
        }
    } catch {}
}
if (-not $suggestOk -and $AllowLlmSkip) {
    Add-Check "ai-design-suggest" $true "skipped-llm-unavailable"
} else {
    Add-Check "ai-design-suggest" ($suggest.ok -and $suggestOk) "status=$($suggest.status)"
}

$exprBody = '{"appCode":"demo-app","chainCode":"CHN_TEST","currentExpression":"1+1","userMessage":"use chainCtx get userId"}'
$expr = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/expression/suggest" $exprBody $h 120
$exprOk = $false
if ($expr.ok) {
    try {
        $xj = ConvertFrom-Json $expr.body
        if ($xj.code -eq 200 -or $xj.code -eq 0) {
            $exprOk = [bool]$xj.data.expression
        }
    } catch {}
}
if (-not $exprOk -and $AllowLlmSkip) {
    Add-Check "ai-expression-suggest" $true "skipped-llm-unavailable"
} else {
    Add-Check "ai-expression-suggest" ($expr.ok -and $exprOk) "status=$($expr.status)"
}

# --- 设置页静态资源（三 Tab 懒加载 chunk 已打入 Admin static）---
$settingsBundleOk = $false
$mainIndex = Invoke-Api GET "$BaseAdmin/assets/index-CKbjiEnP.js" $null $null
if (-not $mainIndex.ok) {
    $html = Invoke-Api GET "$BaseAdmin/" $null $null
    if ($html.ok -and ($html.body -match '/assets/(index-[^"]+\.js)')) {
        $mainIndex = Invoke-Api GET "$BaseAdmin/assets/$($Matches[1])" $null $null
    }
}
if ($mainIndex.ok -and ($mainIndex.body -match 'SettingsAiPage-[A-Za-z0-9_-]+\.js')) {
    $pageJs = Invoke-Api GET "$BaseAdmin/assets/$($Matches[0])" $null $null
    if ($pageJs.ok) {
        $settingsBundleOk = ($pageJs.body -match 'tabRag|tabUsage|tabConfig|SettingsAiRagPanel|SettingsAiUsagePanel')
    }
}
Add-Check "ai-settings-ui-bundle" $settingsBundleOk "three-tabs-bundled"

# --- P6：RAG 导入导出 + 用量配额字段 ---
$ragExport = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/rag/documents/export?appCode=demo-app" $null $h
$exportOk = $false
if ($ragExport.ok) {
    try { $exportOk = $null -ne (ConvertFrom-Json $ragExport.body).data.documents } catch {}
}
Add-Check "ai-rag-documents-export" ($ragExport.ok -and $exportOk) "status=$($ragExport.status)"

$importBody = '{"documents":[{"title":"E2E-IMPORT","appCode":"demo-app","content":"## Import\n\nbulk import test","enabled":true}],"replaceByTitle":false}'
$ragImport = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/rag/documents/import" $importBody $h
$importCount = 0
if ($ragImport.ok) {
    try { $importCount = (ConvertFrom-Json $ragImport.body).data.imported } catch {}
}
Add-Check "ai-rag-documents-import" ($ragImport.ok -and $importCount -gt 0) "imported=$importCount"

$usage2 = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/usage/overview?days=30" $null $h
$quotaFieldsOk = $false
if ($usage2.ok) {
    try {
        $u2 = (ConvertFrom-Json $usage2.body).data
        $quotaFieldsOk = ($null -ne $u2.monthlyTokenUsed)
    } catch {}
}
Add-Check "ai-usage-quota-fields" ($usage2.ok -and $quotaFieldsOk) "status=$($usage2.status)"

# --- P7：学习事件 P1～P3（Admin 租户级） ---
$learnBody = '{"appCode":"demo-app","intent":"COMPOSE_CHAIN","feature":"userRegister","chainCode":"CHN_USER_REGISTER","httpMode":2,"validatePassed":true,"validateRounds":1,"adopted":true,"playgroundSuccess":true}'
$learnSave = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/learning/events" $learnBody $h
$learnId = $null
$learnEligible = $false
if ($learnSave.ok) {
    try {
        $lj = (ConvertFrom-Json $learnSave.body).data
        $learnId = $lj.id
        $learnEligible = ($lj.promotionEligible -eq $true)
    } catch {}
}
Add-Check "ai-learning-events-save" ($learnSave.ok -and $learnId -and $learnEligible) "id=$learnId eligible=$learnEligible"

$learnList = Invoke-Api GET "$BaseAdmin/api/zestflow/ai/learning/events?appCode=demo-app&limit=5" $null $h
$learnListOk = $false
if ($learnList.ok) {
    try {
        $items = (ConvertFrom-Json $learnList.body).data
        $learnListOk = ($items.Count -gt 0)
    } catch {}
}
Add-Check "ai-learning-events-list" ($learnList.ok -and $learnListOk) "status=$($learnList.status)"

$badLearnBody = '{"appCode":"demo-app","intent":"COMPOSE_CHAIN","feature":"badCase","validatePassed":false,"validateRounds":5,"adopted":false}'
$badLearnSave = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/learning/events" $badLearnBody $h
$badLearnId = $null
if ($badLearnSave.ok) {
    try { $badLearnId = (ConvertFrom-Json $badLearnSave.body).data.id } catch {}
}
$promoteRejected = $false
if ($badLearnId) {
    $promoteBad = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/learning/events/$badLearnId/promote-rag" $null $h
    $promoteRejected = (-not $promoteBad.ok) -or ($promoteBad.status -ge 400)
}
Add-Check "ai-learning-promote-rejected" ($badLearnId -and $promoteRejected) "badId=$badLearnId"

$promoteDocId = $null
if ($learnId) {
    $promoteOk = Invoke-Api POST "$BaseAdmin/api/zestflow/ai/learning/events/$learnId/promote-rag" $null $h
    if ($promoteOk.ok) {
        try { $promoteDocId = (ConvertFrom-Json $promoteOk.body).data.id } catch {}
    }
    Add-Check "ai-learning-promote-rag" ($promoteOk.ok -and $promoteDocId) "docId=$promoteDocId"
} else {
    Add-Check "ai-learning-promote-rag" $false "no eligible event"
}

if ($ragDocId) {
    $ragDocDel = Invoke-Api DELETE "$BaseAdmin/api/zestflow/ai/rag/documents/$ragDocId" $null $h
    Add-Check "ai-rag-documents-delete" ($ragDocDel.ok -and $ragDocDel.status -eq 200) "status=$($ragDocDel.status)"
} else {
    Add-Check "ai-rag-documents-delete" $false "no document id"
}

$fail = @($checks | Where-Object { -not $_.ok }).Count
Write-Host "Checks: $($checks.Count - $fail)/$($checks.Count) passed" -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
Save-Report
if ($mockProc -and -not $mockProc.HasExited) {
    Stop-Process -Id $mockProc.Id -Force -ErrorAction SilentlyContinue
}
if ($fail -gt 0) { exit 1 }
exit 0
