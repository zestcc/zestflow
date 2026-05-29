<template>
  <div class="design-editor-x6">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar">
      <div class="toolbar-left">
        <el-button text @click="goBack">
          <el-icon><ArrowLeft /></el-icon> {{ $t('design.back') }}
        </el-button>
        <span class="toolbar-title">{{ design?.name }}</span>
        <el-tag v-if="design" :type="design.status === 1 ? 'success' : 'danger'" size="small">
          {{ design.status === 1 ? $t('design.enabled') : $t('design.disabled') }}
        </el-tag>
      </div>
      <div class="toolbar-center">
        <el-tooltip content="撤销">
          <el-button text :disabled="!canUndo" @click="handleUndo"><el-icon><Back /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip content="重做">
          <el-button text :disabled="!canRedo" @click="handleRedo"><el-icon><Right /></el-icon></el-button>
        </el-tooltip>
        <span class="toolbar-divider" />
        <el-tooltip content="复制">
          <el-button text :disabled="selectedCount !== 1" @click="handleCopy"><el-icon><CopyDocument /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip content="粘贴">
          <el-button text :disabled="!canPaste" @click="handlePaste"><el-icon><DocumentAdd /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip content="剪切">
          <el-button text :disabled="selectedCount < 1" @click="handleCut"><el-icon><Scissor /></el-icon></el-button>
        </el-tooltip>
        <span class="toolbar-divider" />
        <el-tooltip content="缩小">
          <el-button text @click="zoomOut"><el-icon><ZoomOut /></el-icon></el-button>
        </el-tooltip>
        <span class="zoom-label">{{ Math.round(zoomLevel * 100) }}%</span>
        <el-tooltip content="放大">
          <el-button text @click="zoomIn"><el-icon><ZoomIn /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip content="适应画布">
          <el-button text @click="zoomToFit"><el-icon><FullScreen /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip content="实际大小">
          <el-button text @click="zoomReset"><el-icon><ScaleToOriginal /></el-icon></el-button>
        </el-tooltip>
      </div>
      <div class="toolbar-right">
        <el-tag v-if="selectedCount > 0" type="info" size="small" style="margin-right:8px">
          {{ selectedCount }} selected
        </el-tag>
        <el-button type="primary" :loading="saving" @click="handleSave">
          <el-icon><Check /></el-icon> {{ $t('design.saveGraph') }}
        </el-button>
      </div>
    </div>

    <!-- 编辑器主体 -->
    <div class="editor-body" @contextmenu.prevent="onCanvasContextMenu">
      <!-- 左侧节点面板 -->
      <div class="node-palette">
        <div class="palette-header">{{ $t('design.nodes') }}</div>
        <div class="palette-list">
          <div
            v-for="nt in nodeTypes"
            :key="nt.type"
            class="palette-item"
            :style="{ borderLeftColor: nt.color }"
            draggable="true"
            @dragstart="onDragStart($event, nt)"
          >
            <div class="palette-icon" :style="{ background: nt.gradient }" v-html="nt.icon" />
            <div class="palette-label">{{ nt.label }}</div>
          </div>
        </div>
      </div>

      <!-- 中间画布 -->
      <div class="canvas-area" ref="canvasContainerRef" @dragover.prevent="onDragOver" @drop.prevent="onDrop">
        <div ref="graphContainerRef" class="graph-container" />
        <div ref="minimapContainerRef" class="minimap-container" />
      </div>

      <!-- 右侧属性面板 -->
      <div class="property-panel">
        <div class="panel-header">{{ $t('design.properties') }}</div>
        <div v-if="selectedNodeData" class="panel-body">
          <el-form size="small" label-position="top">
            <el-form-item :label="$t('design.nodeName')">
              <el-input v-model="selectedNodeData.label" @input="onDataChange" />
            </el-form-item>
            <el-form-item :label="$t('design.nodeType')">
              <el-tag :color="nodeColor(selectedNodeData.nodeType)" style="color:#fff;border:none">
                {{ typeLabel(selectedNodeData.nodeType) }}
              </el-tag>
            </el-form-item>
            <el-form-item v-if="hasDescription(selectedNodeData.nodeType)" :label="$t('design.nodeDesc')">
              <el-input v-model="selectedNodeData.description" type="textarea" :rows="4" @input="onDataChange" />
            </el-form-item>
          </el-form>
        </div>
        <div v-else class="panel-empty">
          <el-icon style="font-size:32px;color:#dcdfe6;margin-bottom:8px"><Pointer /></el-icon>
          <span>{{ $t('design.design') }}</span>
        </div>
      </div>
    </div>

    <!-- 右键菜单 -->
    <teleport to="body">
      <div v-if="contextMenu.visible" class="x6-context-menu" :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }" @click.stop @contextmenu.prevent>
        <div v-if="contextMenu.isNode" class="context-item danger" @click="contextDeleteNode">
          <el-icon><Delete /></el-icon> 删除节点
        </div>
        <div v-if="contextMenu.isNode" class="context-item" @click="contextCopyNode">
          <el-icon><CopyDocument /></el-icon> 复制节点
        </div>
        <div v-if="contextMenu.isNode" class="context-item" @click="contextCutNode">
          <el-icon><Scissor /></el-icon> 剪切节点
        </div>
        <div v-if="contextMenu.isNode" class="context-separator" />
        <div class="context-item" @click="contextSelectAll">
          <el-icon><Select /></el-icon> 全选
        </div>
        <div v-if="!contextMenu.isNode" class="context-item" @click="contextPaste">
          <el-icon><DocumentAdd /></el-icon> 粘贴
        </div>
      </div>
    </teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Graph } from '@antv/x6'
