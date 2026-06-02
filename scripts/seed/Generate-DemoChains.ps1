# Generate demo-app chain/scene SQL from demo-chains.json (graph + real components)
$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent | Split-Path -Parent
$JsonPath = Join-Path $PSScriptRoot 'demo-chains.json'
$OutExecutor = Join-Path $Root 'zestflow-executor\src\main\resources\db\demo-chains-generated.sql'
$OutAdmin = Join-Path $Root 'zestflow-admin\src\main\resources\db\demo-scenes-generated.sql'

$data = Get-Content $JsonPath -Raw -Encoding UTF8 | ConvertFrom-Json

function Escape-Sql([string]$s) {
    if ($null -eq $s) { return '' }
    return ($s -replace "'", "''")
}

function Expand-SerialChain($chain, $stressList) {
    $stressList = @($stressList)[0..74]
    $nodes = @(); $edges = @(); $i = 1
    foreach ($comp in $stressList) {
        $nid = "n$i"
        $label = "步骤$i"
        $nodes += [ordered]@{
            id = $nid; label = $label; type = 'NORMAL'
            component = $comp; componentName = $label
        }
        if ($i -gt 1) { $edges += [ordered]@{ source = "n$($i - 1)"; target = $nid } }
        $i++
    }
    return @{ nodes = $nodes; edges = $edges }
}

function Normalize-Node($n) {
    $node = [ordered]@{
        id = $n.id
        label = $n.label
        type = $n.type
    }
    if ($n.component) { $node.component = $n.component; $node.componentName = $n.label }
    if ($n.script) { $node.script = $n.script }
    if ($n.subChainCode) { $node.subChainCode = $n.subChainCode }
    if ($n.config) { $node.config = $n.config }
    return $node
}

function Build-ChainData($chain) {
    $nodes = @(); $edges = @()
    if ($chain.serialFrom) {
        $list = $data.($chain.serialFrom)
        $g = Expand-SerialChain $chain $list
        $nodes = $g.nodes; $edges = $g.edges
    } else {
        foreach ($n in $chain.nodes) { $nodes += (Normalize-Node $n) }
        foreach ($e in $chain.edges) {
            $edge = [ordered]@{ source = $e.source; target = $e.target }
            if ($e.label) { $edge.label = $e.label }
            $edges += $edge
        }
    }
    $root = [ordered]@{
        code = $chain.code
        version = 1
        nodes = $nodes
        edges = $edges
    }
    if ($chain.errorStrategy) {
        $root.config = @{ errorStrategy = $chain.errorStrategy }
    }
    return ($root | ConvertTo-Json -Compress -Depth 12)
}

function Build-GraphData($chain) {
    $cells = @()
    if ($chain.serialFrom) {
        $list = $data.($chain.serialFrom)
        $x = 40; $y = 40; $col = 0
        for ($i = 0; $i -lt $list.Count; $i++) {
            $comp = $list[$i]
            if ($col -ge 5) { $col = 0; $y += 80 }
            $cells += [ordered]@{
                id = "task-$comp-$i"; shape = 'flow-task'
                position = [ordered]@{ x = (40 + $col * 180); y = $y }
                size = [ordered]@{ width = 140; height = 40 }
                attrs = [ordered]@{ label = [ordered]@{ text = "S$($i + 1)" } }
                data = [ordered]@{ label = "S$($i + 1)"; nodeType = 'task'; componentId = $comp; componentName = $comp }
            }
            $col++
        }
    } else {
        $x = 80
        foreach ($n in $chain.nodes) {
            $shape = switch ($n.type) {
                'CONDITION' { 'flow-condition' }
                'SCRIPT' { 'flow-task' }
                'SUB_CHAIN' { 'flow-task' }
                'ITERATOR' { 'flow-task' }
                default { 'flow-task' }
            }
            $comp = if ($n.component) { $n.component } else { $n.type }
            $cells += [ordered]@{
                id = "cell-$($n.id)"; shape = $shape
                position = [ordered]@{ x = $x; y = 200 }
                size = [ordered]@{ width = 160; height = 46 }
                attrs = [ordered]@{ label = [ordered]@{ text = $n.label } }
                data = [ordered]@{ label = $n.label; nodeType = 'task'; componentId = $comp; componentName = $n.label }
            }
            $x += 200
        }
    }
    return (@{ cells = $cells } | ConvertTo-Json -Compress -Depth 10)
}

