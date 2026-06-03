<template>
  <div class="logs-page">
    <div class="page-header">
      <div class="stats-summary">
        <span style="font-weight:600;color:#409eff">{{ $t('logs.total', { total: list.length }) }}</span>
        <el-tag type="success" size="small" style="margin-left:8px">{{ $t('logs.success') }} {{ stats.success }}</el-tag>
        <el-tag type="danger" size="small">{{ $t('logs.failure') }} {{ stats.failure }}</el-tag>
        <el-select v-model="currentAppCode" filterable style="width:200px;margin-left:16px" :placeholder="$t('design.selectApp')" @change="handleAppChange">
          <el-option v-for="a in apps" :key="a.appCode" :label="a.appName || a.appCode" :value="a.appCode" />
        </el-select>
        <el-input v-model="query.executionId" :placeholder="$t('logs.executionId')" clearable style="width:160px;margin-left:16px" @keyup.enter="search" />
        <el-input v-model="query.keyword" :placeholder="$t('logs.keyword')" clearable style="width:160px;margin-left:8px" @keyup.enter="search" />
        <el-select v-model="query.status" :placeholder="$t('common.status')" clearable style="width:100px;margin-left:8px">
          <el-option :label="$t('common.all')" :value="undefined" />
          <el-option :label="$t('logs.success')" :value="1" />
          <el-option :label="$t('logs.failure')" :value="0" />
        </el-select>
        <el-select v-model="query.eventTypes" :placeholder="$t('logs.allTypes')" clearable multiple collapse-tags style="width:160px;margin-left:8px">
          <el-option label="CHAIN_STARTED" value="CHAIN_STARTED" />
          <el-option label="CHAIN_COMPLETED" value="CHAIN_COMPLETED" />
          <el-option label="CHAIN_FAILED" value="CHAIN_FAILED" />
          <el-option label="CHAIN_TIMEOUT" value="CHAIN_TIMEOUT" />
          <el-option label="NODE_STARTED" value="NODE_STARTED" />
          <el-option label="NODE_COMPLETED" value="NODE_COMPLETED" />
          <el-option label="NODE_FAILED" value="NODE_FAILED" />
        </el-select>
        <el-button type="primary" style="margin-left:8px" @click="search">{{ $t('logs.search') }}</el-button>
        <el-button @click="resetSearch">{{ $t('logs.reset') }}</el-button>
      </div>
    </div>

    <el-table
      :data="list"
      v-loading="loading"
      stripe border
      style="width:100%"
      :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
    >
      <el-table-column prop="executionId" :label="$t('logs.executionId')" width="200" show-overflow-tooltip />
      <el-table-column :label="$t('logs.chainCode')" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span
            v-if="resolveChainCode(row)"
            class="chain-code-link"
            @click.stop="openChainDetail(resolveChainCode(row)!, row.appCode)"
          >{{ resolveChainCode(row) }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('logs.chainName')" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ displayChainName(row) }}
        </template>
      </el-table-column>
      <el-table-column prop="appName" :label="$t('logs.appName')" width="120" show-overflow-tooltip />
      <el-table-column :label="$t('logs.nodeCount')" width="80" align="center">
        <template #default="{ row }">
          <span>{{ row.nodeCount != null ? row.nodeCount : '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('logs.successCount')" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="success">{{ row.successCount || 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('logs.failedCount')" width="80" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="danger" v-if="(row.failedCount || 0) > 0">{{ row.failedCount }}</el-tag>
          <span v-else>0</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('logs.costMs')" width="100" align="center">
        <template #default="{ row }">
          <span>{{ row.costMs != null ? row.costMs + 'ms' : '-' }}</span>
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.status')" width="90" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.status === 1" type="success" size="small">{{ $t('logs.success') }}</el-tag>
          <el-tag v-else-if="row.status === 0" type="danger" size="small">{{ $t('logs.failure') }}</el-tag>
          <el-tag v-else type="info" size="small">{{ $t('logs.inProgress') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('logs.timestamp')" width="170">
        <template #default="{ row }">
          {{ formatTime(row.startTime) }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="110" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" type="primary" class="action-btn" @click.stop="showDetail(row)">
            {{ $t('logs.detail') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div style="display:flex;justify-content:flex-end;margin-top:12px">
      <el-pagination
        v-if="total > 0"
        v-model:current-page="query.page"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="fetchList"
        @size-change="fetchList"
      />
      <el-empty v-if="total === 0 && loaded" :description="$t('logs.noData')" />
    </div>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      :title="$t('logs.traceDetail')"
      :size="720"
      :close-on-click-modal="false"
    >
      <template v-if="traceDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item :label="$t('logs.executionId')" :span="2">{{ traceDetail.executionId }}</el-descriptions-item>
          <el-descriptions-item :label="$t('logs.chainCode')">
            <span
              v-if="resolveChainCode(traceDetail)"
              class="chain-code-link"
              @click="openChainDetail(resolveChainCode(traceDetail)!, traceDetail.appCode)"
            >{{ resolveChainCode(traceDetail) }}</span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('logs.chainName')">{{ displayChainName(traceDetail) }}</el-descriptions-item>
          <el-descriptions-item :label="$t('logs.executorId')">{{ traceDetail.executorId }}</el-descriptions-item>
          <el-descriptions-item :label="$t('logs.appCode')">{{ traceDetail.appCode || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('logs.appName')">{{ traceDetail.appName || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('logs.costMs')">{{ traceDetail.costMs ? traceDetail.costMs + 'ms' : '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('common.status')">
            <el-tag v-if="traceDetail.status === 1" type="success" size="small">{{ $t('logs.success') }}</el-tag>
            <el-tag v-else-if="traceDetail.status === 0" type="danger" size="small">{{ $t('logs.failure') }}</el-tag>
            <el-tag v-else type="info" size="small">{{ $t('logs.inProgress') }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('logs.nodeCount')">{{ traceDetail.nodeCount != null ? traceDetail.nodeCount : '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('logs.successCount')">
            <el-tag size="small" type="success">{{ traceDetail.successCount || 0 }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('logs.failedCount')">
            <el-tag size="small" type="danger" v-if="(traceDetail.failedCount || 0) > 0">{{ traceDetail.failedCount }}</el-tag>
            <span v-else>0</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('logs.errorMessage')" :span="2" v-if="traceDetail.errorMessage">
            <span style="color:var(--el-color-danger)">{{ traceDetail.errorMessage }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <h4 style="margin:20px 0 12px;display:flex;align-items:center;gap:8px">
          {{ $t('logs.traceFlow') }}
          <el-button v-if="!graphError && !graphLoading && execGraph" text size="small" type="primary" @click="exportPNG" style="margin-left:8px;font-size:13px">
            <el-icon style="font-size:14px;margin-right:2px"><Download /></el-icon>PNG
          </el-button>
          <el-button v-if="!graphError && !graphLoading && execGraph" text size="small" type="primary" @click="openFullscreen" style="margin-left:auto;font-size:13px">
            <el-icon style="font-size:14px;margin-right:2px"><FullScreen /></el-icon>{{ $t('logs.expand') }}
          </el-button>
        </h4>

        <!-- 加载中 -->
        <div v-if="graphLoading" style="text-align:center;padding:40px">
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
        </div>

        <!-- 无图/错误 -->
        <div v-else-if="graphError" style="text-align:center;padding:30px;color:#909399;font-size:13px">
          {{ graphError }}
        </div>

        <!-- 执行状态图例 -->
        <div v-else class="graph-legend">
          <span class="legend-item"><span class="legend-dot dot-success" />{{ $t('logs.success') }}</span>
          <span class="legend-item"><span class="legend-dot dot-failed" />{{ $t('logs.failure') }}</span>
          <span class="legend-item"><span class="legend-dot dot-pending" />{{ $t('logs.notExecuted') }}</span>
        </div>

        <!-- X6 画布 -->
        <div ref="graphContainer" class="execution-graph" style="width:100%;height:360px;border:1px solid #e8e8e8;border-radius:6px;background:#fafafa" />
      </template>
      <div v-else-if="detailLoading" style="text-align:center;padding:40px">
        <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      </div>
    </el-drawer>

    <!-- 流程图全屏弹窗 -->
    <el-dialog v-model="fullscreenVisible" fullscreen destroy-on-close @closed="destroyFullscreenGraph">
      <div class="graph-legend">
        <span class="legend-item"><span class="legend-dot dot-success" />{{ $t('logs.success') }}</span>
        <span class="legend-item"><span class="legend-dot dot-failed" />{{ $t('logs.failure') }}</span>
        <span class="legend-item"><span class="legend-dot dot-pending" />{{ $t('logs.notExecuted') }}</span>
      </div>
      <div ref="fullscreenContainer" style="width:100%;height:calc(100vh - 160px);border-radius:6px;background:#fafafa" />
    </el-dialog>

    <!-- 链详情抽屉 -->
    <el-drawer v-model="chainDrawerVisible" :title="$t('chains.chainDetails')" :size="480" destroy-on-close>
      <div v-if="chainDetailLoading" style="text-align:center;padding:40px">
        <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      </div>
      <template v-else-if="currentChainDetail">
        <div style="padding:0 8px">
          <div style="font-size:20px;font-weight:600;color:#303133;margin-bottom:12px">{{ currentChainDetail.name }}</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item :label="$t('logs.chainCode')">
              <el-tag size="small" style="font-family:monospace">{{ currentChainDetail.code }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('common.status')">
              <el-tag :type="currentChainDetail.status === 1 ? 'success' : 'danger'" size="small">
                {{ currentChainDetail.status === 1 ? $t('chains.enabled') : $t('chains.disabled') }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item :label="$t('chains.app')">
              {{ currentChainDetail.appCode || '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('chains.description')">
              {{ currentChainDetail.description || '-' }}
            </el-descriptions-item>
            <el-descriptions-item :label="$t('common.createdBy')">{{ currentChainDetail.createdBy || '-' }}</el-descriptions-item>
            <el-descriptions-item :label="$t('chains.createdAt')">{{ currentChainDetail.createdAt?.replace('T', ' ') }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </template>
      <el-empty v-else :description="$t('common.requestFailed')" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Loading, FullScreen, Download } from '@element-plus/icons-vue'
import { Graph } from '@antv/x6'
import { Export } from '@antv/x6-plugin-export'
import type { EventQueryParams, ExecutionTrace } from '@/api/logs'
import { queryExecutionTraces, getExecutionTrace, getSnapshot } from '@/api/logs'
import { executorApi, type AppOption } from '@/api/executor'
import { chainApi, type ChainVO } from '@/api/chain'

const { t } = useI18n()
const route = useRoute()

const query = reactive<EventQueryParams>({
  executionId: undefined,
  appCode: undefined,
  keyword: undefined,
  status: undefined,
  eventTypes: undefined,
  startTime: undefined,
  endTime: undefined,
  page: 1,
  pageSize: 20,
})

const list = ref<ExecutionTrace[]>([])
const total = ref(0)
const loaded = ref(false)
const loading = ref(false)

const apps = ref<AppOption[]>([])
const currentAppCode = ref<string>('')

const stats = computed(() => {
  const success = list.value.filter(r => r.status === 1).length
  const failure = list.value.filter(r => r.status === 0).length
  const inProgress = list.value.filter(r => r.status !== 0 && r.status !== 1).length
  return { success, failure, inProgress }
})

// 详情抽屉
const detailVisible = ref(false)
const detailLoading = ref(false)
const traceDetail = ref<ExecutionTrace | null>(null)

// 执行状态图
const graphContainer = ref<HTMLElement | null>(null)
const graphLoading = ref(false)
const graphError = ref('')
const graphLegend = ref('')
const execGraph = ref<Graph | null>(null)

// 全屏展开
const savedGraphData = ref<string>('')
const savedGraphEvents = ref<any[]>([])
const fullscreenVisible = ref(false)
const fullscreenContainer = ref<HTMLElement | null>(null)
const fullscreenGraph = ref<Graph | null>(null)

// 链详情抽屉
const chainDrawerVisible = ref(false)
const chainDetailLoading = ref(false)
const currentChainDetail = ref<ChainVO | null>(null)
const chainNameCache = ref<Record<string, string>>({})

function resolveChainCode(row: ExecutionTrace | null | undefined): string | undefined {
  if (!row) return undefined
  return row.chainCode || row.events?.[0]?.chainId || undefined
}

function displayChainName(row: ExecutionTrace): string {
  const code = resolveChainCode(row)
  if (code && chainNameCache.value[code]) {
    return chainNameCache.value[code]
  }
  if (row.chainName && (!code || row.chainName !== code)) {
    return row.chainName
  }
  return row.chainName || code || '-'
}

async function enrichChainNames(rows: ExecutionTrace[]) {
  const seen = new Set<string>()
  const tasks: Promise<void>[] = []
  for (const row of rows) {
    const code = resolveChainCode(row)
    const appCode = row.appCode || currentAppCode.value
    if (!code || !appCode || seen.has(code) || chainNameCache.value[code]) continue
    if (row.chainName && row.chainName !== code) continue
    seen.add(code)
    tasks.push(
      chainApi.getByCode(code, appCode)
        .then(c => { chainNameCache.value[code] = c.name })
        .catch(() => {})
    )
  }
  if (tasks.length > 0) {
    await Promise.allSettled(tasks)
  }
}

async function openChainDetail(chainCode: string, appCode?: string) {
  const resolvedAppCode = appCode || currentAppCode.value
  if (!resolvedAppCode) return
  chainDrawerVisible.value = true
  chainDetailLoading.value = true
  currentChainDetail.value = null
  try {
    currentChainDetail.value = await chainApi.getByCode(chainCode, resolvedAppCode)
    if (currentChainDetail.value?.name) {
      chainNameCache.value[chainCode] = currentChainDetail.value.name
    }
  } catch {
    currentChainDetail.value = null
  } finally {
    chainDetailLoading.value = false
  }
}

function formatTime(ts: number | string | undefined): string {
  if (ts == null) return '-'
  const d = typeof ts === 'number' ? new Date(ts) : new Date(ts)
  if (isNaN(d.getTime())) return String(ts)
  return d.toLocaleString()
}

async function fetchApps() {
  try {
    apps.value = await executorApi.listApps()
    if (apps.value.length > 0 && !currentAppCode.value) {
      currentAppCode.value = apps.value[0].appCode
      query.appCode = currentAppCode.value
    }
  } catch { /* ignore */ }
}

function handleAppChange() {
  query.appCode = currentAppCode.value || undefined
  query.page = 1
  fetchList()
}

async function fetchList() {
  loading.value = true
  try {
    const res: any = await queryExecutionTraces(query)
    list.value = res.list || []
    total.value = res.total || 0
    await enrichChainNames(list.value)
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loaded.value = true
    loading.value = false
  }
}

function search() {
  query.page = 1
  fetchList()
}

function resetSearch() {
  query.executionId = undefined
  query.appCode = undefined
  query.keyword = undefined
  query.status = undefined
  query.eventTypes = undefined
  query.startTime = undefined
  query.endTime = undefined
  currentAppCode.value = ''
  query.page = 1
  fetchList()
}

async function showDetail(row: ExecutionTrace) {
  detailVisible.value = true
  detailLoading.value = true
  traceDetail.value = null
  graphError.value = ''
  graphLoading.value = true
  graphLegend.value = ''
  try {
    let detail = row
    if (!row.events || row.events.length === 0) {
      const res: any = await getExecutionTrace(row.executionId)
      detail = res
    }
    traceDetail.value = detail
    await enrichChainNames([detail])

    // 加载设计图数据，在 X6 画布中还原执行流程
    const appCode = detail.appCode || (query.appCode as string)
    const chainCode = resolveChainCode(detail)
    if (chainCode && appCode) {
      try {
        const snapshotRes: any = await getSnapshot(chainCode, detail.startTime || Date.now())
        const graphData = snapshotRes?.graphData
        if (graphData) {
          await nextTick()
          renderExecGraph(graphData, detail.events || [])
        } else {
          graphError.value = t('logs.noGraphData')
        }
      } catch {
        graphError.value = t('logs.graphLoadError')
      }
    } else {
      graphError.value = t('logs.noChainInfo')
    }
  } catch {
    traceDetail.value = row
  } finally {
    detailLoading.value = false
    graphLoading.value = false
  }
}

onMounted(async () => {
  await fetchApps()
  const executionId = typeof route.query.executionId === 'string' ? route.query.executionId.trim() : ''
  const appCode = typeof route.query.appCode === 'string' ? route.query.appCode.trim() : ''
  if (appCode) {
    currentAppCode.value = appCode
    query.appCode = appCode
  }
  if (executionId) {
    query.executionId = executionId
  }
  await fetchList()
  if (executionId) {
    const row = list.value.find(r => r.executionId === executionId)
    await showDetail(row ?? { executionId, appCode: currentAppCode.value || appCode } as ExecutionTrace)
  }
})

onUnmounted(() => {
  destroyExecGraph()
})

// ====== X6 执行状态图 ======

function registerExecShapes() {
  function reg(name: string, def: any) {
    try { Graph.registerNode(name, def) } catch { /* ignore duplicate HMR */ }
  }

  // 矩形类节点（日志视图使用统一中性底色，执行状态靠着色区分）
  const rectTypes = [
    { name: 'flow-start', rx: 20, label: '开始' },
    { name: 'flow-end', rx: 20, label: '结束' },
    { name: 'flow-task', rx: 8, label: '执行元件' },
    { name: 'flow-loader', rx: 8, label: '加载器' },
    { name: 'flow-parser', rx: 8, label: '解析器' },
    { name: 'flow-script', rx: 8, label: '脚本' },
    { name: 'flow-subchain', rx: 23, label: '子链' },
    { name: 'flow-iterator', rx: 8, label: '迭代器' },
  ]
  for (const s of rectTypes) {
    reg(s.name, {
      inherit: 'rect',
      width: 160, height: 46,
      markup: [{ tagName: 'rect', selector: 'body' }, { tagName: 'text', selector: 'label' }],
      attrs: {
        body: { rx: s.rx, ry: s.rx, fill: '#d4d4d4', stroke: '#a0a4a8', strokeWidth: 1 },
        label: { text: s.label, fill: '#303133', fontSize: 13, fontWeight: 600, refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle' },
      },
    })
  }

  // 多边形类节点
  reg('flow-condition', {
    inherit: 'polygon',
    width: 100, height: 80,
    markup: [{ tagName: 'polygon', selector: 'body' }, { tagName: 'text', selector: 'label' }],
    attrs: {
      body: { refPoints: '50,0 100,40 50,80 0,40', fill: '#d4d4d4', stroke: '#a0a4a8', strokeWidth: 1 },
      label: { text: '判断', fill: '#303133', fontSize: 13, fontWeight: 600, refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle' },
    },
  })

  reg('flow-multicondition', {
    inherit: 'polygon',
    width: 120, height: 80,
    markup: [{ tagName: 'polygon', selector: 'body' }, { tagName: 'text', selector: 'label' }],
    attrs: {
      body: { refPoints: '85,0 120,40 85,80 35,80 0,40 35,0', fill: '#d4d4d4', stroke: '#a0a4a8', strokeWidth: 1 },
      label: { text: '选择器', fill: '#303133', fontSize: 13, fontWeight: 600, refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle' },
    },
  })
}

function renderExecGraph(graphDataStr: string, events: any[]) {
  if (!graphContainer.value) {
    graphError.value = t('logs.graphDataError')
    graphLoading.value = false
    return
  }

  destroyExecGraph()

  let graphData: any
  try {
    graphData = typeof graphDataStr === 'string' ? JSON.parse(graphDataStr) : graphDataStr
  } catch {
    graphError.value = t('logs.graphDataError')
    graphLoading.value = false
    return
  }

  if (!graphData || !graphData.cells || graphData.cells.length === 0) {
    graphError.value = t('logs.noGraphData')
    graphLoading.value = false
    return
  }

  // 存一份供全屏展开使用
  savedGraphData.value = graphDataStr
  savedGraphEvents.value = events

  registerExecShapes()

  const container = graphContainer.value
  const g = new Graph({
    container,
    width: container.clientWidth || 600,
    height: container.clientHeight || 360,
    grid: { visible: true, size: 20, type: 'dot' },
    panning: { enabled: true, eventTypes: ['leftMouseDown'] },
    mousewheel: { enabled: true, zoomAtMousePosition: true },
    interacting: false,
    connecting: {
      router: { name: 'manhattan', args: { padding: { top: 15, bottom: 15, left: 15, right: 15 }, step: 10 } },
      connector: { name: 'rounded' },
    },
    defaultEdge: {
      router: { name: 'manhattan', args: { padding: { top: 15, bottom: 15, left: 15, right: 15 }, step: 10 } },
      connector: { name: 'rounded' },
      attrs: {
        line: { stroke: '#94a3b8', strokeWidth: 2, targetMarker: { name: 'classic', size: 8 } },
      },
      defaultLabel: {
        attrs: { text: { fill: '#303133', fontSize: 12, stroke: '#fff', strokeWidth: 2, paintOrder: 'stroke' } },
      },
    },
  } as any)

  g.use(new Export())
  g.fromJSON(graphData)
  applyExecutionColors(g, events)
  g.zoomToFit({ padding: 20, maxScale: 1.5 })
  execGraph.value = g
}

/** 导出 PNG（4x 高清） */
function exportPNG() {
  if (!execGraph.value || !graphContainer.value) return
  const g = execGraph.value
  const rect = g.getContentBBox()
  if (!rect || rect.width === 0) return
  const svgEl = graphContainer.value.querySelector<SVGSVGElement>('.x6-graph-svg')
  if (!svgEl) return
  const clone = svgEl.cloneNode(true) as SVGSVGElement
  const pad = 40
  const scale = 4
  const w = Math.ceil(rect.width + pad * 2)
  const h = Math.ceil(rect.height + pad * 2)
  const vw = w * scale
  const vh = h * scale
  clone.setAttribute('width', String(vw))
  clone.setAttribute('height', String(vh))
  clone.setAttribute('viewBox', `${rect.x - pad} ${rect.y - pad} ${w} ${h}`)
  const bg = document.createElementNS('http://www.w3.org/2000/svg', 'rect')
  bg.setAttribute('width', '100%'); bg.setAttribute('height', '100%'); bg.setAttribute('fill', '#fafafa')
  clone.insertBefore(bg, clone.firstChild)
  const svgStr = new XMLSerializer().serializeToString(clone)
  const svgUri = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svgStr)
  const img = new Image()
  img.onload = () => {
    const canvas = document.createElement('canvas')
    canvas.width = vw; canvas.height = vh
    const ctx = canvas.getContext('2d')!
    ctx.fillStyle = '#fafafa'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(img, 0, 0)
    const link = document.createElement('a')
    link.download = `execution-${Date.now()}.png`
    link.href = canvas.toDataURL('image/png')
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }
  img.onerror = () => {
    const link = document.createElement('a')
    link.download = `execution-${Date.now()}.svg`
    link.href = svgUri
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }
  img.src = svgUri
}

function destroyExecGraph() {
  if (execGraph.value) {
    execGraph.value.dispose()
    execGraph.value = null
  }
}

/** 着色：根据事件状态给节点和连线着色 */
function applyExecutionColors(g: Graph, events: any[]) {
  // 构建节点执行状态映射：componentId → status
  const nodeStatusMap = new Map<string, string>()
  for (const e of events) {
    const id = e.nodeId
    if (!id) continue
    if (e.eventType === 'NODE_FAILED' || e.eventType === 'NODE_FALLBACK_FAILED') {
      nodeStatusMap.set(id, 'failed')
    } else if (e.eventType === 'NODE_COMPLETED' || e.eventType === 'NODE_FALLBACK_SUCCESS') {
      if (nodeStatusMap.get(id) !== 'failed') {
        nodeStatusMap.set(id, 'success')
      }
    } else if (e.eventType === 'NODE_STARTED') {
      if (!nodeStatusMap.has(id)) {
        nodeStatusMap.set(id, 'running')
      }
    }
  }

  const hasChainCompleted = events.some(e => e.eventType === 'CHAIN_COMPLETED')

  g.getNodes().forEach(node => {
    const data = node.getData() || {}
    const componentId = data.componentId
    const shape = node.shape || ''

    // 开始/结束节点：结构节点无 componentId，按链执行状态着色
    let status = componentId ? nodeStatusMap.get(componentId) : undefined
    if (!status) {
      if (shape === 'flow-start' && events.length > 0) {
        status = 'success'
      } else if (shape === 'flow-end' && hasChainCompleted) {
        status = 'success'
      }
    }

    if (status === 'success') {
      node.attr('body/fill', '#4caf50')
      node.attr('body/stroke', '#388e3c')
      node.attr('body/strokeWidth', 2)
      node.attr('label/fill', '#fff')
    } else if (status === 'failed') {
      node.attr('body/fill', '#f44336')
      node.attr('body/stroke', '#d32f2f')
      node.attr('body/strokeWidth', 2)
      node.attr('label/fill', '#fff')
    } else if (status === 'running') {
      node.attr('body/fill', '#ff9800')
      node.attr('body/stroke', '#f57c00')
      node.attr('body/strokeWidth', 2)
      node.attr('body/strokeDasharray', '4,2')
      node.attr('label/fill', '#fff')
    } else {
      // 未执行：中性灰
      node.attr('body/fill', '#d4d4d4')
      node.attr('body/stroke', '#a0a4a8')
      node.attr('body/strokeWidth', 1)
    }
  })

  // 连线着色：目标节点已执行的连线高亮
  g.getEdges().forEach(edge => {
    const target = edge.getTargetNode()
    if (target) {
      const data = target.getData() || {}
      const id = data.componentId
      const status = id ? nodeStatusMap.get(id) : undefined
      if (status) {
        edge.attr('line/stroke', '#3b82f6')
        edge.attr('line/strokeWidth', 2.5)
      } else {
        edge.attr('line/stroke', '#d9d9d9')
        edge.attr('line/strokeWidth', 1.5)
        edge.attr('line/strokeDasharray', '4,3')
      }
    }
  })
}

// ====== 全屏展开 ======

function openFullscreen() {
  if (!savedGraphData.value) return
  fullscreenVisible.value = true
  nextTick(() => renderFullscreenGraph())
}

function renderFullscreenGraph() {
  destroyFullscreenGraph()
  if (!fullscreenContainer.value) return

  let data: any
  try {
    data = JSON.parse(savedGraphData.value)
  } catch { return }
  if (!data || !data.cells) return

  registerExecShapes()

  const g = new Graph({
    container: fullscreenContainer.value,
    width: fullscreenContainer.value.clientWidth || 800,
    height: fullscreenContainer.value.clientHeight || 600,
    grid: { visible: true, size: 20, type: 'dot' },
    panning: { enabled: true, eventTypes: ['leftMouseDown'] },
    mousewheel: { enabled: true, zoomAtMousePosition: true },
    interacting: false,
    connecting: {
      router: { name: 'manhattan', args: { padding: { top: 15, bottom: 15, left: 15, right: 15 }, step: 10 } },
      connector: { name: 'rounded' },
    },
    defaultEdge: {
      router: { name: 'manhattan', args: { padding: { top: 15, bottom: 15, left: 15, right: 15 }, step: 10 } },
      connector: { name: 'rounded' },
      attrs: {
        line: { stroke: '#94a3b8', strokeWidth: 2, targetMarker: { name: 'classic', size: 8 } },
      },
      defaultLabel: {
        attrs: { text: { fill: '#303133', fontSize: 12, stroke: '#fff', strokeWidth: 2, paintOrder: 'stroke' } },
      },
    },
  } as any)

  g.fromJSON(data)
  applyExecutionColors(g, savedGraphEvents.value)
  g.zoomToFit({ padding: 30, maxScale: 2 })
  fullscreenGraph.value = g
}

function destroyFullscreenGraph() {
  if (fullscreenGraph.value) {
    fullscreenGraph.value.dispose()
    fullscreenGraph.value = null
  }
}
</script>

<style scoped>
.page-header {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 16px;
}
.stats-summary {
  display: flex; align-items: center; font-size: 14px;
}
.action-btn.action-btn { padding: 2px 4px; margin-left: 0; }
.chain-code-link {
  color: #409eff;
  cursor: pointer;
  font-family: monospace;
  font-weight: 600;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.chain-code-link:hover { text-decoration: underline; }

/* X6 执行状态图 */
.execution-graph {
  width: 100%; height: 360px;
  border: 1px solid #e8e8e8; border-radius: 6px; background: #fafafa;
  overflow: hidden;
}
.graph-legend {
  display: flex; gap: 16px; margin: 8px 0 12px; font-size: 13px;
}
.legend-item {
  display: flex; align-items: center; gap: 4px; color: #606266;
}
.legend-dot {
  display: inline-block; width: 10px; height: 10px; border-radius: 50%;
}
.dot-success { background: #4caf50; }
.dot-failed  { background: #f44336; }
.dot-pending { background: #d4d4d4; border: 1px solid #a0a4a8; }
</style>
