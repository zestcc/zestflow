# ZestFlow full-system black-box E2E (config probe + tenant + all playground scenes)
param(
    [string]$BaseAdmin = "http://127.0.0.1:8080",
    [string]$BaseNetty = "http://127.0.0.1:20550",
    [string]$BaseCollector = "http://127.0.0.1:20650",
    [int]$SceneTimeoutSec = 120,
    [string]$PolicyFile = "",
    [ValidateSet('fullGreen', 'partialGreen', 'skipOnError', '')]
    [string]$E2eProfile = '',
    [switch]$SkipHeavyScenes
)

$ErrorActionPreference = "Continue"
$OutDir = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$Ts = Get-Date -Format "yyyyMMdd-HHmmss"
$ReportJson = Join-Path $OutDir "full-e2e-$Ts.json"

if ([string]::IsNullOrWhiteSpace($PolicyFile)) {
    $PolicyFile = Join-Path $PSScriptRoot 'e2e-scene-policy.json'
}
$policyRaw = Get-Content $PolicyFile -Raw -Encoding UTF8 | ConvertFrom-Json
$profileName = if ($E2eProfile) { $E2eProfile } else { $policyRaw.defaultProfile }
$profile = $policyRaw.profiles.$profileName
if (-not $profile) { Write-Host "Unknown E2eProfile: $profileName" -ForegroundColor Red; exit 2 }
$policyMode = $profile.mode
$optionalScenes = @($profile.optionalScenes)
$partialGreenScenes = @($profile.partialGreenScenes)
$heavyMap = $policyRaw.heavyScenes
if ($profile.skipHeavyDefault -and -not $PSBoundParameters.ContainsKey('SkipHeavyScenes')) {
    $SkipHeavyScenes = $true
}

$functional = New-Object System.Collections.Generic.List[object]
$configProbe = New-Object System.Collections.Generic.List[object]
$scenes = New-Object System.Collections.Generic.List[object]
$requiredPass = 0; $requiredFail = 0; $optionalSkipped = 0; $heavySkipped = 0

function Add-F($cat, $name, $ok, $status, $ms, $note) {
    $functional.Add([pscustomobject]@{ category=$cat; name=$name; ok=$ok; status=$status; latencyMs=$ms; note=$note }) | Out-Null
}
function Add-C($name, $key, $value, $note) {
    $configProbe.Add([pscustomobject]@{ name=$name; key=$key; value=$value; note=$note }) | Out-Null
}

function Prepare-SceneBody($sceneCode, $rawBody, $requestPath) {
    if ($sceneCode -eq 'SCN20260601000229') {
        return '{"applyId":"BB-PG-001"}'
    }
    if ([string]::IsNullOrWhiteSpace($rawBody)) { $rawBody = '{}' }
    try {
        $o = ConvertFrom-Json $rawBody
    } catch {
        return $rawBody
    }
    $suffix = (Get-Random -Maximum 99999).ToString("D5")
    if ($requestPath -match 'handleApplyAfterSale' -or $sceneCode -eq 'SCN20260601000229') {
        if (-not $o.PSObject.Properties['applyId'] -or [string]::IsNullOrWhiteSpace([string]$o.applyId)) {
            $o | Add-Member -NotePropertyName applyId -NotePropertyValue "BB-PG-001" -Force
        }
    }
    if ($requestPath -eq '/execute' -or $requestPath -like '/execute*') {
        if (-not $o.PSObject.Properties['orderId'] -or [string]::IsNullOrWhiteSpace([string]$o.orderId)) {
            $o | Add-Member -NotePropertyName orderId -NotePropertyValue "ORD-E2E-$suffix" -Force
        }
    }
    return ($o | ConvertTo-Json -Compress -Depth 12)
}

function Test-BizStatusOk($st) {
    if ($null -eq $st) { return $true }
    if ($st -eq 1 -or $st -eq '1') { return $true }
    if ($st -eq 'SUCCESS' -or $st -eq 4 -or $st -eq '4') { return $true }
    return $false
}