import { History } from '@antv/x6-plugin-history'
import { Snapline } from '@antv/x6-plugin-snapline'
import { MiniMap } from '@antv/x6-plugin-minimap'
import { Selection } from '@antv/x6-plugin-selection'
import { Keyboard } from '@antv/x6-plugin-keyboard'
import { Clipboard } from '@antv/x6-plugin-clipboard'
import { Transform } from '@antv/x6-plugin-transform'
import { register } from '@antv/x6-vue-shape'
import FlowNodeX6 from './FlowNodeX6.vue'
import { designApi } from '@/api/design'
import {
  ArrowLeft, Check, Pointer, Back, Right,
  CopyDocument, DocumentAdd, Scissor,
  ZoomIn, ZoomOut, FullScreen, ScaleToOriginal,
  Delete, Select,
} from '@element-plus/icons-vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const designId = Number(route.params.id)

const design = ref<any>(null)
const saving = ref(false)
const selectedCount = ref(0)
const canUndo = ref(false)
const canRedo = ref(false)
const selectedNodeData = ref<any>(null)
const zoomLevel = ref(1)
const canPaste = ref(false)

const graphContainerRef = ref<HTMLDivElement | null>(null)
const canvasContainerRef = ref<HTMLDivElement | null>(null)
const minimapContainerRef = ref<HTMLDivElement | null>(null)

let graph: Graph | null = null
let resizeObserver: ResizeObserver | null = null
let selectedCell: any = null

// 右键菜单
const contextMenu = reactive({ visible: false, x: 0, y: 0, isNode: false, node: null as any })
let contextMenuCloseHandler: (() => void) | null = null

// 节点类型定义
const nodeTypes = [
  { type: 'start', label: t('design.startNode'), color: '#67c23a', gradient: 'linear-gradient(135deg, #67c23a, #85ce61)', icon: '<svg viewBox="0 0 16 16" width="14" height="14"><circle cx="8" cy="8" r="6" fill="#fff"/></svg>' },
  { type: 'task', label: t('design.taskNode'), color: '#409eff', gradient: 'linear-gradient(135deg, #409eff, #6ab0ff)', icon: '<svg viewBox="0 0 16 16" width="14" height="14"><rect x="2" y="1" width="12" height="14" rx="2" fill="#fff"/></svg>' },
  { type: 'condition', label: t('design.conditionNode'), color: '#e6a23c', gradient: 'linear-gradient(135deg, #e6a23c, #f0b75e)', icon: '<svg viewBox="0 0 16 16" width="14" height="14"><polygon points="8,1 15,8 8,15 1,8" fill="#fff"/></svg>' },
  { type: 'end', label: t('design.endNode'), color: '#909399', gradient: 'linear-gradient(135deg, #909399, #a8abb0)', icon: '<svg viewBox="0 0 16 16" width="14" height="14"><circle cx="8" cy="8" r="5" fill="none" stroke="#fff" stroke-width="2"/><circle cx="8" cy="8" r="2" fill="#fff"/></svg>' },
]

