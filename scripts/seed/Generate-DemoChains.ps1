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

function ConvertTo-JsonForSql($obj) {
    $json = ($obj | ConvertTo-Json -Compress -Depth 12)
    # PowerShell ConvertTo-Json 会把单引号转成 \u0027，Aviator 无法解析
    return ($json -replace '\\u0027', "'")
}

function Expand-SerialChain($chain, $stressList) {
    $stepPrefix = -join ([char]0x6B65, [char]0x9AA4)
    $stressList = @($stressList)[0..74]
    $nodes = @(); $edges = @(); $i = 1
    foreach ($comp in $stressList) {
        $nid = "n$i"
        $label = "$stepPrefix$i"
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

function Resolve-ChainGraph($chain) {
    if ($chain.serialFrom) {
        $list = $data.($chain.serialFrom)
        return Expand-SerialChain $chain $list
    }
    $nodes = @(); $edges = @()
    foreach ($n in $chain.nodes) { $nodes += (Normalize-Node $n) }
    foreach ($e in $chain.edges) {
        $edge = [ordered]@{ source = $e.source; target = $e.target }
        if ($e.label) { $edge.label = $e.label }
        $edges += $edge
    }
    return @{ nodes = $nodes; edges = $edges }
}

function Get-VisualMeta($node) {
    switch ($node.type) {
        'SCRIPT' { return @{ shape = 'flow-script'; nodeType = 'script'; w = 160; h = 46 } }
        'SUB_CHAIN' { return @{ shape = 'flow-subchain'; nodeType = 'subchain'; w = 160; h = 46 } }
        'ITERATOR' { return @{ shape = 'flow-iterator'; nodeType = 'iterator'; w = 160; h = 46 } }
        'CONDITION' {
            $comp = $node.component
            if ($comp -eq 'handleAfterSale' -or $comp -eq 'routePromotion') {
                return @{ shape = 'flow-multicondition'; nodeType = 'multicondition'; w = 120; h = 80 }
            }
            return @{ shape = 'flow-condition'; nodeType = 'condition'; w = 100; h = 80 }
        }
        default {
            switch ($node.component) {
                'loadUserInfo' { return @{ shape = 'flow-loader'; nodeType = 'loader'; w = 160; h = 46 } }
                'parseOrderResult' { return @{ shape = 'flow-parser'; nodeType = 'parser'; w = 160; h = 46 } }
                default { return @{ shape = 'flow-task'; nodeType = 'task'; w = 160; h = 46 } }
            }
        }
    }
}

function New-GraphCellId() {
    return [guid]::NewGuid().ToString()
}

function New-StartEndCell($id, $shape, $label, $nodeType, $x, $y) {
    return [ordered]@{
        id = $id
        shape = $shape
        position = [ordered]@{ x = $x; y = $y }
        size = [ordered]@{ width = 148; height = 40 }
        visible = $true
        attrs = [ordered]@{ label = [ordered]@{ text = $label } }
        data = [ordered]@{ label = $label; nodeType = $nodeType }
    }
}

function New-BizCell($node, $x, $y) {
    $meta = Get-VisualMeta $node
    $comp = if ($node.component) { $node.component } elseif ($node.subChainCode) { $node.subChainCode } else { $node.type }
    $cell = [ordered]@{
        id = $node.id
        shape = $meta.shape
        position = [ordered]@{ x = $x; y = $y }
        size = [ordered]@{ width = $meta.w; height = $meta.h }
        visible = $true
        attrs = [ordered]@{ label = [ordered]@{ text = $node.label } }
        data = [ordered]@{
            label = $node.label
            nodeType = $meta.nodeType
            componentId = $comp
            componentName = $node.label
        }
    }
    if ($node.script) { $cell.data.script = $node.script }
    if ($node.subChainCode) { $cell.data.subChainCode = $node.subChainCode }
    return $cell
}

function Get-EdgePorts($sx, $sy, $tx, $ty) {
    $dx = $tx - $sx
    $dy = $ty - $sy
    if ([math]::Abs($dy) -ge [math]::Abs($dx)) {
        if ($dy -ge 0) { return @{ sourcePort = 'b'; targetPort = 't' } }
        return @{ sourcePort = 't'; targetPort = 'b' }
    }
    if ($dx -ge 0) { return @{ sourcePort = 'r'; targetPort = 'l' } }
    return @{ sourcePort = 'l'; targetPort = 'r' }
}

function New-EdgeCell($sourceId, $targetId, $label, $positions, $zIndex) {
    $sp = $positions[$sourceId]
    $tp = $positions[$targetId]
    $ports = Get-EdgePorts $sp.x $sp.y $tp.x $tp.y
    $dx = [math]::Abs($tp.x - $sp.x)
    $dy = [math]::Abs($tp.y - $sp.y)
    # 同列/同行短距：直线，避免 manhattan 绕圈
    if ($dx -le 30 -or $dy -le 30) {
        $router = [ordered]@{ name = 'normal' }
        $connector = [ordered]@{ name = 'normal' }
    } else {
        $router = [ordered]@{
            name = 'manhattan'
            args = [ordered]@{
                padding = [ordered]@{ top = 24; bottom = 24; left = 24; right = 24 }
                step = 10
            }
        }
        $connector = [ordered]@{ name = 'rounded' }
    }
    $edge = [ordered]@{
        shape = 'edge'
        id = (New-GraphCellId)
        zIndex = $zIndex
        router = $router
        connector = $connector
        source = [ordered]@{ cell = $sourceId; port = $ports.sourcePort }
        target = [ordered]@{ cell = $targetId; port = $ports.targetPort }
        attrs = [ordered]@{
            line = [ordered]@{
                stroke = '#94a3b8'
                strokeWidth = 2
                targetMarker = [ordered]@{ name = 'classic'; size = 8 }
            }
        }
    }
    if ($label) {
        $edge.labels = @([ordered]@{
            attrs = [ordered]@{ label = [ordered]@{ text = $label; fill = '#475569'; fontSize = 12 } }
        })
    }
    return $edge
}

function Test-IsPureSerial($nodes, $edges) {
    if ($nodes.Count -le 1) { return $true }
    if ($edges.Count -ne ($nodes.Count - 1)) { return $false }
    $out = @{}; $in = @{}
    foreach ($n in $nodes) { $out[$n.id] = 0; $in[$n.id] = 0 }
    foreach ($e in $edges) {
        if ($out.ContainsKey($e.source)) { $out[$e.source]++ }
        if ($in.ContainsKey($e.target)) { $in[$e.target]++ }
    }
    foreach ($v in $out.Values) { if ($v -gt 1) { return $false } }
    foreach ($v in $in.Values) { if ($v -gt 1) { return $false } }
    return $true
}

function Test-HasBranching($nodes, $edges) {
    $out = @{}; $in = @{}
    foreach ($n in $nodes) { $out[$n.id] = 0; $in[$n.id] = 0 }
    foreach ($e in $edges) {
        if ($out.ContainsKey($e.source)) { $out[$e.source]++ }
        if ($in.ContainsKey($e.target)) { $in[$e.target]++ }
    }
    foreach ($v in $out.Values) { if ($v -gt 1) { return $true } }
    foreach ($v in $in.Values) { if ($v -gt 1) { return $true } }
    return $false
}

function Get-LayoutSize($node) {
    $meta = Get-VisualMeta $node
    return @{ w = $meta.w; h = $meta.h }
}

function Layout-VerticalFlow($nodes) {
    $positions = @{}
    $centerX = 260
    $y = 110
    $gap = 72
    foreach ($n in $nodes) {
        $sz = Get-LayoutSize $n
        $x = $centerX - [math]::Round($sz.w / 2.0)
        $positions[$n.id] = @{ x = $x; y = $y }
        $y += $sz.h + $gap
    }
    return @{
        positions = $positions
        startX = ($centerX - 74); startY = 40
        endX = ($centerX - 74); endY = ($y + 32)
    }
}

function Layout-Pipeline($nodes) {
    $positions = @{}
    $gap = 56; $y = 220
    $x = 40 + 148 + $gap
    foreach ($n in $nodes) {
        $sz = Get-LayoutSize $n
        $positions[$n.id] = @{ x = $x; y = ($y - [math]::Round(($sz.h - 46) / 2)) }
        $x += $sz.w + $gap
    }
    $last = $nodes[-1]
    $lastSz = Get-LayoutSize $last
    $lastX = $positions[$last.id].x
    return @{
        positions = $positions
        startX = 40; startY = ($y - 20)
        endX = ($lastX + $lastSz.w + $gap); endY = ($y - 20)
    }
}

function Layout-StressSnake($nodes) {
    $positions = @{}
    $cols = 5; $stepX = 220; $stepY = 100
    $baseX = 40; $baseY = 120
    for ($i = 0; $i -lt $nodes.Count; $i++) {
        $row = [math]::Floor($i / $cols)
        $col = $i % $cols
        if ($row % 2 -eq 1) { $col = $cols - 1 - $col }
        $positions[$nodes[$i].id] = @{
            x = $baseX + $col * $stepX
            y = $baseY + $row * $stepY
        }
    }
    $maxRow = [math]::Floor(($nodes.Count - 1) / $cols)
    $centerX = $baseX + (($cols - 1) * $stepX / 2)
    return @{
        positions = $positions
        startX = [math]::Round($centerX); startY = 40
        endX = [math]::Round($centerX); endY = ($baseY + ($maxRow + 1) * $stepY + 20)
    }
}

function Layout-Dag($nodes, $edges) {
    $layer = @{}
    foreach ($n in $nodes) { $layer[$n.id] = 0 }
    $changed = $true
    while ($changed) {
        $changed = $false
        foreach ($e in $edges) {
            $next = $layer[$e.source] + 1
            if ($next -gt $layer[$e.target]) {
                $layer[$e.target] = $next
                $changed = $true
            }
        }
    }
    $groups = @{}
    foreach ($n in $nodes) {
        $lv = $layer[$n.id]
        if (-not $groups.ContainsKey($lv)) { $groups[$lv] = [System.Collections.Generic.List[object]]::new() }
        [void]$groups[$lv].Add($n)
    }
    $positions = @{}
    $hGap = 280; $vGap = 130; $baseY = 120
    $maxLayer = ($groups.Keys | Measure-Object -Maximum).Maximum
    foreach ($lv in ($groups.Keys | Sort-Object)) {
        $group = @($groups[$lv])
        $count = $group.Count
        $rowWidth = ($count - 1) * $hGap
        $startX = -($rowWidth / 2.0)
        for ($i = 0; $i -lt $count; $i++) {
            $n = $group[$i]
            $positions[$n.id] = @{
                x = [math]::Round($startX + $i * $hGap)
                y = [math]::Round($baseY + $lv * $vGap)
            }
        }
    }
    $avgX = 0
    if ($positions.Count -gt 0) {
        $avgX = [math]::Round(($positions.Values | ForEach-Object { $_.x } | Measure-Object -Average).Average)
    }
    foreach ($id in @($positions.Keys)) {
        $positions[$id] = @{ x = ($positions[$id].x - $avgX + 240); y = $positions[$id].y }
    }
    $centerX = 240
    return @{
        positions = $positions
        startX = $centerX; startY = 40
        endX = $centerX; endY = ($baseY + ($maxLayer + 1) * $vGap + 20)
    }
}

function Resolve-Layout($chain, $nodes, $edges) {
    if ($chain.serialFrom) { return Layout-StressSnake $nodes }
    if (Test-HasBranching $nodes $edges) { return Layout-Dag $nodes $edges }
    return Layout-VerticalFlow $nodes
}

function Build-GraphData($chain) {
    $lblStart = -join ([char]0x5F00, [char]0x59CB)
    $lblEnd = -join ([char]0x7ED3, [char]0x675F)
    $resolved = Resolve-ChainGraph $chain
    $nodes = @($resolved.nodes)
    $edges = @($resolved.edges)

    $layout = Resolve-Layout $chain $nodes $edges
    $positions = $layout.positions

    $startId = '_start'
    $endId = '_end'
    $positions[$startId] = @{ x = $layout.startX; y = $layout.startY }
    $positions[$endId] = @{ x = $layout.endX; y = $layout.endY }

    $cells = @()
    $cells += New-StartEndCell $startId 'flow-start' $lblStart 'start' $layout.startX $layout.startY
    foreach ($n in $nodes) {
        $pos = $positions[$n.id]
        $cells += New-BizCell $n $pos.x $pos.y
    }
    $cells += New-StartEndCell $endId 'flow-end' $lblEnd 'end' $layout.endX $layout.endY

    $inDeg = @{}; $outDeg = @{}
    foreach ($n in $nodes) { $inDeg[$n.id] = 0; $outDeg[$n.id] = 0 }
    foreach ($e in $edges) {
        if ($inDeg.ContainsKey($e.target)) { $inDeg[$e.target]++ }
        if ($outDeg.ContainsKey($e.source)) { $outDeg[$e.source]++ }
    }

    $z = 1
    foreach ($e in $edges) {
        $cells += New-EdgeCell $e.source $e.target $e.label $positions $z
        $z++
    }
    foreach ($n in $nodes) {
        if ($inDeg[$n.id] -eq 0) {
            $cells += New-EdgeCell $startId $n.id $null $positions $z
            $z++
        }
    }
    foreach ($n in $nodes) {
        if ($outDeg[$n.id] -eq 0) {
            $cells += New-EdgeCell $n.id $endId $null $positions $z
            $z++
        }
    }

    return (ConvertTo-JsonForSql @{ cells = $cells })
}

function Build-ChainData($chain) {
    $resolved = Resolve-ChainGraph $chain
    $root = [ordered]@{
        code = $chain.code
        version = 1
        nodes = @($resolved.nodes)
        edges = @($resolved.edges)
    }
    if ($chain.errorStrategy) {
        $root.config = @{ errorStrategy = $chain.errorStrategy }
    }
    return (ConvertTo-JsonForSql $root)
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
    $tenantId = if ($s.PSObject.Properties.Name -contains 'tenantId') { [int]$s.tenantId } else { 1 }
    $rows += "('$($s.scene)', '$(Escape-Sql $s.name)', '$(Escape-Sql $s.name)', '$path', 'POST', 'JSON', '$(Escape-Sql $s.body)', '{""code"":200}', '$($s.chain)', 30, $tenantId, 'demo-app', 'system', 'system', NOW(), NOW())"
}
[void]$admin.AppendLine(($rows -join ",`n") + ';')

$utf8Bom = New-Object System.Text.UTF8Encoding $true
[System.IO.File]::WriteAllText($OutExecutor, $sb.ToString(), $utf8Bom)
[System.IO.File]::WriteAllText($OutAdmin, $admin.ToString(), $utf8Bom)
Write-Host "OK chains=$($chains.Count) scenes=$($data.scenes.Count) stressNodes=$(@($data.stressComponents).Count)"