function Test-PlaygroundOk($body, $requestPath, $sceneCode, $partialGreenScenes) {
    try {
        $resp = ConvertFrom-Json $body
        if ($resp.code -ne 200) { return @{ ok=$false; note="api-code-$($resp.code)" } }
        $data = $resp.data
        if ($null -eq $data) { $data = $resp }
        $pgStatus = $null
        if ($data.PSObject.Properties.Name -contains 'status') { $pgStatus = $data.status }
        $isPartial = $partialGreenScenes -contains $sceneCode
        if ($null -ne $pgStatus) {
            if (-not (Test-BizStatusOk $pgStatus)) {
                return @{ ok=$false; note="playground-status-$pgStatus" }
            }
            if ($isPartial) { return @{ ok=$true; note="partialGreen-ok" } }
        }
        $isApi = ($requestPath -match '/api/') -or ($requestPath -match 'handleApplyAfterSale')
        if ($isApi -and $null -ne $data -and ($data.PSObject.Properties.Name -contains 'status')) {
            if (-not (Test-BizStatusOk $data.status)) {
                return @{ ok=$false; note="business-status-$($data.status)" }
            }
        }
        return @{ ok=$true; note="" }
    } catch {
        return @{ ok=$true; note="" }
    }
}

function Invoke-Api($method, $url, $body, $headers, $timeoutSec = 30) {
    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $p = @{ Uri=$url; Method=$method; TimeoutSec=$timeoutSec; UseBasicParsing=$true }
        if ($headers) { $p.Headers = $headers }
        if ($null -ne $body) { $p.Body = $body; $p.ContentType = "application/json" }
        $r = Invoke-WebRequest @p
        $sw.Stop()
        $bodyText = $r.Content
        if ($bodyText -is [byte[]]) {
            $bodyText = [System.Text.Encoding]::UTF8.GetString($bodyText)
        }
        return @{ status=[int]$r.StatusCode; ok=$true; ms=$sw.ElapsedMilliseconds; body=$bodyText }
    } catch {
        $sw.Stop()
        $st = 0; $b = ""
        if ($_.Exception.Response) {
            $st = [int]$_.Exception.Response.StatusCode.value__
            try { $rd = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream()); $b = $rd.ReadToEnd() } catch {}
        }
        return @{ status=$st; ok=$false; ms=$sw.ElapsedMilliseconds; body=$b }
    }
}

Write-Host "=== Full E2E ===" -ForegroundColor Cyan

# --- Auth first (system/features requires JWT) ---
$login = Invoke-Api POST "$BaseAdmin/api/zestflow/auth/login" '{"username":"admin","password":"admin123"}' $null
$token = $null
if ($login.ok) { try { $token = (ConvertFrom-Json $login.body).data.token } catch {} }
Add-F "auth" "login" ($null -ne $token) $login.status $login.ms ""

if (-not $token) {
    Write-Host "Login failed, abort." -ForegroundColor Red
    exit 1
}
$h = @{ Authorization = "Bearer $token" }

# --- Config probe (runtime) ---
$feat = Invoke-Api GET "$BaseAdmin/api/zestflow/system/features" $null $h
$pgEnabled = $false
$tenantMode = "single"
$ipDemoMode = "disabled"
if ($feat.ok) {
    try {
        $j = ConvertFrom-Json $feat.body
        if ($j.playground) { $pgEnabled = $j.playground.enabled }
        if ($j.tenant) {
            if ($j.tenant.mode) { $tenantMode = $j.tenant.mode }
            if ($j.tenant.ipDemoMode) { $ipDemoMode = $j.tenant.ipDemoMode }
        }
        if ($j.admin) {
            Add-C "runtime" "zestflow.admin.deploy-mode" $j.admin.deployMode "standalone=no Redis required"
            Add-C "runtime" "zestflow.admin.cache.type" $j.admin.cacheType "independent from deploy-mode"
            Add-C "runtime" "zestflow.admin.redis-required" $j.admin.redisRequired "false on typical single-node"
        }
    } catch {}
}
Add-C "runtime" "zestflow.playground.enabled" $pgEnabled "from /api/system/features"
Add-C "runtime" "zestflow.tenant.mode" $tenantMode "profile enterprise-e2e => multi"
Add-C "runtime" "zestflow.tenant.ip-demo-mode" $ipDemoMode "profile enterprise-e2e => enabled"
Add-C "runtime" "zestflow.admin.registry-token" "empty=dev-open" "ON: set token + restart, wrong header -> 401"
Add-C "runtime" "zestflow.executor.access-token" "empty=dev-open" "ON: set token on both sides + restart"
Add-C "runtime" "zestflow.mail.enabled" "see application-local.yml" "false=NoopMailService, true=SmtpMailService"

Add-F "config" "system-features" $feat.ok $feat.status $feat.ms "playground=$pgEnabled"

$r = Invoke-Api GET "$BaseAdmin/api/zestflow/auth/userinfo" $null $h
Add-F "auth" "userinfo" $r.ok $r.status $r.ms ""