const nodeColorMap: Record<string, string> = {
  start: '#67c23a',
  task: '#409eff',
  condition: '#e6a23c',
  end: '#909399',
}

function nodeColor(type: string) { return nodeColorMap[type] || '#409eff' }

function typeLabel(type: string) {
  return nodeTypes.find(nt => nt.type === type)?.label || type
}

function hasDescription(type: string) {
  return type === 'task' || type === 'condition'
}

// 获取指定节点类型的端口配置
function getPorts(nodeType: string) {
  const items: any[] = []
  if (nodeType !== 'start') {
    items.push({ id: 'in', group: 'in' })
  }
  if (nodeType === 'condition') {
    items.push({ id: 'out-yes', group: 'out-yes' })
    items.push({ id: 'out-no', group: 'out-no' })
  } else if (nodeType !== 'end') {
    items.push({ id: 'out', group: 'out' })
  }
  return items
}

// 注册 X6 节点类型
function registerNodes() {
  register({
    shape: 'flow-node',
    width: 160,
    height: 50,
    component: FlowNodeX6,
    ports: {
      groups: {
        in: {
          position: { name: 'top' },
          attrs: { circle: { r: 5, magnet: true, stroke: '#666', fill: '#fff', strokeWidth: 2 } },
        },
        out: {
          position: { name: 'bottom' },
          attrs: { circle: { r: 5, magnet: true, stroke: '#666', fill: '#fff', strokeWidth: 2 } },
        },
        'out-yes': {
          position: { name: 'absolute', args: { x: '25%', y: '100%' } },
          attrs: {
            circle: { r: 5, magnet: true, stroke: '#67c23a', fill: '#67c23a', strokeWidth: 2 },
            text: { text: 'Y', fill: '#67c23a', fontSize: 11, fontWeight: 'bold' },
          },
        },
        'out-no': {
          position: { name: 'absolute', args: { x: '75%', y: '100%' } },
          attrs: {
            circle: { r: 5, magnet: true, stroke: '#f56c6c', fill: '#f56c6c', strokeWidth: 2 },
            text: { text: 'N', fill: '#f56c6c', fontSize: 11, fontWeight: 'bold' },
          },
        },
      },
      items: [],
    },
  })
}

// 关闭右键菜单
function closeContextMenu() {
  contextMenu.visible = false
  contextMenu.node = null
  if (contextMenuCloseHandler) {
    document.removeEventListener('click', contextMenuCloseHandler)
    contextMenuCloseHandler = null
  }
}

// 画布右键菜单
function onCanvasContextMenu(e: MouseEvent) {
  if (!graph) return
  const target = e.target as HTMLElement
  const cell = graph.getCellFromPoint(e.clientX, e.clientY)
  contextMenu.isNode = !!cell && cell.isNode()
  contextMenu.node = cell && cell.isNode() ? cell : null
  contextMenu.x = e.clientX
  contextMenu.y = e.clientY
  contextMenu.visible = true
  closeContextMenu()
  // 点击其他地方关闭
  contextMenuCloseHandler = () => { closeContextMenu() }
  setTimeout(() => document.addEventListener('click', contextMenuCloseHandler!), 0)
}

function contextDeleteNode() {
  if (contextMenu.node) {
    graph?.removeCells([contextMenu.node])
    if (selectedCell === contextMenu.node) {
      selectedCell = null
      selectedNodeData.value = null
    }
  }
  closeContextMenu()
}

function contextCopyNode() {
  if (contextMenu.node && graph) {
    graph.copy([contextMenu.node])
    canPaste.value = true
  }
  closeContextMenu()
}

function contextCutNode() {
  if (contextMenu.node && graph) {
    graph.cut([contextMenu.node])
    canPaste.value = true
    if (selectedCell === contextMenu.node) {
      selectedCell = null
      selectedNodeData.value = null
    }
  }
  closeContextMenu()
}