$chains = @($data.chains)
$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine('-- 2026-06-02: demo-app chains with DAG (scripts/seed/Generate-DemoChains.ps1)')
[void]$sb.AppendLine('USE `zestflow_app_bussiness`;')
[void]$sb.AppendLine('')
[void]$sb.AppendLine('INSERT IGNORE INTO `zf_chain` (`code`, `name`, `description`, `status`, `version`, `created_by`, `app_code`, `tenant_id`, `created_at`, `updated_at`) VALUES')

$vals = @()
foreach ($c in $chains) {
    $bizCount = if ($c.serialFrom) { 75 } else { $c.nodes.Count }
    $desc = "$(Escape-Sql $c.desc) [tier=$($c.tier) nodes=$bizCount]"
    $vals += "('$($c.code)', '$(Escape-Sql $c.name)', '$desc', 4, 1, 'system', 'demo-app', 1, NOW(), NOW())"
}
[void]$sb.AppendLine(($vals -join ",`n") + ';')
[void]$sb.AppendLine('')

foreach ($c in $chains) {
    $des = $c.code -replace '^CHN_', 'DES_'
    $cj = Escape-Sql (Build-ChainData $c)
    $gj = Escape-Sql (Build-GraphData $c)
    [void]$sb.AppendLine("INSERT IGNORE INTO ``zf_design`` (``code``, ``name``, ``description``, ``designer``, ``status``, ``graph_data``, ``chain_data``, ``created_by``, ``app_code``, ``tenant_id``, ``created_at``, ``updated_at``) VALUES")
    [void]$sb.AppendLine("('$des', '$(Escape-Sql $c.name) Design', '$(Escape-Sql $c.desc)', 'system', 1, '$gj', '$cj', 'system', 'demo-app', 1, NOW(), NOW());")
    [void]$sb.AppendLine("INSERT IGNORE INTO ``zf_design_binding`` (``design_code``, ``chain_code``, ``tenant_id``, ``app_code``) VALUES ('$des', '$($c.code)', 1, 'demo-app');")
    [void]$sb.AppendLine("INSERT IGNORE INTO ``zf_chain_version`` (``chain_code``, ``version``, ``design_code``, ``graph_data``, ``chain_data``, ``created_by``, ``tenant_id``, ``app_code``, ``created_at``) VALUES ('$($c.code)', 1, '$des', '$gj', '$cj', 'system', 1, 'demo-app', NOW());")
    [void]$sb.AppendLine('')
}

$admin = [System.Text.StringBuilder]::new()
[void]$admin.AppendLine('-- 2026-06-02: demo-app playground scenes')
[void]$admin.AppendLine('USE `zestflow_admin`;')
[void]$admin.AppendLine('')
[void]$admin.AppendLine('INSERT IGNORE INTO `playground_scene` (`scene_code`, `name`, `description`, `request_path`, `request_method`, `body_type`, `request_body`, `response_example`, `chain_code`, `rate_limit`, `tenant_id`, `app_code`, `created_by`, `updated_by`, `created_at`, `updated_at`) VALUES')
$rows = @()
foreach ($s in $data.scenes) {
    $path = if ($s.PSObject.Properties.Name -contains 'path') { $s.path } else { '/execute' }
    $rows += "('$($s.scene)', '$(Escape-Sql $s.name)', '$(Escape-Sql $s.name)', '$path', 'POST', 'JSON', '$(Escape-Sql $s.body)', '{""code"":200}', '$($s.chain)', 30, 1, 'demo-app', 'system', 'system', NOW(), NOW())"
}
[void]$admin.AppendLine(($rows -join ",`n") + ';')

$utf8Bom = New-Object System.Text.UTF8Encoding $true
[System.IO.File]::WriteAllText($OutExecutor, $sb.ToString(), $utf8Bom)
[System.IO.File]::WriteAllText($OutAdmin, $admin.ToString(), $utf8Bom)
Write-Host "OK chains=$($chains.Count) scenes=$($data.scenes.Count) stressNodes=$(@($data.stressComponents).Count)"