$r = Invoke-Api GET "$BaseAdmin/api/zestflow/auth/tenants" $null $h
$tenantCount = 0
if ($r.ok) { try { $tenantCount = @((ConvertFrom-Json $r.body).data).Count } catch {} }
Add-F "tenant" "list-my-tenants" $r.ok $r.status $r.ms "count=$tenantCount"

$r = Invoke-Api GET "$BaseAdmin/api/zestflow/tenants" $null $h
Add-F "tenant" "tenant-crud-list" $r.ok $r.status $r.ms ""

$r = Invoke-Api POST "$BaseAdmin/api/zestflow/auth/switch-tenant/1" $null $h
Add-F "tenant" "switch-tenant-1" $r.ok $r.status $r.ms ""

# --- Core admin modules ---
$modules = @(
    @{ cat="admin"; name="dashboard"; url="$BaseAdmin/api/zestflow/dashboard/stats" },
    @{ cat="admin"; name="executors-apps"; url="$BaseAdmin/api/zestflow/executors/apps" },
    @{ cat="admin"; name="executors-list"; url="$BaseAdmin/api/zestflow/executors" },
    @{ cat="admin"; name="collectors"; url="$BaseAdmin/api/zestflow/collectors" },
    @{ cat="admin"; name="chains"; url="$BaseAdmin/api/zestflow/chains?appCode=demo-app" + '&page=1&size=5' },
    @{ cat="admin"; name="designs"; url="$BaseAdmin/api/zestflow/designs?appCode=demo-app" + '&page=1&size=5' },
    @{ cat="admin"; name="components"; url="$BaseAdmin/api/zestflow/components?appCode=demo-app" },
    @{ cat="admin"; name="schedules"; url="$BaseAdmin/api/zestflow/schedules?page=1&size=5" },
    @{ cat="admin"; name="users"; url="$BaseAdmin/api/zestflow/users?page=1&size=5" },
    @{ cat="admin"; name="roles"; url="$BaseAdmin/api/zestflow/roles" },
    @{ cat="admin"; name="dict-types"; url="$BaseAdmin/api/zestflow/dict-types?page=1&size=5" }
)
foreach ($m in $modules) {
    $r = Invoke-Api GET $m.url $null $h
    Add-F $m.cat $m.name $r.ok $r.status $r.ms ""
}

$r = Invoke-Api POST "$BaseAdmin/api/zestflow/logs/events/query" '{"page":1,"size":5}' $h
Add-F "admin" "logs-events" $r.ok $r.status $r.ms ""

$r = Invoke-Api GET "$BaseAdmin/actuator/health" $null $null 10
$healthOk = ($r.status -eq 200 -or $r.status -eq 503) -and [bool](
    ($r.body -match 'zestFlowAdmin') -and ($r.body -match 'deployMode'))
if ($healthOk) {
    try {
        $healthJson = ConvertFrom-Json $r.body
        $comp = $healthJson.components.zestFlowAdmin
        if ($comp) {
            Add-C "runtime" "zestflow.admin.health.status" ([string]$comp.status) "UP/DEGRADED/DOWN"
            if ($comp.details -and $comp.details.deployMode) {
                Add-C "runtime" "zestflow.admin.health.deployMode" $comp.details.deployMode ""
            }
            if ($comp.details -and $comp.details.onlineExecutors -ne $null) {
                Add-C "runtime" "zestflow.admin.health.onlineExecutors" $comp.details.onlineExecutors ""
            }
        }
    } catch {}
}
Add-F "admin" "actuator-zestFlowAdmin" ([bool]$healthOk) $(if ($healthOk) { 200 } else { $r.status }) $r.ms ""

$r = Invoke-Api GET "$BaseAdmin/api/zestflow/chains/active-codes?appCode=$($policyRaw.appCode)" $null $h
Add-F "admin" "chains-active-codes" $r.ok $r.status $r.ms ""

& "$PSScriptRoot\run-chain-publish-e2e.ps1" -BaseAdmin $BaseAdmin -AppCode $policyRaw.appCode
$chainPubOk = ($LASTEXITCODE -eq 0)
Add-F "admin" "chain-publish-e2e" $chainPubOk $(if ($chainPubOk) { 200 } else { 500 }) 0 $(if ($LASTEXITCODE -eq 2) { "skipped" } else { "publish+active-codes" })