function contextSelectAll() {
  graph?.getAllCells().filter(c => c.isNode()).forEach(c => graph?.select(c.id))
  closeContextMenu()
}

function contextPaste() {
  handlePaste()
  closeContextMenu()
}

// 初始化 X6 Graph
function initGraph() {
  if (!graphContainerRef.value) return

  graph = new Graph({
    container: graphContainerRef.value,
    grid: { visible: true, size: 20, type: 'dot' },
    panning: { enabled: true, eventTypes: ['rightMouseDown'] },
    mousewheel: { enabled: true, zoomAtMousePosition: true },
    highlighting: {
      nodeAvailable: { name: 'stroke', args: { padding: 4, attrs: { stroke: '#409eff', strokeWidth: 2 } } },
      magnetAvailable: { name: 'stroke', args: { attrs: { stroke: '#409eff', fill: '#409eff' } } },
    },
    connecting: {
      router: { name: 'orth' },
      connector: { name: 'rounded' },
      snap: { radius: 20 },
      allowBlank: false,
      allowLoop: false,
      allowNode: true,
      highlight: true,
      validateConnection({ sourceCell, targetCell, sourcePort, targetPort }) {
        if (!sourcePort || !targetPort) return false
        if (sourceCell.id === targetCell.id) return false
        const srcData = sourceCell.getData()
        const tgtData = targetCell.getData()
        if (!srcData || !tgtData) return false
        if (tgtData.nodeType === 'start') return false
        if (srcData.nodeType === 'end') return false
        if (sourcePort === 'in') return false
        if (targetPort !== 'in') return false
        return true
      },
    },
    defaultEdge: {
      attrs: {
        line: {
          stroke: '#409eff',
          strokeWidth: 2,
          strokeDasharray: '0',
          targetMarker: { name: 'classic', size: 8 },
        },
      },
      label: {
        attrs: {
          text: { fill: '#606266', fontSize: 11 },
          rect: { fill: '#fff', rx: 3, stroke: '#e8e8e8', strokeWidth: 1 },
        },
      },
    },
  })

  // 插件
  graph.use(new Snapline({ enabled: true, sharp: true }))
  graph.use(new Selection({
    enabled: true,
    multiple: true,
    rubberEdge: true,
    rubberNode: true,
    rubberband: true,
    showNodeSelectionBox: true,
  }))
  graph.use(new MiniMap({
    container: minimapContainerRef.value!,
    width: 200,
    height: 140,
    minVisible: 0.1,
  }))
  graph.use(new History({ enabled: true }))
  // 键盘快捷键插件
  graph.use(new Keyboard({ enabled: true }))
  // 剪贴板插件
  graph.use(new Clipboard())
  // 节点变换（调整大小）
  graph.use(new Transform({ resizing: true, rotating: false }))

  // 键盘快捷键
  graph.bindKey('backspace', () => removeSelected())
  graph.bindKey('del', () => removeSelected())
  graph.bindKey('ctrl+z', () => graph?.undo())
  graph.bindKey('ctrl+y', () => graph?.redo())
  graph.bindKey('ctrl+c', () => { if (graph) { const cells = graph.getSelectedCells(); if (cells.length > 0) { graph.copy(cells); canPaste.value = true } } })
  graph.bindKey('ctrl+v', () => handlePaste())
  graph.bindKey('ctrl+x', () => { if (graph) { const cells = graph.getSelectedCells(); if (cells.length > 0) { graph.cut(cells); canPaste.value = true; selectedCell = null; selectedNodeData.value = null } } })
  graph.bindKey('ctrl+a', () => { graph?.getAllCells().filter(c => c.isNode()).forEach(c => graph?.select(c.id)) })

  // 缩放监听
  graph.on('scale', ({ sx }) => { zoomLevel.value = sx })

  // 事件监听
  graph.on('selection:changed', () => {
    if (!graph) return
    const cells = graph.getSelectedCells()
    selectedCount.value = cells.length
    if (cells.length === 1 && cells[0].isNode()) {
      selectedCell = cells[0]
      selectedNodeData.value = { ...cells[0].getData() }
    } else {
      selectedCell = null
      selectedNodeData.value = null
    }
  })

  graph.on('node:click', ({ node }) => {
    graph?.cleanSelection()
    graph?.select(node.id)
    selectedCell = node
    selectedNodeData.value = { ...node.getData() }
  })

  graph.on('blank:click', () => {
    graph?.cleanSelection()
    selectedCell = null
    selectedNodeData.value = null
  })

  graph.on('history:change', () => {
    canUndo.value = graph?.canUndo() ?? false
    canRedo.value = graph?.canRedo() ?? false
  })

  // 画布自适应
  const container = canvasContainerRef.value
  if (container) {
    resizeObserver = new ResizeObserver(() => {
      if (graph && container) {
        const rect = container.getBoundingClientRect()
        graph.resize(rect.width, rect.height)
      }
    })
    resizeObserver.observe(container)
  }
}