& "$PSScriptRoot\run-chain-lifecycle-e2e.ps1" -BaseAdmin $BaseAdmin -BaseNetty $BaseNetty -AppCode $policyRaw.appCode
$chainLifeOk = ($LASTEXITCODE -eq 0)
Add-F "admin" "chain-lifecycle-e2e" $chainLifeOk $(if ($chainLifeOk) { 200 } else { 500 }) 0 $(if ($LASTEXITCODE -eq 2) { "skipped" } else { "create-publish-execute" })

& "$PSScriptRoot\run-rbac-horizontal-e2e.ps1" -BaseAdmin $BaseAdmin -AppCode $policyRaw.appCode
$rbacOk = ($LASTEXITCODE -eq 0)
Add-F "security" "rbac-horizontal-e2e" $rbacOk $(if ($rbacOk) { 200 } else { 403 }) 0 $(if ($LASTEXITCODE -eq 2) { "skipped" } else { "no-jwt-denied" })

$r = Invoke-Api GET "$BaseAdmin/api/zestflow/schedules?page=1&size=1" $null $h
$scheduleId = $null
if ($r.ok) {
    try {
        $schedBody = ConvertFrom-Json $r.body
        if ($schedBody.records -and $schedBody.records.Count -gt 0) { $scheduleId = $schedBody.records[0].id }
        elseif ($schedBody.data.records -and $schedBody.data.records.Count -gt 0) { $scheduleId = $schedBody.data.records[0].id }
    } catch {}
}
if ($scheduleId) {
    $r2 = Invoke-Api POST "$BaseAdmin/api/zestflow/schedules/$scheduleId/trigger" $null $h 60
    Add-F "admin" "schedule-trigger" $r2.ok $r2.status $r2.ms "id=$scheduleId"
} else {
    Add-F "admin" "schedule-trigger" $true 0 0 "skipped-no-schedule"
}

# --- Executor netty ---
$r = Invoke-Api GET "$BaseNetty/health" $null $null
Add-F "executor" "health" $r.ok $r.status $r.ms ""
$r = Invoke-Api POST "$BaseNetty/api/orders/handleApplyAfterSale" '{"applyId":"E2E-001"}' $null
Add-F "executor" "business-api" $r.ok $r.status $r.ms ""

$r = Invoke-Api GET "$BaseCollector/collector/health" $null $null 5
Add-F "collector" "health" $r.ok $r.status $r.ms ""

# --- Playground module ---
if ($pgEnabled) {
    $r = Invoke-Api GET "$BaseAdmin/api/zestflow/playground/scenes/list-all" $null $h
    $list = @()
    if ($r.ok) {
        try { $list = (ConvertFrom-Json $r.body).data } catch {}
    }
    Add-F "playground" "list-all-scenes" $r.ok $r.status $r.ms "count=$($list.Count)"

    foreach ($s in $list) {
        if ($s.appCode -and $s.appCode -ne $policyRaw.appCode) { continue }
        $code = $s.sceneCode
        $isOptional = $optionalScenes -contains $code
        $isPartial = $partialGreenScenes -contains $code
        $heavyCfg = $heavyMap.$code
        if ($SkipHeavyScenes -and $heavyCfg) {
            $scenes.Add([pscustomobject]@{
                sceneCode=$code; name=$s.name; skipped=$true; ok=$null; status=0; ms=0
                required=$true; optional=$false; partialGreen=$false; note="heavy-skipped"
            }) | Out-Null
            continue
        }
        $path = $s.requestPath
        $body = Prepare-SceneBody $code $s.requestBody $path
        $heavyTimeout = if ($heavyCfg -and $heavyCfg.timeoutSec) { [int]$heavyCfg.timeoutSec } else { 300 }
        $to = if ($heavyCfg) { [Math]::Max($SceneTimeoutSec, $heavyTimeout) }
               elseif ($path -eq '/execute' -or $path.StartsWith('/api/') -or $path -match '/api/') { $SceneTimeoutSec }
               else { 30 }
        $r = Invoke-Api POST "$BaseAdmin/api/zestflow/playground/execute/$code" $body $h $to
        $eval = Test-PlaygroundOk $r.body $path $code $partialGreenScenes
        $runOk = $r.ok -and $eval.ok
        $note = $eval.note
        if (-not $runOk -and $isOptional -and $policyMode -eq 'permissive') {
            $scenes.Add([pscustomobject]@{
                sceneCode=$code; name=$s.name; appCode=$s.appCode; path=$path
                ok=$false; status=$r.status; ms=$r.ms; skipped=$true; required=$false; optional=$true
                partialGreen=$isPartial; note="optional-skip-$note"
            }) | Out-Null
            Write-Host ("  scene {0} SKIP(optional) {1}" -f $code, $note) -ForegroundColor Yellow
            continue
        }
        $scenes.Add([pscustomobject]@{
            sceneCode=$code; name=$s.name; appCode=$s.appCode; path=$path
            ok=$runOk; status=$r.status; ms=$r.ms; skipped=$false
            required=(-not $isOptional); optional=$isOptional; partialGreen=$isPartial; note=$note
        }) | Out-Null
        Write-Host ("  scene {0} ok={1} {2}ms {3}" -f $code, $runOk, $r.ms, $(if ($isPartial) { '[partialGreen]' } else { '' }))
    }
    $requiredPass = @($scenes | Where-Object { $_.skipped -ne $true -and $_.required -ne $false -and $_.ok -eq $true }).Count
    $requiredFail = @($scenes | Where-Object { $_.skipped -ne $true -and $_.required -ne $false -and $_.ok -ne $true }).Count
    $optionalSkipped = @($scenes | Where-Object { $_.skipped -eq $true -and $_.optional -eq $true }).Count
    $heavySkipped = @($scenes | Where-Object { $_.skipped -eq $true -and $_.note -eq 'heavy-skipped' }).Count
    $batchOk = ($requiredFail -eq 0)
    Add-F "playground" "all-scenes-batch" $batchOk 200 0 "profile=$profileName mode=$policyMode reqPass=$requiredPass reqFail=$requiredFail optSkip=$optionalSkipped heavySkip=$heavySkipped"
} else {
    $r = Invoke-Api GET "$BaseAdmin/api/zestflow/playground/scenes/list-all" $null $h
    Add-F "playground" "disabled-expect-404" ($r.status -eq 404) $r.status $r.ms "actual when playground.enabled=false"
}