// HTML5 拖拽：左侧面板 → 画布
function onDragStart(event: DragEvent, nt: typeof nodeTypes[0]) {
  if (event.dataTransfer) {
    event.dataTransfer.setData('text/plain', JSON.stringify({ type: nt.type, label: nt.label }))
    event.dataTransfer.effectAllowed = 'copy'
  }
}

function onDragOver(event: DragEvent) {
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
}

function onDrop(event: DragEvent) {
  if (!graph) return
  const raw = event.dataTransfer?.getData('text/plain')
  if (!raw) return
  const { type, label } = JSON.parse(raw)

  const pos = graph.clientToLocal(event.clientX, event.clientY)
  graph.addNode({
    shape: 'flow-node',
    x: pos.x - 80,
    y: pos.y - 25,
    width: 160,
    height: 50,
    data: { label, nodeType: type, description: '' },
    ports: { items: getPorts(type) },
  })
}

// 属性变更
function onDataChange() {
  if (selectedCell && selectedNodeData.value) {
    selectedCell.setData({ ...selectedNodeData.value })
  }
}

// 删除选中
function removeSelected() {
  if (!graph) return
  const cells = graph.getSelectedCells()
  if (cells.length > 0) {
    graph.removeCells(cells)
    selectedCell = null
    selectedNodeData.value = null
  }
}

// 撤销/重做
function handleUndo() { graph?.undo() }
function handleRedo() { graph?.redo() }

// 复制/粘贴/剪切
function handleCopy() {
  if (!graph) return
  const cells = graph.getSelectedCells()
  if (cells.length > 0) {
    graph.copy(cells)
    canPaste.value = true
    ElMessage.success('已复制')
  }
}

function handlePaste() {
  if (!graph || !canPaste.value) return
  const cells = graph.paste()
  if (cells && cells.length > 0) {
    graph.cleanSelection()
    cells.forEach(c => graph?.select(c.id))
    ElMessage.success('已粘贴')
  }
}

function handleCut() {
  if (!graph) return
  const cells = graph.getSelectedCells()
  if (cells.length > 0) {
    graph.cut(cells)
    canPaste.value = true
    selectedCell = null
    selectedNodeData.value = null
    ElMessage.success('已剪切')
  }
}

// 缩放控制
function zoomIn() {
  if (!graph) return
  const s = graph.zoom()
  graph.zoom(s + 0.1, { minScale: 0.2, maxScale: 3 })
}

function zoomOut() {
  if (!graph) return
  const s = graph.zoom()
  graph.zoom(s - 0.1, { minScale: 0.2, maxScale: 3 })
}

function zoomToFit() {
  graph?.zoomToFit({ padding: 40, maxScale: 1 })
}

function zoomReset() {
  graph?.zoom(1)
}

// 加载设计数据
async function loadDesign() {
  try {
    design.value = await designApi.getById(designId)
    if (!graph) return
    if (design.value.graphData) {
      const data = JSON.parse(design.value.graphData)
      if (data && data.cells && data.cells.length > 0) {
        graph.fromJSON(data)
        graph.zoomToFit({ padding: 60, maxScale: 1 })
        return
      }
    }
    // 空设计：添加默认开始/结束节点
    graph.addNode({
      shape: 'flow-node',
      x: 250, y: 40,
      width: 160, height: 50,
      data: { label: t('design.startNode'), nodeType: 'start' },
      ports: { items: getPorts('start') },
    })
    graph.addNode({
      shape: 'flow-node',
      x: 250, y: 400,
      width: 160, height: 50,
      data: { label: t('design.endNode'), nodeType: 'end' },
      ports: { items: getPorts('end') },
    })
    graph.centerContent()
  } catch (e) {
    console.error(e)
    ElMessage.error('加载设计失败')
    router.push('/design')
  }
}