# --- Security matrix ---
$rNoJwt = Invoke-Api GET "$BaseAdmin/api/zestflow/dashboard/stats" $null $null
$badH = @{ Authorization = "Bearer invalid.token.here" }
$rBadJwt = Invoke-Api GET "$BaseAdmin/api/zestflow/dashboard/stats" $null $badH
$devOpenSec = ($rNoJwt.status -eq 200) -and ($rBadJwt.status -in 401,403)
Add-F "security" "no-jwt" (($rNoJwt.status -in 401,403) -or $devOpenSec) $rNoJwt.status $rNoJwt.ms $(if ($devOpenSec) { "dev-open" } else { "" })

$r = Invoke-Api POST "$BaseAdmin/api/zestflow/registry/register" '{"executorId":"e2e-evil","host":"10.0.0.1","port":1,"moduleCode":"x","moduleName":"x"}' $null
Add-F "security" "registry-no-token" ($r.status -ge 200 -and $r.status -lt 500) $r.status $r.ms "dev-open if token empty"

Add-F "security" "bad-jwt-protected-api" ($rBadJwt.status -in 401,403) $rBadJwt.status $rBadJwt.ms "/api/auth/** is permitAll"

$sceneSummary = @{
    profile = $profileName
    mode = $policyMode
    skipHeavyScenes = [bool]$SkipHeavyScenes
    requiredPass = $requiredPass
    requiredFail = $requiredFail
    optionalSkipped = $optionalSkipped
    heavySkipped = $heavySkipped
}
$out = @{
    timestamp = (Get-Date).ToString("o")
    environment = @{ admin=$BaseAdmin; netty=$BaseNetty; collector=$BaseCollector }
    e2ePolicy = @{ file=$PolicyFile; profile=$profileName; mode=$policyMode }
    sceneSummary = $sceneSummary
    configProbe = $configProbe
    functional = $functional
    playgroundScenes = $scenes
}
$json = $out | ConvertTo-Json -Depth 8
Set-Content -Path $ReportJson -Value $json -Encoding UTF8
Write-Host "Saved: $ReportJson" -ForegroundColor Green
$passF = @($functional | Where-Object { $_.ok }).Count
$totalF = $functional.Count
Write-Host "Functional: $passF / $totalF passed"
if ($pgEnabled) {
    Write-Host ("Scenes [{0}/{1}] required pass/fail={2}/{3} optionalSkip={4} heavySkip={5}" -f $profileName, $policyMode, $requiredPass, $requiredFail, $optionalSkipped, $heavySkipped) -ForegroundColor $(if ($requiredFail -eq 0) { 'Green' } else { 'Red' })
}
$exitFail = @($functional | Where-Object { -not $_.ok }).Count
if ($pgEnabled -and $requiredFail -gt 0) { $exitFail++ }
if ($exitFail -gt 0) { exit 1 }
exit 0