// 保存
async function handleSave() {
  if (!graph) return
  saving.value = true
  try {
    const json = graph.toJSON()
    await designApi.saveGraph(designId, JSON.stringify(json))
    ElMessage.success(t('design.saveGraphSuccess'))
  } catch {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push('/design')
}

onMounted(async () => {
  registerNodes()
  await nextTick()
  initGraph()
  await loadDesign()
})

onBeforeUnmount(() => {
  closeContextMenu()
  resizeObserver?.disconnect()
  graph?.dispose()
  graph = null
})
</script>

<style scoped>
.design-editor-x6 {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  background: #fff;
  border-radius: 4px;
  overflow: hidden;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 16px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
  flex-shrink: 0;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-title {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
}

.toolbar-center {
  display: flex;
  align-items: center;
  gap: 2px;
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  background: #dcdfe6;
  margin: 0 6px;
}

.zoom-label {
  font-size: 12px;
  color: #606266;
  min-width: 36px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.toolbar-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.editor-body {
  display: flex;
  flex: 1;
  overflow: hidden;
  position: relative;
}

.node-palette {
  width: 180px;
  border-right: 1px solid #ebeef5;
  background: #fafafa;
  flex-shrink: 0;
  overflow-y: auto;
}

.palette-header {
  padding: 12px 16px;
  font-weight: 600;
  font-size: 13px;
  color: #606266;
  border-bottom: 1px solid #ebeef5;
}

.palette-list {
  padding: 8px;
}

.palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: #fff;
  border-radius: 6px;
  margin-bottom: 6px;
  cursor: grab;
  box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  transition: box-shadow 0.2s, transform 0.15s;
  border-left: 3px solid transparent;
  user-select: none;
}

.palette-item:hover {
  box-shadow: 0 3px 12px rgba(0,0,0,0.1);
  transform: translateY(-1px);
}

.palette-item:active {
  cursor: grabbing;
  transform: translateY(0);
}

.palette-icon {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 12px;
  flex-shrink: 0;
}

.palette-icon :deep(svg) {
  display: block;
}

.palette-label {
  font-size: 12px;
  color: #303133;
  font-weight: 500;
}

.canvas-area {
  flex: 1;
  position: relative;
  overflow: hidden;
  background: #f9fafb;
}

.graph-container {
  width: 100%;
  height: 100%;
}

.minimap-container {
  position: absolute;
  bottom: 12px;
  right: 12px;
  border: 1px solid #e0e0e0;
  border-radius: 6px;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0,0,0,0.1);
  z-index: 10;
  overflow: hidden;
}

.property-panel {
  width: 260px;
  border-left: 1px solid #ebeef5;
  background: #fafafa;
  flex-shrink: 0;
  overflow-y: auto;
}

.panel-header {
  padding: 12px 16px;
  font-weight: 600;
  font-size: 13px;
  color: #606266;
  border-bottom: 1px solid #ebeef5;
}

.panel-body {
  padding: 16px;
}

.panel-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 16px;
  color: #909399;
  font-size: 13px;
}
</style>

<!-- 全局右键菜单样式 -->
<style>
.x6-context-menu {
  position: fixed;
  z-index: 9999;
  min-width: 150px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 6px 24px rgba(0,0,0,0.15);
  padding: 4px;
  border: 1px solid #ebeef5;
  user-select: none;
}

.x6-context-menu .context-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: #303133;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.x6-context-menu .context-item:hover {
  background: #f0f5ff;
}

.x6-context-menu .context-item.danger {
  color: #f56c6c;
}

.x6-context-menu .context-item.danger:hover {
  background: #fef0f0;
}

.x6-context-menu .context-item .el-icon {
  font-size: 15px;
}

.x6-context-menu .context-separator {
  height: 1px;
  background: #ebeef5;
  margin: 4px 8px;
}
</style>
