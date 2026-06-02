<template>
  <div class="design-editor-x6">
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar">
      <div class="toolbar-left">
        <el-button text @click="goBack">
          <el-icon><ArrowLeft /></el-icon> {{ $t('design.back') }}
        </el-button>
        <span v-if="appName" class="app-prefix">{{ appName }}</span>
        <span class="toolbar-title">{{ design?.name }}</span>
        <el-tag v-if="design" :type="design.status === 1 ? 'success' : 'danger'" size="small">
          {{ design.status === 1 ? $t('design.enabled') : $t('design.disabled') }}
        </el-tag>
      </div>
      <div class="toolbar-center">
        <el-tooltip :content="$t('design.undo')">
          <el-button text :disabled="!canUndo" @click="handleUndo"><el-icon><Back /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip :content="$t('design.redo')">
          <el-button text :disabled="!canRedo" @click="handleRedo"><el-icon><Right /></el-icon></el-button>
        </el-tooltip>
        <span class="toolbar-divider" />
        <el-tooltip :content="$t('design.copy')">
          <el-button text :disabled="selectedCount !== 1" @click="handleCopy"><el-icon><CopyDocument /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip :content="$t('design.paste')">
          <el-button text :disabled="!canPaste" @click="handlePaste"><el-icon><DocumentAdd /></el-icon></el-button>
        </el-tooltip>
        <span class="toolbar-divider" />
        <el-tooltip :content="$t('design.zoomOut')">
          <el-button text @click="zoomOut"><el-icon><ZoomOut /></el-icon></el-button>
        </el-tooltip>
        <span class="zoom-label">{{ Math.round(zoomLevel * 100) }}%</span>
        <el-tooltip :content="$t('design.zoomIn')">
          <el-button text @click="zoomIn"><el-icon><ZoomIn /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip :content="$t('design.fitCanvas')">
          <el-button text @click="zoomToFit"><el-icon><FullScreen /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip :content="$t('design.actualSize')">
          <el-button text @click="zoomReset"><el-icon><ScaleToOriginal /></el-icon></el-button>
        </el-tooltip>
        <span class="toolbar-divider" />
        <el-tooltip :content="$t('design.exportPng')">
          <el-button text @click="handleExport"><el-icon><Picture /></el-icon></el-button>
        </el-tooltip>
        <el-tooltip :content="$t('design.viewChainData')">
          <el-button text @click="showChainDataDialog">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M9 4H7a2 2 0 00-2 2v5a2 2 0 01-2 2 2 2 0 012 2v5a2 2 0 002 2h2" />
              <path d="M15 4h2a2 2 0 012 2v5a2 2 0 002 2 2 2 0 00-2 2v5a2 2 0 01-2 2h-2" />
            </svg>
          </el-button>
        </el-tooltip>
        <span class="toolbar-divider" />
        <el-select v-model="defaultEdgeStyle" size="small" style="width:80px" @change="onDefaultEdgeStyleChange">
          <el-option :label="$t('design.straightLine')" value="straight" />
          <el-option :label="$t('design.polylineLine')" value="polyline" />
          <el-option :label="$t('design.curveLine')" value="curve" />
        </el-select>
        <span class="toolbar-divider" />
        <!-- 对齐 -->
        <el-dropdown trigger="click" :disabled="selectedCount < 2" @command="onAlign">
          <el-button text size="small">{{ $t('design.align') }}<el-icon><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="left"><el-icon><ArrowLeft /></el-icon> {{ $t('design.alignLeft') }}</el-dropdown-item>
              <el-dropdown-item command="center">{{ $t('design.alignVCenter') }}</el-dropdown-item>
              <el-dropdown-item command="right"><el-icon><ArrowRight /></el-icon> {{ $t('design.alignRight') }}</el-dropdown-item>
              <el-dropdown-item command="top" divided>{{ $t('design.alignTop') }}</el-dropdown-item>
              <el-dropdown-item command="middle">{{ $t('design.alignHCenter') }}</el-dropdown-item>
              <el-dropdown-item command="bottom"><el-icon><ArrowDown /></el-icon> {{ $t('design.alignBottom') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <!-- 均匀分布 -->
        <el-dropdown trigger="click" :disabled="selectedCount < 3" @command="onDistribute">
          <el-button text size="small">{{ $t('design.distribute') }}<el-icon><ArrowDown /></el-icon></el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="horizontal"><el-icon><Sort /></el-icon> {{ $t('design.distributeH') }}</el-dropdown-item>
              <el-dropdown-item command="vertical"><el-icon><Sort /></el-icon> {{ $t('design.distributeV') }}</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <span class="toolbar-divider" />
        <!-- 网格吸附 -->
        <el-tooltip :content="gridSnapEnabled ? $t('design.snapDisable') : $t('design.snapEnable')">
          <el-button text @click="toggleSnap">
            <el-icon :style="gridSnapEnabled ? { color: '#3b82f6' } : {}"><Pointer /></el-icon>
          </el-button>
        </el-tooltip>
        <!-- 全屏 -->
        <el-tooltip :content="$t('design.fullscreen')">
          <el-button text @click="toggleFullscreen"><el-icon><FullScreen /></el-icon></el-button>
        </el-tooltip>
        <!-- 清空 -->
        <el-tooltip :content="$t('design.clearCanvas')">
          <el-button text @click="clearCanvas"><el-icon><Delete /></el-icon></el-button>
        </el-tooltip>
        <!-- 拖拽模式 -->
        <el-tooltip :content="panModeEnabled ? $t('design.exitPanMode') : $t('design.panMode')">
          <el-button text @click="togglePanMode">
            <el-icon :style="panModeEnabled ? { color: '#3b82f6' } : {}"><Rank /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
      <div class="toolbar-right">
        <el-tag v-if="selectedCount > 0" type="info" size="small" style="margin-right:8px">
          {{ $t('design.selected') }} {{ selectedCount }}
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
              draggable="true"
              @dragstart="onDragStart($event, nt)"
          >
            <div class="palette-icon" :style="{ background: nt.color }" v-html="nt.icon" />
            <div class="palette-label">{{ typeLabel(nt.type) }}</div>
          </div>
        </div>
      </div>

      <!-- 中间画布 -->
      <div class="canvas-area" ref="canvasContainerRef" @dragover.prevent="onDragOver" @drop.prevent="onDrop">
        <div ref="graphContainerRef" class="graph-container" />
        <div ref="minimapContainerRef" class="minimap-container" />
        <!-- 连线端点拖拽手柄 -->
        <div
            v-for="ep in endpointHandles" :key="ep.side"
            class="ep-handle"
            :style="{ left: ep.x + 'px', top: ep.y + 'px' }"
            @mousedown.prevent="onEpDragStart($event, ep.side)"
        />
      </div>

      <!-- 右侧属性面板 -->
      <div class="property-panel">
        <div class="panel-header">
          <span style="display:flex;align-items:center;gap:6px;flex-wrap:wrap">
            {{ selectedEdgeData ? $t('design.selectedEdge') : selectedNodeData ? $t('design.selectedNode') : $t('design.properties') }}
            <el-tag v-if="selectedNodeData" size="small" :color="nodeColor(selectedNodeData.nodeType)" style="color:#fff;border:none;margin-left:6px">
              {{ typeLabel(selectedNodeData.nodeType) }}
            </el-tag>
            <el-button v-if="selectedNodeData && canBindComponent(selectedNodeData.nodeType)" size="small" type="primary" plain @click="openBindDialog" style="margin-left:4px">
              {{ $t('design.bindComponent') }}
            </el-button>
          </span>
        </div>
        <!-- 节点属性 -->
        <div v-if="selectedNodeData" class="panel-body">
          <el-form size="small" label-position="top">
            <el-form-item v-if="canBindComponent(selectedNodeData.nodeType)" :label="$t('design.componentId')">
              <el-link v-if="selectedNodeData.componentId" type="primary" :underline="'never'" style="font-family:monospace;cursor:pointer" @click="openCompDetail(selectedNodeData)">
                {{ selectedNodeData.componentId }}
              </el-link>
              <span v-else style="color:#bbb;font-size:12px">{{ $t('design.autoFill') }}</span>
            </el-form-item>
            <el-form-item v-if="canBindComponent(selectedNodeData.nodeType)" :label="$t('design.componentName')">
              <el-input :model-value="selectedNodeData.componentName || ''" disabled :placeholder="$t('design.autoFill')" />
            </el-form-item>
            <el-form-item v-if="canBindComponent(selectedNodeData.nodeType)" :label="$t('design.executeStrategy')">
              <el-select v-model="selectedNodeData.executeStrategy" style="width:100%" @change="onDataChange">
                <el-option v-for="item in executeStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <!-- 参数解析器链 -->
            <div v-if="canBindComponent(selectedNodeData.nodeType)" style="padding:0 0 8px 0;width:100%">
              <div style="font-size:12px;color:#606266;margin-bottom:4px">{{ $t('components.typeParamBinder') }}</div>
              <div v-for="(item, idx) in (selectedNodeData.paramResolvers || [])" :key="idx" style="display:flex;align-items:center;gap:2px;margin-bottom:3px">
                <el-tag type="info" size="small" closable style="flex:1;overflow:hidden;text-align:left;justify-content:flex-start" @close="removeParamResolver(idx)">
                  <span style="opacity:0.6;margin-right:2px">{{ idx + 1 }}.</span>{{ item.componentName }}
                </el-tag>
                <el-button text size="small" :disabled="idx === 0" @click="moveParamResolver(idx, -1)" style="padding:0 2px">↑</el-button>
                <el-button text size="small" :disabled="idx === (selectedNodeData.paramResolvers || []).length - 1" @click="moveParamResolver(idx, 1)" style="padding:0 2px">↓</el-button>
              </div>
              <el-button size="small" type="primary" plain @click="openBindDialog('resolver')" style="width:100%">{{ $t('design.addResolver') }}</el-button>
            </div>
            <!-- 参数校验器 -->
            <div v-if="canBindComponent(selectedNodeData.nodeType)" style="padding:0 0 4px 0;width:100%">
              <div style="font-size:12px;color:#606266;margin-bottom:4px">{{ $t('components.typeParamValidator') }}</div>
              <el-tag v-if="selectedNodeData.paramValidatorName" type="info" size="small" closable style="margin-bottom:4px" @close="clearValidator()">
                {{ selectedNodeData.paramValidatorName }}
              </el-tag>
              <el-button size="small" type="primary" plain @click="openBindDialog('validator')" style="width:100%">
                {{ $t('design.bindComponent') }}
              </el-button>
            </div>
            <!-- 前置处理器 -->
            <div v-if="canBindComponent(selectedNodeData.nodeType)" style="padding:0 0 8px 0;width:100%">
              <div style="font-size:12px;color:#606266;margin-bottom:4px">{{ $t('design.preProcessor') }}</div>
              <div v-for="(item, idx) in (selectedNodeData.preComponents || [])" :key="idx" style="display:flex;align-items:center;gap:2px;margin-bottom:3px">
                <el-tag type="info" size="small" closable style="flex:1;overflow:hidden;text-align:left;justify-content:flex-start" @close="removePrePost('pre', idx)">
                  <span style="opacity:0.6;margin-right:2px">{{ idx + 1 }}.</span>{{ item.componentName }}
                </el-tag>
                <el-button text size="small" :disabled="idx === 0" @click="movePrePost('pre', idx, -1)" style="padding:0 2px">↑</el-button>
                <el-button text size="small" :disabled="idx === (selectedNodeData.preComponents || []).length - 1" @click="movePrePost('pre', idx, 1)" style="padding:0 2px">↓</el-button>
              </div>
              <el-button size="small" type="primary" plain @click="openBindDialog('pre')" style="width:100%">{{ $t('design.addPre') }}</el-button>
            </div>
            <!-- 后置处理器 -->
            <div v-if="canBindComponent(selectedNodeData.nodeType)" style="padding:0 0 8px 0;width:100%">
              <div style="font-size:12px;color:#606266;margin-bottom:4px">{{ $t('design.postProcessor') }}</div>
              <div v-for="(item, idx) in (selectedNodeData.postComponents || [])" :key="idx" style="display:flex;align-items:center;gap:2px;margin-bottom:3px">
                <el-tag type="info" size="small" closable style="flex:1;overflow:hidden;text-align:left;justify-content:flex-start" @close="removePrePost('post', idx)">
                  <span style="opacity:0.6;margin-right:2px">{{ idx + 1 }}.</span>{{ item.componentName }}
                </el-tag>
                <el-button text size="small" :disabled="idx === 0" @click="movePrePost('post', idx, -1)" style="padding:0 2px">↑</el-button>
                <el-button text size="small" :disabled="idx === (selectedNodeData.postComponents || []).length - 1" @click="movePrePost('post', idx, 1)" style="padding:0 2px">↓</el-button>
              </div>
              <el-button size="small" type="primary" plain @click="openBindDialog('post')" style="width:100%">{{ $t('design.addPost') }}</el-button>
            </div>
            <el-form-item v-if="hasDescription(selectedNodeData.nodeType)" :label="$t('design.script')">
              <el-input v-model="selectedNodeData.script" type="textarea" :rows="3" :placeholder="$t('design.scriptPlaceholder')" @input="onDataChange" />
            </el-form-item>
            <el-form-item v-if="hasDescription(selectedNodeData.nodeType)" :label="$t('design.description')">
              <el-input v-model="selectedNodeData.description" type="textarea" :rows="3" @input="onDataChange" />
            </el-form-item>
            <!-- 子链节点：子链编码 -->
            <el-form-item v-if="selectedNodeData.nodeType === 'subchain'" :label="$t('design.subChainCode')">
              <el-input v-model="selectedNodeData.subChainCode" :placeholder="$t('design.selectChain')" @input="onDataChange" />
            </el-form-item>
            <!-- 迭代器节点：数据源 + 迭代项名 -->
            <template v-if="selectedNodeData.nodeType === 'iterator'">
              <el-form-item :label="$t('design.iteratorDataSource')">
                <el-input v-model="selectedNodeData.iteratorDataSource" :placeholder="$t('design.iteratorDataSourcePlaceholder')" @input="onDataChange" />
              </el-form-item>
              <el-form-item :label="$t('design.iteratorItemName')">
                <el-input v-model="selectedNodeData.iteratorItemName" placeholder="item" @input="onDataChange" />
              </el-form-item>
            </template>
          </el-form>
        </div>
        <!-- 连线属性 -->
        <div v-else-if="selectedEdgeData" class="panel-body">
          <el-form size="small" label-position="top">
              <el-form-item :label="$t('design.labelValue')">
              <el-select v-if="edgeSourceTagDefs.length > 0" v-model="selectedEdgeData.label" :placeholder="$t('design.labelValuePlaceholder')" @change="onEdgeLabelChange" style="width:100%">
                <el-option v-for="td in edgeSourceTagDefs" :key="td.name" :label="td.name + ' (' + td.value + ')'" :value="td.name" />
              </el-select>
              <el-input v-else v-model="selectedEdgeData.label" :placeholder="$t('design.labelPlaceholder')" @input="onEdgeLabelChange" />
            </el-form-item>
            <el-form-item :label="$t('design.lineType')">
              <el-radio-group v-model="selectedEdgeStyle" size="small" @change="onEdgeStyleChange">
                <el-radio-button value="straight">{{ $t('design.straightLine') }}</el-radio-button>
                <el-radio-button value="polyline">{{ $t('design.polylineLine') }}</el-radio-button>
                <el-radio-button value="curve">{{ $t('design.curveLine') }}</el-radio-button>
              </el-radio-group>
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
          <el-icon><Delete /></el-icon> {{ $t('design.deleteNode') }}
        </div>
        <div v-if="contextMenu.isNode" class="context-item" @click="contextCopyNode">
          <el-icon><CopyDocument /></el-icon> {{ $t('design.copyNode') }}
        </div>
        <div v-if="contextMenu.isEdge" class="context-item danger" @click="contextDeleteEdge">
          <el-icon><Delete /></el-icon> {{ $t('design.deleteEdge') }}
        </div>
        <div v-if="contextMenu.isEdge" class="context-item" @click="contextEditEdgeLabel">
          <el-icon><Edit /></el-icon> {{ $t('design.editLabel') }}
        </div>
        <div v-if="contextMenu.isNode || contextMenu.isEdge" class="context-separator" />
        <div class="context-item" @click="contextSelectAll">
          <el-icon><Select /></el-icon> {{ $t('design.selectAll') }}
        </div>
        <div v-if="!contextMenu.isNode && !contextMenu.isEdge" class="context-item" @click="contextPaste">
          <el-icon><DocumentAdd /></el-icon> {{ $t('design.pasteNode') }}
        </div>
      </div>
    </teleport>

    <!-- 行内编辑输入框 -->
    <teleport to="body">
      <div v-if="inlineEditor.show" class="inline-editor-overlay" :style="{ left: inlineEditor.x + 'px', top: inlineEditor.y + 'px' }" @click.stop>
        <el-select
            v-if="inlineEditor.tagDefs.length > 0"
            v-model="inlineEditor.value"
            size="small"
            :placeholder="$t('design.selectTagValue')"
            @change="inlineEditorConfirm"
            @keydown.escape.prevent="inlineEditorCancel"
            @blur="inlineEditorConfirm"
            ref="inlineInputRef"
            popper-class="inline-editor-popper"
        >
          <el-option v-for="td in inlineEditor.tagDefs" :key="td.value" :label="td.name + ' (' + td.value + ')'" :value="td.name" />
        </el-select>
        <el-input
            v-else
            ref="inlineInputRef"
            v-model="inlineEditor.value"
            size="small"
            :placeholder="$t('design.inputLabel')"
            @keydown.enter.prevent="inlineEditorConfirm"
            @keydown.escape.prevent="inlineEditorCancel"
            @blur="inlineEditorConfirm"
        />
      </div>
    </teleport>

    <!-- 绑定元件弹窗 -->
    <el-dialog v-model="bindDialog.visible" :title="bindDialog.target === 'main' ? $t('design.bindTitle', { label: bindDialog.typeLabel }) : $t('design.addTitle', { target: bindTypeTargetLabel(bindDialog.target) })" width="1060px" @close="bindDialog.loading=false">
      <div style="margin-bottom:12px;display:flex;gap:8px">
        <el-input v-model="bindDialog.keyword" :placeholder="$t('design.searchComponent')" clearable style="width:200px" @keyup.enter="fetchBindList" />
        <el-select v-model="bindDialog.groupFilter" :placeholder="$t('design.allGroups')" clearable style="width:150px" @change="fetchBindList">
          <el-option v-for="g in bindGroupOptions" :key="g" :label="g" :value="g" />
        </el-select>
        <el-button type="primary" @click="fetchBindList">{{ $t('design.search') }}</el-button>
      </div>
      <el-table :data="bindFilteredList" v-loading="bindDialog.loading" stripe border height="250" style="width:100%"
                :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
                @row-click="onBindSelect"
      >
        <el-table-column prop="componentId" :label="$t('design.colComponentId')" width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="'never'" style="font-family:monospace;font-weight:500;cursor:pointer" @click.stop="openCompDetail(row)">
              {{ row.componentId }}
            </el-link>
          </template>
        </el-table-column>
        <el-table-column prop="componentName" :label="$t('design.colName')" show-overflow-tooltip min-width="80" />
        <el-table-column :label="$t('design.colType')" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="bindTypeTagType(row.componentType)" size="small">
              {{ bindTypeLabel(row.componentType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="groupName" :label="$t('design.colGroup')" width="90" show-overflow-tooltip />
        <el-table-column prop="executorSource" :label="$t('design.colSource')" width="140" show-overflow-tooltip />
        <el-table-column :label="$t('design.colTimeout')" width="70" align="center">
          <template #default="{ row }">{{ row.timeout === -1 ? '-' : row.timeout }}</template>
        </el-table-column>
        <el-table-column :label="$t('design.colAsync')" width="55" align="center">
          <template #default="{ row }">
            <el-tag :type="row.async ? 'warning' : 'info'" size="small">
              {{ row.async ? $t('components.yes') : $t('components.no') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('design.colStatus')" width="70" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? $t('components.online') : $t('components.offline') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="cachedAt" :label="$t('design.colCachedAt')" width="140" show-overflow-tooltip />
        <el-table-column :label="$t('design.colSelected')" width="55" align="center">
          <template #default="{ row }">
            <el-tag v-if="bindDialog.selectedIds.includes(row.componentId)" type="success" size="small">{{ $t('design.selected') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="$t('design.colTags')" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.tagDefs && row.tagDefs.length" size="small" type="info">{{ row.tagDefs.length }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;margin-top:12px">
        <el-pagination
            v-model:current-page="bindDialog.page"
            v-model:page-size="bindDialog.pageSize"
            :total="bindDialog.total"
            :page-sizes="[5, 10, 20]"
            layout="total, sizes, prev, pager, next"
            @current-change="fetchBindList"
            @size-change="fetchBindList"
        />
      </div>
      <template #footer>
        <el-button @click="bindDialog.visible=false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="confirmBind">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 元件详情抽屉 -->
    <el-drawer v-model="compDrawer.visible" :title="$t('design.componentDetail')" size="500px" destroy-on-close>
      <template v-if="compDrawer.data">
        <el-descriptions :column="1" border style="margin-bottom:16px">
          <el-descriptions-item :label="$t('design.detailComponentId')">
            <span style="font-family:monospace">{{ compDrawer.data.componentId }}</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('design.detailComponentName')">
            {{ compDrawer.data.componentName }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('design.detailType')">
            <el-tag size="small">{{ bindTypeLabel(compDrawer.data.componentType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('design.detailGroup')" v-if="compDrawer.data.groupName">
            {{ compDrawer.data.groupName }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('design.detailSource')" v-if="compDrawer.data.executorSource">
            {{ compDrawer.data.executorSource }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('design.detailTimeout')">
            {{ compDrawer.data.timeout === -1 ? '-' : compDrawer.data.timeout }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('design.detailAsync')">
            <el-tag :type="compDrawer.data.async ? 'warning' : 'info'" size="small">
              {{ compDrawer.data.async ? $t('components.yes') : $t('components.no') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('design.detailStatus')">
            <el-tag :type="compDrawer.data.status === 1 ? 'success' : 'info'" size="small">
              {{ compDrawer.data.status === 1 ? $t('components.online') : $t('components.offline') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('design.detailCachedAt')" v-if="compDrawer.data.cachedAt">
            {{ compDrawer.data.cachedAt }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('design.detailDescription')" v-if="compDrawer.data.description">
            {{ compDrawer.data.description }}
          </el-descriptions-item>
        </el-descriptions>

        <el-descriptions :column="1" border v-if="compDrawer.data.tagDefs && compDrawer.data.tagDefs.length > 0">
          <el-descriptions-item :label="$t('design.detailTags')">
            <el-table :data="compDrawer.data.tagDefs" border size="small" style="width:100%">
              <el-table-column :label="$t('design.detailTagName')" prop="name" show-overflow-tooltip />
              <el-table-column :label="$t('design.detailTagValue')" prop="value" show-overflow-tooltip width="140">
                <template #default="{ row }">
                  <el-tag size="small" type="info">{{ row.value }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>

    <!-- 链数据预览弹窗 -->
    <el-dialog v-model="chainDataDialog.visible" :title="$t('design.chainDataPreview')" width="900px" top="5vh">
      <div v-if="chainDataDialog.errors.length > 0" style="margin-bottom:12px">
        <el-alert
            v-for="(err, i) in chainDataDialog.errors" :key="i"
            :title="err" type="warning" show-icon :closable="false"
            style="margin-bottom:4px"
        />
      </div>
      <div v-if="chainDataDialog.success" style="margin-bottom:12px">
        <el-alert :title="$t('design.validationPassed')" type="success" show-icon :closable="false" />
      </div>
      <el-input
          v-model="chainDataDialog.json"
          type="textarea"
          :rows="20"
          readonly
          style="font-family:monospace;font-size:12px"
      />
      <template #footer>
        <el-button @click="chainDataDialog.visible=false">{{ $t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Graph, Node, Edge } from '@antv/x6'
import { History } from '@antv/x6-plugin-history'
import { Snapline } from '@antv/x6-plugin-snapline'
import { MiniMap } from '@antv/x6-plugin-minimap'
import { Selection } from '@antv/x6-plugin-selection'
import { Keyboard } from '@antv/x6-plugin-keyboard'
import { Clipboard } from '@antv/x6-plugin-clipboard'
import { Export } from '@antv/x6-plugin-export'
import { designApi } from '@/api/design'
import { componentApi } from '@/api/component'
import { executorApi } from '@/api/executor'
import {
  ArrowLeft, Check, Pointer, Back, Right,
  CopyDocument, DocumentAdd,
  ZoomIn, ZoomOut, FullScreen, ScaleToOriginal,
  Delete, Select, Edit, Picture,
  ArrowRight, ArrowDown, Sort, Rank,
} from '@element-plus/icons-vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const designCode = route.params.id as string
const appCode = route.query.appCode as string || ''

// ====== 响应式状态 ======
const design = ref<any>(null)
const appName = ref('')
const saving = ref(false)
const selectedCount = ref(0)
const canUndo = ref(false)
const canRedo = ref(false)
const selectedNodeData = ref<any>(null)
const selectedEdgeData = ref<any>(null)
const edgeSourceTagDefs = ref<Array<{name: string; value: string}>>([])
const zoomLevel = ref(1)
const canPaste = ref(false)
const nodeCount = ref(0)
const edgeCount = ref(0)
const selectedEdgeStyle = ref<'straight' | 'polyline' | 'curve'>('straight')
const defaultEdgeStyle = ref<'straight' | 'polyline' | 'curve'>('polyline')
const gridSnapEnabled = ref(false)
const panModeEnabled = ref(false)
const endpointHandles = ref<{ side: 'source' | 'target'; x: number; y: number }[]>([])
let draggingEp: { side: 'source' | 'target' } | null = null

const executeStrategyOptions = computed(() => [
  { value: 'NORMAL', label: t('design.strategyNormal') },
  { value: 'RETRY_ON_FAILURE', label: t('design.strategyRetry') },
  { value: 'STOP_ON_EXCEPTION', label: t('design.strategyStopOnException') },
  { value: 'IGNORE_EXCEPTION', label: t('design.strategyIgnoreException') },
])

// 绑定元件弹窗状态
const bindDialog = reactive({
  visible: false,
  loading: false,
  typeLabel: '',
  componentType: '',
  target: 'main',
  list: [] as any[],
  selectedId: '' as string,
  selectedIds: [] as string[],
  keyword: '',
  groupFilter: '',
  page: 1,
  pageSize: 5,
  total: 0,
})

// 元件详情抽屉
const compDrawer = reactive({
  visible: false,
  data: null as any,
})

// 链数据预览弹窗
const chainDataDialog = reactive({
  visible: false,
  json: '',
  errors: [] as string[],
  success: false,
})

function openCompDetail(row: any) {
  // 优先从绑定列表中补全详情；如果是节点属性面板传入，可能缺少来源/采集时间等字段
  const full = row.executorSource ? row : bindDialog.list.find((c: any) => c.componentId === row.componentId)
  compDrawer.data = full || row
  compDrawer.visible = true
}

const bindFilteredList = computed(() => {
  if (!bindDialog.groupFilter) return bindDialog.list
  return bindDialog.list.filter((item: any) => item.groupName === bindDialog.groupFilter)
})

const bindGroupOptions = computed(() => {
  const groups = new Set<string>()
  bindDialog.list.forEach((item: any) => {
    if (item.groupName) groups.add(item.groupName)
  })
  return Array.from(groups).sort()
})

const graphContainerRef = ref<HTMLDivElement | null>(null)
const canvasContainerRef = ref<HTMLDivElement | null>(null)
const minimapContainerRef = ref<HTMLDivElement | null>(null)
const inlineInputRef = ref<any>(null)

let graph: Graph | null = null
let resizeObserver: ResizeObserver | null = null
let selectedCell: any = null

// ====== 行内编辑器 ======
const inlineEditor = reactive({
  show: false,
  x: 0,
  y: 0,
  value: '',
  target: null as any,
  isEdge: false,
  tagDefs: [] as Array<{name: string; value: string}>,
})

function showInlineEditor(x: number, y: number, value: string, target: any, isEdge: boolean, tagDefs?: Array<{name: string; value: string}>) {
  inlineEditor.show = true
  inlineEditor.x = x
  inlineEditor.y = y
  inlineEditor.value = value
  inlineEditor.target = target
  inlineEditor.isEdge = isEdge
  inlineEditor.tagDefs = tagDefs || []
  nextTick(() => {
    if (tagDefs && tagDefs.length > 0) {
      // select 下拉不需要 focus
    } else {
      inlineInputRef.value?.focus()
    }
  })
  setTimeout(() => document.addEventListener('click', inlineEditorOutsideClick), 0)
}

function setEdgeLabelSafe(edge: any, text: string) {
  if (!text) { edge.setLabels([]); return }
  // 一律创建新对象，确保 X6 检测到变更触发重绘
  edge.setLabels([{
    attrs: {
      labelBg: { fill: '#fff', rx: 4, ry: 4, stroke: '#e2e8f0', strokeWidth: 1, pointerEvents: 'auto', cursor: 'move' },
      label: { text, fill: '#475569', fontSize: 12, textAnchor: 'middle', textVerticalAnchor: 'middle', refX: '50%', pointerEvents: 'auto', cursor: 'move' },
    },
    position: { distance: 0.5 },
  }])
}

function inlineEditorConfirm() {
  if (!inlineEditor.target) return
  if (inlineEditor.isEdge) {
    const text = inlineEditor.value || ''
    setEdgeLabelSafe(inlineEditor.target, text)
    if (selectedEdgeData.value) selectedEdgeData.value.label = text
  } else {
    const data = { ...inlineEditor.target.getData(), label: inlineEditor.value || inlineEditor.target.id }
    inlineEditor.target.setData(data)
  }
  inlineEditor.show = false
  inlineEditor.target = null
}

function inlineEditorCancel() {
  inlineEditor.show = false
  inlineEditor.target = null
  document.removeEventListener('click', inlineEditorOutsideClick)
}

function inlineEditorOutsideClick(e: MouseEvent) {
  if (!inlineEditor.show) return
  const overlay = document.querySelector('.inline-editor-overlay')
  if (overlay?.contains(e.target as any)) return
  const popper = (e.target as HTMLElement).closest('.el-select-dropdown, .el-popper')
  if (popper) return
  inlineEditorCancel()
}

// ====== 右键菜单 ======
const contextMenu = reactive({ visible: false, x: 0, y: 0, isNode: false, isEdge: false, cell: null as any })
let contextMenuCloseHandler: (() => void) | null = null

function closeContextMenu() {
  contextMenu.visible = false
  contextMenu.cell = null
  contextMenu.isNode = false
  contextMenu.isEdge = false
  if (contextMenuCloseHandler) {
    document.removeEventListener('click', contextMenuCloseHandler)
    contextMenuCloseHandler = null
  }
}

function onCanvasContextMenu(e: MouseEvent) {
  if (!graph) return
  const cell = (graph as any).getCellAt(e.clientX, e.clientY)
  contextMenu.isNode = !!cell && cell.isNode()
  contextMenu.isEdge = !!cell && cell.isEdge()
  contextMenu.cell = cell
  contextMenu.x = e.clientX
  contextMenu.y = e.clientY
  contextMenu.visible = true
  closeContextMenu()
  contextMenuCloseHandler = () => { closeContextMenu() }
  setTimeout(() => document.addEventListener('click', contextMenuCloseHandler!), 0)
}

function contextDeleteNode() {
  if (contextMenu.cell) { graph?.removeCells([contextMenu.cell]); clearSelectionIfNeeded(contextMenu.cell) }
  closeContextMenu()
}

function contextDeleteEdge() {
  if (contextMenu.cell) { graph?.removeCells([contextMenu.cell]); clearSelectionIfNeeded(contextMenu.cell) }
  closeContextMenu()
}

function contextCopyNode() {
  if (contextMenu.cell && graph) { graph.copy([contextMenu.cell]); canPaste.value = true }
  closeContextMenu()
}

function contextSelectAll() {
  graph?.getCells().filter(c => c.isNode()).forEach(c => graph?.select(c.id))
  closeContextMenu()
}

function contextPaste() { handlePaste(); closeContextMenu() }

function contextEditEdgeLabel() {
  if (contextMenu.cell && contextMenu.cell.isEdge()) {
    closeContextMenu()
    triggerEdgeLabelEdit(contextMenu.cell)
  }
}

function clearSelectionIfNeeded(cell: any) {
  if (selectedCell === cell) { selectedCell = null; selectedNodeData.value = null; selectedEdgeData.value = null }
}

// ====== 节点类型定义 ======
const nodeColors: Record<string, string> = {
  start: '#22c55e',
  task: '#3b82f6',
  condition: '#f59e0b',
  multicondition: '#8b5cf6',
  loader: '#06b6d4',
  parser: '#ec4899',
  script: '#8b5cf6',
  subchain: '#06b6d4',
  iterator: '#f97316',
  end: '#6b7280',
}

const nodeTypes = [
  { type: 'start', label: '开始', color: '#22c55e', icon: '<svg viewBox="0 0 14 14"><circle cx="7" cy="7" r="6" fill="currentColor"/></svg>' },
  { type: 'task', label: '执行元件', color: '#3b82f6', icon: '<svg viewBox="0 0 14 14"><rect x="2" y="1" width="10" height="12" rx="2" fill="currentColor"/></svg>' },
  { type: 'condition', label: '判断元件', color: '#f59e0b', icon: '<svg viewBox="0 0 14 14"><polygon points="7,0 14,7 7,14 0,7" fill="currentColor"/></svg>' },
  { type: 'multicondition', label: '选择器元件', color: '#8b5cf6', icon: '<svg viewBox="0 0 14 14"><polygon points="10,0 14,7 10,14 4,14 0,7 4,0" fill="currentColor"/></svg>' },
  { type: 'loader', label: '加载器元件', color: '#06b6d4', icon: '<svg viewBox="0 0 14 14"><path d="M7,0 L14,3 L14,11 L7,14 L0,11 L0,3 Z" fill="currentColor"/></svg>' },
  { type: 'parser', label: '解析器元件', color: '#ec4899', icon: '<svg viewBox="0 0 14 14"><path d="M2,1 L12,1 L12,13 L2,13 Z M4,4 L10,4 M4,7 L10,7 M4,10 L8,10" fill="none" stroke="currentColor" stroke-width="2"/></svg>' },
  { type: 'script', label: '脚本元件', color: '#8b5cf6', icon: '<svg viewBox="0 0 14 14"><path d="M4,0 L14,0 L14,10 L10,14 L0,14 L0,4 Z M5,5 L9,9 M9,5 L5,9" fill="none" stroke="currentColor" stroke-width="1.5"/></svg>' },
  { type: 'subchain', label: '子链元件', color: '#06b6d4', icon: '<svg viewBox="0 0 14 14"><path d="M2,4 L8,4 L8,10 L2,10 Z M6,7 L12,7 L12,13 L6,13 Z" fill="none" stroke="currentColor" stroke-width="1.5"/></svg>' },
  { type: 'iterator', label: '迭代器元件', color: '#f97316', icon: '<svg viewBox="0 0 14 14"><path d="M7,0 L14,7 L7,14 L0,7 Z M10,5 L10,9 M4,5 L4,9" fill="none" stroke="currentColor" stroke-width="1.5"/></svg>' },
  { type: 'end', label: '结束', color: '#6b7280', icon: '<svg viewBox="0 0 14 14"><circle cx="7" cy="7" r="5" fill="none" stroke="currentColor" stroke-width="2"/><circle cx="7" cy="7" r="2" fill="currentColor"/></svg>' },
]

function nodeColor(type: string) { return nodeColors[type] || '#3b82f6' }

function typeLabel(type: string) {
  const key = `design.${type}Node`
  const translated = t(key)
  return translated !== key ? translated : (nodeTypes.find(nt => nt.type === type)?.label || type)
}

function hasDescription(type: string) { return type === 'task' || type === 'condition' || type === 'multicondition' || type === 'loader' || type === 'parser' || type === 'script' || type === 'subchain' || type === 'iterator' }

// ====== 绑定元件 ======

/** 元件类型标签 */
function bindTypeLabel(type: string): string {
  const map: Record<string, string> = {
    EXECUTOR: 'components.typeExecutor',
    PREDICATE: 'components.typePredicate',
    SELECTOR: 'components.typeSelector',
    LOADER: 'components.typeLoader',
    PARSER: 'components.typeParser',
    PRE_PROCESSOR: 'components.typePreProcessor',
    POST_PROCESSOR: 'components.typePostProcessor',
    PARAM_BINDER: 'components.typeParamBinder',
    PARAM_VALIDATOR: 'components.typeParamValidator',
  }
  return map[type] ? t(map[type]) : type
}

function bindTypeTagType(type: string): string {
  const map: Record<string, string> = {
    EXECUTOR: 'primary',
    PREDICATE: 'warning',
    SELECTOR: '',
    LOADER: 'cyan',
    PARSER: 'success',
    PRE_PROCESSOR: '',
    POST_PROCESSOR: 'success',
    PARAM_BINDER: '',
    PARAM_VALIDATOR: 'success',
  }
  return map[type] || 'info'
}

function bindTypeTargetLabel(target: string): string {
  const map: Record<string, string> = {
    pre: 'design.preProcessor',
    post: 'design.postProcessor',
    resolver: 'design.paramResolver',
    validator: 'components.typeParamValidator',
  }
  return map[target] ? t(map[target]) : ''
}

/** 节点类型 → 元件类型映射 */
function typeToComponentType(nodeType: string): string {
  const map: Record<string, string> = {
    task: 'EXECUTOR',
    condition: 'PREDICATE',
    multicondition: 'SELECTOR',
    loader: 'LOADER',
    parser: 'PARSER',
  }
  return map[nodeType] || ''
}

/** 非开始/结束节点可绑定元件 */
function canBindComponent(nodeType: string) {
  return typeToComponentType(nodeType) !== ''
}

async function openBindDialog(target: string = 'main') {
  if (!selectedNodeData.value) return
  bindDialog.target = target
  let ct: string
  if (target === 'pre') {
    ct = 'PRE_PROCESSOR'
    bindDialog.typeLabel = t('components.typePreProcessor')
    bindDialog.selectedId = ''
    bindDialog.selectedIds = (selectedNodeData.value.preComponents || []).map((p: any) => p.componentId)
  } else if (target === 'post') {
    ct = 'POST_PROCESSOR'
    bindDialog.typeLabel = t('components.typePostProcessor')
    bindDialog.selectedId = ''
    bindDialog.selectedIds = (selectedNodeData.value.postComponents || []).map((p: any) => p.componentId)
  } else if (target === 'resolver') {
    ct = 'PARAM_BINDER'
    bindDialog.typeLabel = t('design.paramResolver')
    bindDialog.selectedId = ''
    bindDialog.selectedIds = (selectedNodeData.value.paramResolvers || []).map((p: any) => p.componentId)
  } else if (target === 'validator') {
    ct = 'PARAM_VALIDATOR'
    bindDialog.typeLabel = t('components.typeParamValidator')
    bindDialog.selectedId = selectedNodeData.value.paramValidatorId || ''
    bindDialog.selectedIds = bindDialog.selectedId ? [bindDialog.selectedId] : []
  } else {
    ct = typeToComponentType(selectedNodeData.value.nodeType)
    if (!ct) return
    bindDialog.typeLabel = typeLabel(selectedNodeData.value.nodeType)
    bindDialog.selectedId = selectedNodeData.value.componentId || ''
    bindDialog.selectedIds = bindDialog.selectedId ? [bindDialog.selectedId] : []
  }
  bindDialog.componentType = ct
  bindDialog.keyword = ''
  bindDialog.groupFilter = ''
  bindDialog.page = 1
  bindDialog.pageSize = 5
  bindDialog.total = 0
  bindDialog.visible = true
  await fetchBindList()
}

async function fetchBindList() {
  bindDialog.loading = true
  try {
    const res = await componentApi.list({
      appCode,
      componentType: bindDialog.componentType,
      keyword: bindDialog.keyword || undefined,
      page: bindDialog.page,
      size: bindDialog.pageSize,
    })
    bindDialog.list = res.records || []
    bindDialog.total = res.total
  } catch {
    bindDialog.list = []
    bindDialog.total = 0
  } finally {
    bindDialog.loading = false
  }
}

function onBindSelect(row: any) {
  bindDialog.selectedId = bindDialog.selectedId === row.componentId ? '' : row.componentId
  // 同步 selectedIds，实时反映选中状态
  const existing = (() => {
    if (bindDialog.target === 'pre') return (selectedNodeData.value?.preComponents || []).map((p: any) => p.componentId)
    if (bindDialog.target === 'post') return (selectedNodeData.value?.postComponents || []).map((p: any) => p.componentId)
    if (bindDialog.target === 'resolver') return (selectedNodeData.value?.paramResolvers || []).map((p: any) => p.componentId)
    return []
  })()
  bindDialog.selectedIds = [...existing]
  if (bindDialog.selectedId) bindDialog.selectedIds.push(bindDialog.selectedId)
}

function confirmBind() {
  bindDialog.visible = false
  if (!selectedCell || !selectedNodeData.value) return
  if (bindDialog.target === 'pre' || bindDialog.target === 'post' || bindDialog.target === 'resolver') {
    // 前置/后置/解析器：追加到数组
    if (!bindDialog.selectedId) return
    const found = bindDialog.list.find((c: any) => c.componentId === bindDialog.selectedId)
    if (!found) return
    const key = bindDialog.target === 'pre' ? 'preComponents' : bindDialog.target === 'post' ? 'postComponents' : 'paramResolvers'
    if (!selectedNodeData.value[key]) selectedNodeData.value[key] = []
    selectedNodeData.value[key].push({ componentId: found.componentId, componentName: found.componentName })
    selectedCell.setData({ ...selectedNodeData.value })
    return
  }
  // 参数校验器：单槽绑定
  if (bindDialog.target === 'validator') {
    if (!bindDialog.selectedId) return
    const found = bindDialog.list.find((c: any) => c.componentId === bindDialog.selectedId)
    if (!found) return
    selectedNodeData.value.paramValidatorId = found.componentId
    selectedNodeData.value.paramValidatorName = found.componentName
    selectedCell.setData({ ...selectedNodeData.value })
    return
  }
  // 主元件绑定
  const found = bindDialog.list.find((c: any) => c.componentId === bindDialog.selectedId)
  if (found) {
    selectedNodeData.value.componentId = found.componentId
    selectedNodeData.value.componentName = found.componentName
    selectedNodeData.value.tagDefs = found.tagDefs || []
    selectedNodeData.value.label = found.componentName || found.componentId
  } else {
    delete selectedNodeData.value.componentId
    delete selectedNodeData.value.componentName
    selectedNodeData.value.label = typeLabel(selectedNodeData.value.nodeType)
  }
  selectedCell.setData({ ...selectedNodeData.value })
  selectedCell.setLabels(selectedNodeData.value.label ? [{ attrs: { label: { text: selectedNodeData.value.label } } }] : [])
}

function removePrePost(target: string, idx: number) {
  if (!selectedCell || !selectedNodeData.value) return
  const key = target === 'pre' ? 'preComponents' : 'postComponents'
  const arr = selectedNodeData.value[key]
  if (arr) {
    arr.splice(idx, 1)
    selectedCell.setData({ ...selectedNodeData.value })
  }
}

function movePrePost(target: string, idx: number, direction: number) {
  if (!selectedCell || !selectedNodeData.value) return
  const key = target === 'pre' ? 'preComponents' : 'postComponents'
  const arr = selectedNodeData.value[key]
  if (!arr) return
  const newIdx = idx + direction
  if (newIdx < 0 || newIdx >= arr.length) return
  const tmp = arr[newIdx]
  arr[newIdx] = arr[idx]
  arr[idx] = tmp
  selectedCell.setData({ ...selectedNodeData.value })
}

function unbindComponent() {
  if (!selectedCell || !selectedNodeData.value) return
  delete selectedNodeData.value.componentId
  delete selectedNodeData.value.componentName
  delete selectedNodeData.value.tagDefs
  selectedNodeData.value.label = typeLabel(selectedNodeData.value.nodeType)
  selectedCell.setData({ ...selectedNodeData.value })
  selectedCell.setLabels(selectedNodeData.value.label ? [{ attrs: { label: { text: selectedNodeData.value.label } } }] : [])
}

function clearValidator() {
  if (!selectedCell || !selectedNodeData.value) return
  delete selectedNodeData.value.paramValidatorId
  delete selectedNodeData.value.paramValidatorName
  selectedCell.setData({ ...selectedNodeData.value })
}

function removeParamResolver(idx: number) {
  if (!selectedCell || !selectedNodeData.value) return
  const arr = selectedNodeData.value.paramResolvers
  if (arr) {
    arr.splice(idx, 1)
    selectedCell.setData({ ...selectedNodeData.value })
  }
}

function moveParamResolver(idx: number, direction: number) {
  if (!selectedCell || !selectedNodeData.value) return
  const arr = selectedNodeData.value.paramResolvers
  if (!arr) return
  const newIdx = idx + direction
  if (newIdx < 0 || newIdx >= arr.length) return
  const tmp = arr[newIdx]
  arr[newIdx] = arr[idx]
  arr[idx] = tmp
  selectedCell.setData({ ...selectedNodeData.value })
}

// ====== 连接手柄端口 ======
const handleGroup = {
  position: { name: 'absolute' },
  attrs: {
    circle: { r: 7, magnet: true, stroke: '#fff', fill: '#fff', strokeWidth: 2.5, cursor: 'crosshair',
      'stroke-opacity': 0, 'fill-opacity': 0 },
  },
  zIndex: 10,
}

function getPorts(type: string) {
  // 矩形类（开始/结束/任务/加载器/解析器）：每条边 2 个端口，共 8 个
  if (type === 'start' || type === 'end' || type === 'task' || type === 'loader' || type === 'parser') {
    return [
      { id: 't',  group: 'handle', args: { x: '50%', y: '0%' } },
      { id: 'tr', group: 'handle', args: { x: '100%', y: '15%' } },
      { id: 'r',  group: 'handle', args: { x: '100%', y: '50%' } },
      { id: 'br', group: 'handle', args: { x: '100%', y: '85%' } },
      { id: 'b',  group: 'handle', args: { x: '50%', y: '100%' } },
      { id: 'bl', group: 'handle', args: { x: '0%', y: '85%' } },
      { id: 'l',  group: 'handle', args: { x: '0%', y: '50%' } },
      { id: 'tl', group: 'handle', args: { x: '0%', y: '15%' } },
    ]
  }
  // 菱形（条件）：4 个顶点
  if (type === 'condition') {
    return [
      { id: 't', group: 'handle', args: { x: '50%', y: '0%' } },
      { id: 'r', group: 'handle', args: { x: '100%', y: '50%' } },
      { id: 'b', group: 'handle', args: { x: '50%', y: '100%' } },
      { id: 'l', group: 'handle', args: { x: '0%', y: '50%' } },
    ]
  }
  // 六边形（多条件）：6 个顶点
  if (type === 'multicondition') {
    return [
      { id: 'tr', group: 'handle', args: { x: '71%', y: '0%' } },
      { id: 'r',  group: 'handle', args: { x: '100%', y: '50%' } },
      { id: 'br', group: 'handle', args: { x: '71%', y: '100%' } },
      { id: 'bl', group: 'handle', args: { x: '29%', y: '100%' } },
      { id: 'l',  group: 'handle', args: { x: '0%', y: '50%' } },
      { id: 'tl', group: 'handle', args: { x: '29%', y: '0%' } },
    ]
  }
  return []
}

// 手柄可见性切换
function showPorts(node: Node) {
  const nodeType = node.getData()?.nodeType || 'task'
  const color = nodeColors[nodeType] || '#3b82f6'
  graph?.batchUpdate(() => {
    node.getPorts().forEach(p => {
      node.setPortProp((p as any).id, 'attrs/circle/stroke-opacity', 1)
      node.setPortProp((p as any).id, 'attrs/circle/fill-opacity', 0.3)
      node.setPortProp((p as any).id, 'attrs/circle/stroke', color)
      node.setPortProp((p as any).id, 'attrs/circle/fill', '#fff')
    })
  })
}
function hidePorts(node: Node) {
  graph?.batchUpdate(() => {
    node.getPorts().forEach(p => node.setPortProp((p as any).id, 'attrs/circle/stroke-opacity', 0))
    node.getPorts().forEach(p => node.setPortProp((p as any).id, 'attrs/circle/fill-opacity', 0))
  })
}

// ====== 注册 X6 原生形状 ======
function registerShapes() {
  function reg(name: string, def: any) {
    try { Graph.registerNode(name, def) } catch { /* ignore duplicate HMR */ }
  }

  // --- 开始节点（绿色圆角矩形） ---
  reg('flow-start', {
    inherit: 'rect',
    attrs: {
      body: { rx: 20, ry: 20, fill: nodeColors.start, stroke: 'none' },
      label: { text: t('design.startNode'), fill: '#fff', fontSize: 13, fontWeight: 600, refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle', cursor: 'pointer' },
    },
    ports: { groups: { handle: handleGroup }, items: getPorts('start') },
  })

  // --- 结束节点（灰色圆角矩形） ---
  reg('flow-end', {
    inherit: 'rect',
    width: 148,
    height: 40,
    markup: [{ tagName: 'rect', selector: 'body' }, { tagName: 'text', selector: 'label' }],
    attrs: {
      body: { rx: 20, ry: 20, fill: nodeColors.end, stroke: 'none' },
      label: { text: t('design.endNode'), fill: '#fff', fontSize: 13, fontWeight: 600, refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle', cursor: 'pointer' },
    },
    ports: { groups: { handle: handleGroup }, items: getPorts('end') },
  })

  // --- 执行元件（蓝底白字） ---
  reg('flow-task', {
    inherit: 'rect',
    width: 160,
    height: 46,
    markup: [{ tagName: 'rect', selector: 'body' }, { tagName: 'text', selector: 'label' }],
    attrs: {
      body: { rx: 8, ry: 8, fill: nodeColors.task, stroke: 'none' },
      label: { text: t('design.taskNode'), fill: '#ffffff', fontSize: 13, fontWeight: 600, refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle', cursor: 'pointer' },
    },
    ports: { groups: { handle: handleGroup }, items: getPorts('task') },
  })

  // --- 判断元件（橘色菱形） ---
  reg('flow-condition', {
    inherit: 'polygon',
    width: 100,
    height: 80,
    markup: [{ tagName: 'polygon', selector: 'body' }, { tagName: 'text', selector: 'label' }],
    attrs: {
      body: { refPoints: '50,0 100,40 50,80 0,40', fill: nodeColors.condition, stroke: 'none' },
      label: { text: t('design.conditionNode'), fill: '#ffffff', fontSize: 13, fontWeight: 600, refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle', cursor: 'pointer' },
    },
    ports: { groups: { handle: handleGroup }, items: getPorts('condition') },
  })

  // --- 选择器元件（紫色六边形） ---
  reg('flow-multicondition', {
    inherit: 'polygon',
    width: 120,
    height: 80,
    markup: [{ tagName: 'polygon', selector: 'body' }, { tagName: 'text', selector: 'label' }],
    attrs: {
      body: { refPoints: '85,0 120,40 85,80 35,80 0,40 35,0', fill: nodeColors.multicondition, stroke: 'none' },
      label: { text: t('design.multiconditionNode'), fill: '#ffffff', fontSize: 13, fontWeight: 600, refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle', cursor: 'pointer' },
    },
    ports: { groups: { handle: handleGroup }, items: getPorts('multicondition') },
  })

  // --- 加载器元件（青色圆角矩形） ---
  reg('flow-loader', {
    inherit: 'rect',
    width: 160,
    height: 46,
    markup: [{ tagName: 'rect', selector: 'body' }, { tagName: 'text', selector: 'label' }],
    attrs: {
      body: { rx: 8, ry: 8, fill: nodeColors.loader, stroke: 'none' },
      label: { text: t('design.loaderNode'), fill: '#ffffff', fontSize: 13, fontWeight: 600, refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle', cursor: 'pointer' },
    },
    ports: { groups: { handle: handleGroup }, items: getPorts('loader') },
  })

  // --- 解析器元件（粉色圆角矩形） ---
  reg('flow-parser', {
    inherit: 'rect',
    width: 160,
    height: 46,
    markup: [{ tagName: 'rect', selector: 'body' }, { tagName: 'text', selector: 'label' }],
    attrs: {
      body: { rx: 8, ry: 8, fill: nodeColors.parser, stroke: 'none' },
      label: { text: t('design.parserNode'), fill: '#ffffff', fontSize: 13, fontWeight: 600, refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle', cursor: 'pointer' },
    },
    ports: { groups: { handle: handleGroup }, items: getPorts('parser') },
  })

  // --- 脚本元件（紫色圆角矩形） ---
  reg('flow-script', {
    inherit: 'rect',
    width: 160,
    height: 46,
    markup: [{ tagName: 'rect', selector: 'body' }, { tagName: 'text', selector: 'label' }],
    attrs: {
      body: { rx: 8, ry: 8, fill: nodeColors.script, stroke: 'none' },
      label: { text: t('design.scriptNode'), fill: '#ffffff', fontSize: 13, fontWeight: 600, refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle', cursor: 'pointer' },
    },
    ports: { groups: { handle: handleGroup }, items: getPorts('script') },
  })

  // --- 子链元件（青色胶囊形） ---
  reg('flow-subchain', {
    inherit: 'rect',
    width: 160,
    height: 46,
    markup: [{ tagName: 'rect', selector: 'body' }, { tagName: 'text', selector: 'label' }],
    attrs: {
      body: { rx: 23, ry: 23, fill: nodeColors.subchain, stroke: 'none' },
      label: { text: t('design.subchainNode'), fill: '#ffffff', fontSize: 13, fontWeight: 600, refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle', cursor: 'pointer' },
    },
    ports: { groups: { handle: handleGroup }, items: getPorts('subchain') },
  })

  // --- 迭代器元件（橙色圆角矩形 + 虚线边框） ---
  reg('flow-iterator', {
    inherit: 'rect',
    width: 160,
    height: 46,
    markup: [{ tagName: 'rect', selector: 'body' }, { tagName: 'text', selector: 'label' }],
    attrs: {
      body: { rx: 8, ry: 8, fill: nodeColors.iterator, stroke: '#fff', strokeWidth: 2, strokeDasharray: '4,2' },
      label: { text: t('design.iteratorNode'), fill: '#ffffff', fontSize: 13, fontWeight: 600, refX: 0.5, refY: 0.5, textAnchor: 'middle', textVerticalAnchor: 'middle', cursor: 'pointer' },
    },
    ports: { groups: { handle: handleGroup }, items: getPorts('iterator') },
  })
}

// ====== 更新节点视觉 ======
function updateNodeVisual(node: Node) {
  const data = node.getData() || {}
  const nt = data.nodeType || 'task'
  node.attr('label/text', data.label || typeLabel(nt))
}

/** 加载后修正连线：同列/同行短距用直线，避免 manhattan 在近距离绕圈 */
function normalizeLoadedEdges() {
  if (!graph) return
  const COL_THRESHOLD = 48
  const ROW_THRESHOLD = 36
  graph.getEdges().forEach(e => {
    const src = e.getSourceNode()
    const tgt = e.getTargetNode()
    if (!src || !tgt) return
    const sb = src.getBBox()
    const tb = tgt.getBBox()
    const srcCx = sb.x + sb.width / 2
    const tgtCx = tb.x + tb.width / 2
    const srcCy = sb.y + sb.height / 2
    const tgtCy = tb.y + tb.height / 2
    const dx = Math.abs(srcCx - tgtCx)
    const dy = tgtCy - srcCy
    if (dx <= COL_THRESHOLD && dy > 0 && dy < 400) {
      e.setRouter({ name: 'normal' })
      e.setConnector({ name: 'normal' })
      return
    }
    if (Math.abs(dy) <= ROW_THRESHOLD && tb.x > sb.x + sb.width * 0.3) {
      e.setRouter({ name: 'normal' })
      e.setConnector({ name: 'normal' })
      return
    }
    const r = e.getRouter()
    if (r?.name === 'orth') {
      e.setRouter({ name: 'manhattan', args: { padding: { top: 15, bottom: 15, left: 15, right: 15 }, step: 10 } })
      e.setConnector('rounded')
    }
  })
}

// ====== 获取形状名 ======
function getShapeForType(nodeType: string): string {
  return {
    start: 'flow-start', task: 'flow-task', condition: 'flow-condition',
    multicondition: 'flow-multicondition', loader: 'flow-loader', parser: 'flow-parser',
    script: 'flow-script', subchain: 'flow-subchain', iterator: 'flow-iterator',
    end: 'flow-end',
  }[nodeType] || 'flow-task'
}

// ====== 初始化 Graph ======
function initGraph() {
  if (!graphContainerRef.value) return

  graph = new Graph({
    container: graphContainerRef.value,
    grid: { visible: true, size: 20, type: 'dot' },
    panning: { enabled: true, eventTypes: ['rightMouseDown'] },
    mousewheel: { enabled: true, zoomAtMousePosition: true },
    interacting: { edgeLabelMovable: true },
    connecting: {
      router: { name: 'manhattan', args: { padding: { top: 15, bottom: 15, left: 15, right: 15 }, step: 10 } },
      connector: { name: 'rounded' },
      snap: { radius: 40 },
      allowBlank: false,
      allowNode: true,
      highlight: false,
      targetAnchor: { name: 'orth' },
      connectionPoint: { name: 'anchor' },
      validateConnection({ sourceCell, targetCell }: { sourceCell: any; targetCell: any }) {
        if (!sourceCell || !targetCell) return false
        if (sourceCell.id === targetCell.id) return false
        return true
      },
    },
    defaultEdge: {
      router: { name: 'manhattan', args: { padding: { top: 15, bottom: 15, left: 15, right: 15 }, step: 10 } },
      connector: { name: 'rounded' },
      attrs: {
        line: { stroke: '#94a3b8', strokeWidth: 2, targetMarker: { name: 'classic', size: 8 } },
      },
      label: {
        markup: [{ tagName: 'rect', selector: 'labelBg' }, { tagName: 'text', selector: 'label' }],
        attrs: {
          labelBg: { fill: '#fff', rx: 4, ry: 4, stroke: '#e2e8f0', strokeWidth: 1, pointerEvents: 'auto', cursor: 'move' },
          label: { text: '', fill: '#475569', fontSize: 12, textAnchor: 'middle', textVerticalAnchor: 'middle', refX: '50%', pointerEvents: 'auto', cursor: 'move' },
        },
        position: { distance: 0.5 },
      },
    },
    sorting: 'approx',
  } as any)

  // 插件
  graph.use(new Snapline({ enabled: true, sharp: true }))
  graph.use(new Selection({
    enabled: true, multiple: true, rubberEdge: true, rubberNode: true, rubberband: true, showNodeSelectionBox: true,
  }))
  graph.use(new MiniMap({ container: minimapContainerRef.value!, width: 200, height: 140 }))
  graph.use(new History({ enabled: true }))
  graph.use(new Keyboard({ enabled: true }))
  graph.use(new Clipboard())
  graph.use(new Export())

  // 键盘快捷键
  graph.bindKey('backspace', () => removeSelected())
  graph.bindKey('del', () => removeSelected())
  graph.bindKey('ctrl+z', () => graph?.undo())
  graph.bindKey('ctrl+y', () => graph?.redo())
  graph.bindKey('ctrl+c', () => { if (graph) { const c = graph.getSelectedCells(); if (c.length > 0) { graph.copy(c); canPaste.value = true } } })
  graph.bindKey('ctrl+v', () => handlePaste())
  graph.bindKey('ctrl+a', () => graph?.getCells().filter(c => c.isNode()).forEach(c => graph?.select(c.id)))

  // 事件
  graph.on('scale', ({ sx }) => { zoomLevel.value = sx })
  graph.on('history:change', () => { canUndo.value = graph?.canUndo() ?? false; canRedo.value = graph?.canRedo() ?? false })

  // 悬停显示连接手柄
  graph.on('node:mouseenter', ({ node }) => showPorts(node))
  graph.on('node:mouseleave', ({ node }) => hidePorts(node))

  graph.on('selection:changed', () => {
    if (!graph) return
    const cells = graph.getSelectedCells()
    selectedCount.value = cells.length
    selectedEdgeData.value = null
    selectedNodeData.value = null
    selectedCell = null
    edgeSourceTagDefs.value = []
    if (cells.length !== 1 || !cells[0].isEdge()) {
      hideEndpointHandles()
    }
    if (cells.length === 1) {
      const cell = cells[0]
      if (cell.isNode()) { selectedCell = cell; selectedNodeData.value = { ...cell.getData() } }
      else if (cell.isEdge()) {
        selectedCell = cell
        const ls = cell.getLabels()
        const srcNode = cell.getSourceCell()
        if (srcNode && srcNode.isNode()) {
          edgeSourceTagDefs.value = srcNode.getData()?.tagDefs || []
        }
        selectedEdgeData.value = { label: ls?.[0]?.attrs?.label?.text || '' }
        const router = cell.getRouter()
        const connector = cell.getConnector()
        if ((router?.name === 'orth' || router?.name === 'manhattan') && connector?.name === 'rounded') selectedEdgeStyle.value = 'polyline'
        else if (router?.name === 'normal' && connector?.name === 'smooth') selectedEdgeStyle.value = 'curve'
        else selectedEdgeStyle.value = 'straight'
      }
    }
  })

  let lastClickTime = 0
  let lastClickNode: any = null
  graph.on('node:click', ({ node }) => {
    const now = Date.now()
    if (lastClickNode === node && now - lastClickTime < 350) {
      // 双击检测：选中节点 + 弹出绑定弹窗
      const data = node.getData()
      if (data && canBindComponent(data.nodeType)) {
        selectedCell = node
        selectedNodeData.value = { ...data }
        openBindDialog()
      }
    }
    lastClickTime = now
    lastClickNode = node
    graph?.cleanSelection(); graph?.select(node.id)
  })

  graph.on('edge:click', ({ edge }) => {
    graph?.cleanSelection(); graph?.select(edge.id)
    updateEndpointHandles(edge)
  })

  graph.on('edge:dblclick', ({ edge, e }) => {
    triggerEdgeLabelEdit(edge, e)
  })

  graph.on('blank:click', () => {
    graph?.cleanSelection(); selectedCell = null; selectedNodeData.value = null; selectedEdgeData.value = null
    hideEndpointHandles()
  })

  // 网格吸附
  graph.on('node:moving', ({ node }) => {
    if (!gridSnapEnabled.value) return
    const p = node.position()
    const gs = 20
    node.position(Math.round(p.x / gs) * gs, Math.round(p.y / gs) * gs)
  })

  // 节点 data 变化 → 更新视觉
  graph.on('node:change:data', ({ node }) => {
    if (node.isNode()) updateNodeVisual(node as Node)
  })

  // 统计节点/连线数
  graph.on('cell:added', updateStats)
  graph.on('cell:removed', ({ cell }) => {
    updateStats()
    if (selectedCell === cell) {
      selectedCell = null
      hideEndpointHandles()
    }
  })

  // 连线落到节点内部时，自动吸附到最近端口
  graph.on('edge:connected', ({ edge }: { edge: Edge }) => {
    if (edge.getTargetPortId()) return
    const node = edge.getTargetCell()
    if (!node || !node.isNode()) return
    const ports = node.getPorts()
    if (!ports || ports.length === 0) return
    const pt = edge.getTargetPoint()
    const bbox = (node as Node).getBBox()
    let min = Infinity, best: string | null = null
    ports.forEach(p => {
      const id = p.id!
      const a = (node as Node).getPortProp(id, 'args') as any
      if (!a) return
      const px = bbox.x + bbox.width * (parseFloat(String(a.x).replace('%', '')) / 100)
      const py = bbox.y + bbox.height * (parseFloat(String(a.y).replace('%', '')) / 100)
      const d = Math.sqrt((pt.x - px) ** 2 + (pt.y - py) ** 2)
      if (d < min) { min = d; best = id }
    })
    if (best) edge.setTarget({ cell: node.id, port: best })
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

function triggerEdgeLabelEdit(edge: Edge, e?: any) {
  const view = graph!.findViewByCell(edge)
  if (view) {
    let x: number, y: number
    if (e) {
      x = e.clientX
      y = e.clientY
    } else {
      const bbox = view.getBBox()
      const center = graph!.localToClient({ x: bbox.x + bbox.width / 2, y: bbox.y + bbox.height / 2 })
      x = center.x
      y = center.y
    }
    const labelText = String(edge.getLabels()?.[0]?.attrs?.label?.text || '')
    const srcNode = edge.getSourceCell()
    const tagDefs = (srcNode && srcNode.isNode()) ? (srcNode.getData()?.tagDefs || []) : []
    showInlineEditor(x + 10, y + 10, labelText, edge, true, tagDefs)
  }
}

function updateStats() {
  if (!graph) return
  nodeCount.value = graph.getNodes().length
  edgeCount.value = graph.getEdges().length
}

// ====== 拖拽 ======
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
  const shape = getShapeForType(type)
  const pos = graph.clientToLocal(event.clientX, event.clientY)
  const sizes: Record<string, [number, number]> = { start: [148, 40], task: [160, 46], condition: [100, 80], multicondition: [120, 80], loader: [160, 46], parser: [160, 46], script: [160, 46], subchain: [160, 46], iterator: [160, 46], end: [148, 40] }
  const [w, h] = sizes[type] || [160, 46]
  const node = graph.addNode({
    shape,
    x: pos.x - w / 2,
    y: pos.y - h / 2,
    width: w,
    height: h,
    data: { label, nodeType: type, description: '', preComponents: [], postComponents: [], paramResolvers: [], paramValidatorId: '', paramValidatorName: '', executeStrategy: 'NORMAL', script: '', subChainCode: '', iteratorDataSource: '', iteratorItemName: 'item' },
  })
  updateNodeVisual(node)
}

// ====== 数据变更 ======
function onDataChange() {
  if (selectedCell && selectedNodeData.value) {
    selectedCell.setData({ ...selectedNodeData.value })
  }
}

function onEdgeLabelChange(val: string) {
  if (selectedCell && selectedEdgeData.value) {
    setEdgeLabelSafe(selectedCell, val || '')
  }
}

function onEdgeStyleChange(style: 'straight' | 'polyline' | 'curve') {
  if (!selectedCell || !selectedCell.isEdge()) return
  const edge = selectedCell as Edge
  const isPolyline = style === 'polyline'
  edge.setRouter(isPolyline ? { name: 'manhattan', args: { padding: { top: 15, bottom: 15, left: 15, right: 15 }, step: 10 } } : { name: 'normal' })
  edge.setConnector(style === 'straight' ? 'normal' : isPolyline ? 'rounded' : 'smooth')
}

function onDefaultEdgeStyleChange(style: 'straight' | 'polyline' | 'curve') {
  if (!graph) return
  const isPolyline = style === 'polyline'
  const router = isPolyline ? { name: 'manhattan', args: { padding: { top: 15, bottom: 15, left: 15, right: 15 }, step: 10 } } : { name: 'normal' }
  const connector = { name: style === 'straight' ? 'normal' : isPolyline ? 'rounded' : 'smooth' }
  graph.options.connecting.router = router
  graph.options.connecting.connector = connector
  ;(graph.options as any).defaultEdge.router = router as any
  ;(graph.options as any).defaultEdge.connector = connector as any
  // manhattan 路由器自带方向计算，无需额外 sourceAnchor
  // 曲线/直线从端口位置出发，也不需要 sourceAnchor
  ;(graph.options.connecting as any).sourceAnchor = undefined
  if (selectedCell?.isEdge()) {
    onEdgeStyleChange(style)
    selectedEdgeStyle.value = style
  }
}

/** 获取画布中选中的节点列表 */
function selectedNodes(): Node[] {
  return graph?.getSelectedCells().filter(c => c.isNode()) as Node[] || []
}

/** 对齐 */
function onAlign(dir: string) {
  ;(graph as any).alignCells(selectedNodes(), dir as any)
}

/** 均匀分布 */
function onDistribute(dir: string) {
  const nodes = selectedNodes()
  if (nodes.length < 3) return
  const sorted = dir === 'horizontal'
      ? [...nodes].sort((a, b) => a.position().x - b.position().x)
      : [...nodes].sort((a, b) => a.position().y - b.position().y)
  const first = sorted[0].position()
  const last = sorted[sorted.length - 1].position()
  const span = dir === 'horizontal' ? last.x - first.x : last.y - first.y
  const gap = span / (sorted.length - 1)
  sorted.forEach((n, i) => {
    const pos = n.position()
    if (dir === 'horizontal') n.position(first.x + gap * i, pos.y)
    else n.position(pos.x, first.y + gap * i)
  })
}

/** 网格吸附开关 */
function toggleSnap() {
  gridSnapEnabled.value = !gridSnapEnabled.value
  if (!graph) return
  if (gridSnapEnabled.value) {
    ;(graph as any).drawGrid({ visible: true, size: 20, type: 'doubleMesh', args: [{ color: '#e2e8f0', thickness: 1 }, { color: '#cbd5e1', thickness: 1 }] })
  } else {
    ;(graph as any).drawGrid({ visible: true, size: 20, type: 'dot', args: [{ color: '#e2e8f0', thickness: 1 }] })
  }
}

/** 全屏 */
function toggleFullscreen() {
  if (!document.fullscreenElement) {
    document.documentElement.requestFullscreen()
  } else {
    document.exitFullscreen()
  }
}

/** 清空画布 */
function clearCanvas() {
  if (!graph) return
  ElMessageBox.confirm(t('design.clearConfirm'), t('design.clearTitle'), { confirmButtonText: t('design.confirmClear'), cancelButtonText: t('common.cancel'), type: 'warning' }).then(() => {
    graph?.clearCells()
  }).catch(() => {})
}

/** 手型拖拽模式切换 */
function togglePanMode() {
  if (!graph) return
  panModeEnabled.value = !panModeEnabled.value
  if (panModeEnabled.value) {
    // 左键拖拽平移画布，禁用选择
    graph.options.panning.eventTypes = ['leftMouseDown']
    graph.disableSelection()
    graph.getSelectedCells().forEach(c => graph?.unselect(c.id))
    if (canvasContainerRef.value) canvasContainerRef.value.classList.add('pan-mode')
  } else {
    graph.options.panning.eventTypes = ['rightMouseDown']
    graph.enableSelection()
    if (canvasContainerRef.value) canvasContainerRef.value.classList.remove('pan-mode')
  }
}

// ====== 连线端点拖拽 ======
function updateEndpointHandles(edge: Edge) {
  if (!graph || !canvasContainerRef.value) return
  const box = canvasContainerRef.value.getBoundingClientRect()
  const src = graph.localToClient(edge.getSourcePoint())
  const tgt = graph.localToClient(edge.getTargetPoint())
  endpointHandles.value = [
    { side: 'source', x: src.x - box.left - 8, y: src.y - box.top - 8 },
    { side: 'target', x: tgt.x - box.left - 8, y: tgt.y - box.top - 8 },
  ]
}
function hideEndpointHandles() { endpointHandles.value = [] }

function onEpDragStart(e: MouseEvent, side: 'source' | 'target') {
  if (!selectedCell || !selectedCell.isEdge()) return
  draggingEp = { side }
  const handler = (ev: MouseEvent) => onEpDragMove(ev, selectedCell as Edge)
  const cleanup = () => { document.removeEventListener('mousemove', handler); document.removeEventListener('mouseup', cleanup); draggingEp = null }
  document.addEventListener('mousemove', handler)
  document.addEventListener('mouseup', cleanup)
}

function onEpDragMove(e: MouseEvent, edge: Edge) {
  if (!graph || !draggingEp || !canvasContainerRef.value) return
  const box = canvasContainerRef.value.getBoundingClientRect()
  const clientPt = { x: e.clientX, y: e.clientY }
  const localPt = graph.clientToLocal(clientPt)
  const cell = draggingEp.side === 'source' ? edge.getSourceCell() : edge.getTargetCell()
  if (!cell) return
  const center = cell.getBBox().center
  const angle = Math.atan2(localPt.y - center.y, localPt.x - center.x) * (180 / Math.PI)
  if (draggingEp.side === 'source') {
    edge.setSource({ ...edge.getSource(), connectionPoint: { name: 'boundary', args: { angle } } })
  } else {
    edge.setTarget({ ...edge.getTarget(), connectionPoint: { name: 'boundary', args: { angle } } })
  }
  updateEndpointHandles(edge)
}

function onLabelDragMove(e: MouseEvent, edge: Edge, path: any, totalLen: number) {
  if (!graph) return
  const p = graph.clientToLocal({ x: e.clientX, y: e.clientY })
  const steps = 100
  let minDist = Infinity, bestLen = 0
  for (let i = 0; i <= steps; i++) {
    const len = (i / steps) * totalLen
    const pt = path.pointAtLength(len, {})
    if (!pt) continue
    const d = Math.sqrt((p.x - pt.x) ** 2 + (p.y - pt.y) ** 2)
    if (d < minDist) { minDist = d; bestLen = len }
  }
  const distance = totalLen > 0 ? Math.max(0, Math.min(1, bestLen / totalLen)) : 0.5
  const labels = edge.getLabels()
  if (labels[0]) {
    labels[0].position = { distance }
    edge.setLabels(labels)
  }
}

function removeSelected() {
  if (!graph) return
  const cells = graph.getSelectedCells()
  if (cells.length > 0) { graph.removeCells(cells); selectedCell = null; selectedNodeData.value = null; selectedEdgeData.value = null; hideEndpointHandles() }
}

function handleUndo() { graph?.undo() }
function handleRedo() { graph?.redo() }

function handleCopy() {
  if (!graph) return
  const cells = graph.getSelectedCells()
  if (cells.length > 0) { graph.copy(cells); canPaste.value = true }
}

function handlePaste() {
  if (!graph || !canPaste.value) return
  const cells = graph.paste({ offset: 32 })
  if (cells && cells.length > 0) { graph.cleanSelection(); cells.forEach(c => graph?.select(c.id)) }
}

function handleCut() {
  if (!graph) return
  const cells = graph.getSelectedCells()
  if (cells.length > 0) { graph.cut(cells); canPaste.value = true; selectedCell = null; selectedNodeData.value = null; selectedEdgeData.value = null }
}

// ====== 缩放 ======
function zoomIn() { graph?.zoom(0.05) }
function zoomOut() { graph?.zoom(-0.05) }
function zoomToFit() { graph?.zoomToFit({ padding: 40, maxScale: 1 }) }
function zoomReset() { graph?.zoom(1, { absolute: true }) }

// ====== 导出 PNG ======
function handleExport() {
  if (!graph) return
  const name = design.value?.name || 'flow'
  const rect = graph.getContentBBox()
  if (!rect || rect.width === 0) return
  const svgEl = graphContainerRef.value?.querySelector<SVGSVGElement>('.x6-graph-svg')
  if (!svgEl) return
  // 克隆 → 加边距 + 白色背景 → 序列化 → 同时下载 SVG 和尝试转 PNG
  const clone = svgEl.cloneNode(true) as SVGSVGElement
  const pad = 40
  const w = Math.ceil(rect.width + pad * 2)
  const h = Math.ceil(rect.height + pad * 2)
  clone.setAttribute('width', String(w))
  clone.setAttribute('height', String(h))
  clone.setAttribute('viewBox', `${rect.x - pad} ${rect.y - pad} ${w} ${h}`)
  const bg = document.createElementNS('http://www.w3.org/2000/svg', 'rect')
  bg.setAttribute('width', '100%'); bg.setAttribute('height', '100%'); bg.setAttribute('fill', '#ffffff')
  clone.insertBefore(bg, clone.firstChild)
  const svgStr = new XMLSerializer().serializeToString(clone)
  // 先下载 SVG 保证用户有内容，同时异步尝试转 PNG
  const svgUri = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svgStr)
  const img = new Image()
  img.onload = () => {
    const canvas = document.createElement('canvas')
    canvas.width = w; canvas.height = h
    const ctx = canvas.getContext('2d')!
    ctx.fillStyle = '#ffffff'
    ctx.fillRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(img, 0, 0)
    downloadURI(canvas.toDataURL('image/png'), `${name}.png`)
  }
  img.onerror = () => { downloadURI(svgUri, `${name}.svg`) }
  img.src = svgUri
}

function downloadURI(uri: string, filename: string) {
  const link = document.createElement('a')
  link.download = filename
  link.href = uri
  link.click()
}

// ====== 图翻译 → ChainDefinitionDTO ======

/** 节点类型映射：X6 nodeType → ChainNodeDTO type */
function mapNodeType(nodeType: string): string {
  const map: Record<string, string> = {
    task: 'NORMAL',
    condition: 'CONDITION',
    multicondition: 'CONDITION',
    loader: 'NORMAL',
    parser: 'NORMAL',
    script: 'SCRIPT',
    subchain: 'SUB_CHAIN',
    iterator: 'ITERATOR',
  }
  return map[nodeType] || 'NORMAL'
}

/** 翻译 X6 图为 ChainDefinitionDTO JSON */
function translateGraphToChain(): any {
  if (!graph) return { nodes: [], edges: [] }

  // 链级元数据
  const root: Record<string, any> = {}
  if (design.value?.name) root.name = design.value.name
  if (designCode) root.code = designCode

  // 只翻译业务节点（跳过 start/end）
  root.nodes = graph.getNodes()
      .filter(n => { const t = n.getData()?.nodeType; return t && t !== 'start' && t !== 'end' })
      .map(n => {
        const data = n.getData() || {}
        const config: Record<string, any> = {}

        // 执行策略（有非默认值时才带出）
        if (data.executeStrategy && data.executeStrategy !== 'NORMAL') {
          config.executeStrategy = data.executeStrategy
        }

        const node: Record<string, any> = {
          id: n.id,
          label: data.label || '',
          type: mapNodeType(data.nodeType),
          component: data.componentId || undefined,
        }

        // 分组
        if (data.groupName) node.groupName = data.groupName

        // 绑定元件名称
        if (data.componentName) node.componentName = data.componentName

        // 执行脚本
        if (data.script) node.script = data.script

        // 子链编码
        if (data.subChainCode) node.subChainCode = data.subChainCode

        // 迭代器配置
        if (data.nodeType === 'iterator') {
          const icfg: Record<string, any> = {}
          if (data.iteratorDataSource) icfg.dataSource = data.iteratorDataSource
          if (data.iteratorItemName && data.iteratorItemName !== 'item') icfg.itemName = data.iteratorItemName
          if (Object.keys(icfg).length > 0) {
            node.config = { ...(node.config || {}), ...icfg }
          }
        }

        // 描述
        if (data.description) node.description = data.description

        // 执行策略配置
        if (Object.keys(config).length > 0) node.config = config

        // 参数解析器链
        if (data.paramResolvers?.length) {
          node.paramResolvers = data.paramResolvers.map((p: any) => ({
            componentId: p.componentId,
            componentName: p.componentName || '',
          }))
        }

        // 参数校验器
        if (data.paramValidatorId) {
          node.paramValidator = { componentId: data.paramValidatorId, componentName: data.paramValidatorName || '' }
        }

        // 前置处理器
        if (data.preComponents?.length) {
          node.preComponents = data.preComponents.map((p: any) => ({
            componentId: p.componentId,
            componentName: p.componentName || '',
          }))
        }

        // 后置处理器
        if (data.postComponents?.length) {
          node.postComponents = data.postComponents.map((p: any) => ({
            componentId: p.componentId,
            componentName: p.componentName || '',
          }))
        }

        return node
      })

  root.edges = graph.getEdges().map(e => {
    const labelText = String(e.getLabels()?.[0]?.attrs?.label?.text || '')

    return {
      source: e.getSourceCellId() || '',
      target: e.getTargetCellId() || '',
      label: labelText || undefined,
    }
  })

  return root
}

/** 校验链拓扑 */
function validateChain(): string[] {
  const errors: string[] = []
  if (!graph) return [t('design.canvasNotInit')]

  const nodes = graph.getNodes()
  const edges = graph.getEdges()

  if (nodes.length < 2) {
    errors.push(t('design.needMinNodes'))
    return errors
  }

  const startNodes = nodes.filter(n => n.getData()?.nodeType === 'start')
  const endNodes = nodes.filter(n => n.getData()?.nodeType === 'end')

  // 1. 基础 — Start/End 存在性
  if (startNodes.length === 0) errors.push(t('design.missingStart'))
  if (endNodes.length === 0) errors.push(t('design.missingEnd'))
  if (startNodes.length > 1) errors.push(t('design.multipleStart'))
  if (startNodes.length === 0 || endNodes.length === 0) return errors

  // 2. 节点名称不能为空
  nodes.forEach(n => {
    const data = n.getData()
    if (!data?.label || !data.label.trim()) {
      errors.push(t('design.nodeNameEmpty', { id: n.id }))
    }
  })

  // 3. 孤立节点（没有任何连线）
  const connectedNodeIds = new Set<string>()
  edges.forEach(e => {
    const srcId = e.getSourceCellId()
    const tgtId = e.getTargetCellId()
    if (srcId) connectedNodeIds.add(srcId)
    if (tgtId) connectedNodeIds.add(tgtId)
  })
  nodes.forEach(n => {
    if (!connectedNodeIds.has(n.id)) {
      errors.push(t('design.isolatedNode', { label: n.getData()?.label || n.id }))
    }
  })

  // 3. 路径连通性：Start → ... → End BFS
  const reachableFromStart = new Set<string>()
  const queue = startNodes.map(n => n.id)
  while (queue.length > 0) {
    const current = queue.shift()!
    if (reachableFromStart.has(current)) continue
    reachableFromStart.add(current)
    edges.forEach(e => {
      if (e.getSourceCellId() === current) {
        const tgt = e.getTargetCellId()
        if (tgt && !reachableFromStart.has(tgt)) queue.push(tgt)
      }
    })
  }
  if (!endNodes.some(en => reachableFromStart.has(en.id))) {
    errors.push(t('design.pathNotComplete'))
    return errors
  }

  // 3b. 节点自环检测
  edges.forEach(e => {
    const src = e.getSourceCellId()
    const tgt = e.getTargetCellId()
    if (src && tgt && src === tgt) {
      const node = graph!.getCellById(src)
      const label = (node as any)?.getData?.()?.label || src
      errors.push(t('design.selfLoop', { label }))
    }
  })

  // 4. 业务节点（除 start/end）未绑定元件
  const bindableTypes = new Set(['task', 'condition', 'multicondition', 'loader', 'parser'])
  nodes.forEach(n => {
    const data = n.getData() || {}
    const nodeType = data.nodeType
    if (!bindableTypes.has(nodeType)) return
    if (!data.componentId) {
      errors.push(t('design.nodeNotBound', { label: data.label || n.id, type: typeLabel(nodeType) }))
    }
  })

  // 5. 条件节点出线检查
  nodes.forEach(n => {
    const data = n.getData() || {}
    const nodeType = data.nodeType
    if (nodeType !== 'condition' && nodeType !== 'multicondition') return
    const outgoingEdges = edges.filter(e => e.getSourceCellId() === n.id)
    if (outgoingEdges.length === 0) {
      errors.push(t('design.conditionNoOutEdge', { label: data.label || n.id }))
    }
    outgoingEdges.forEach(e => {
      const labelText = String(e.getLabels()?.[0]?.attrs?.label?.text || '').trim()
      if (!labelText) {
        errors.push(t('design.conditionMissingLabel', { label: data.label || n.id }))
      }
    })
  })

  return errors
}

/** 死环检测 — 仅警告，不拦截保存 */
function detectCycleWarnings(): string[] {
  const warnings: string[] = []
  if (!graph) return warnings

  const nodes = graph.getNodes()
  const idToLabel = new Map<string, string>()
  const adj = new Map<string, string[]>()
  nodes.forEach(n => {
    const t = n.getData()?.nodeType
    if (t === 'start' || t === 'end') return
    idToLabel.set(n.id, n.getData()?.label || n.id)
    adj.set(n.id, [])
  })
  graph.getEdges().forEach(e => {
    const src = e.getSourceCellId()
    const tgt = e.getTargetCellId()
    if (src && tgt && adj.has(src) && adj.has(tgt)) {
      adj.get(src)!.push(tgt)
    }
  })

  // 三色标记 DFS
  const color = new Map<string, number>()
  const parent = new Map<string, string | null>()
  adj.forEach((_, id) => { color.set(id, 0); parent.set(id, null) })

  const found: string[] = []
  function dfs(u: string) {
    color.set(u, 1)
    for (const v of adj.get(u) || []) {
      if (color.get(v) === 1) {
        const path: string[] = [v]
        let cur: string | null = u
        while (cur && cur !== v) { path.push(cur); cur = parent.get(cur) || null }
        path.reverse()
        found.push(t('design.cycleWarning', { path: path.map(id => idToLabel.get(id) || id).join(' → '), start: path[0] }))
      } else if (color.get(v) === 0) {
        parent.set(v, u)
        dfs(v)
      }
    }
    color.set(u, 2)
  }
  adj.forEach((_, id) => { if (color.get(id) === 0) dfs(id) })
  return found
}

/** 显示链数据预览弹窗 */
function showChainDataDialog() {
  if (!graph) {
    ElMessage.warning(t('design.canvasNotInit'))
    return
  }

  const validationErrors = validateChain()
  const cycleWarnings = detectCycleWarnings()
  const allErrors = [...validationErrors, ...cycleWarnings]

  const chain = translateGraphToChain()
  chainDataDialog.json = JSON.stringify(chain, null, 2)
  chainDataDialog.errors = allErrors
  chainDataDialog.success = allErrors.length === 0
  chainDataDialog.visible = true
}

// ====== 加载设计 ======
async function loadDesign() {
  try {
    design.value = await designApi.getByCode(designCode, appCode)
    // 获取应用名称
    if (appCode) {
      try {
        const apps = await executorApi.listApps()
        const mod = apps.find((a: any) => a.appCode === appCode)
        if (mod) appName.value = mod.appName || mod.appCode
      } catch { /* ignore */ }
    }
    if (!graph) return
    if (design.value.graphData) {
      // graphData 可能是字符串或已解析对象
      let data = design.value.graphData
      if (typeof data === 'string') data = JSON.parse(data)
      if (data?.cells?.length > 0) {
        // 兼容旧版 flow-node shape → 新版专用形状
        data.cells.forEach((cell: any) => {
          if (cell.shape === 'flow-node' && cell.data?.nodeType) {
            cell.shape = getShapeForType(cell.data.nodeType)
          }
          // 清除旧的 vue-shape-view 引用
          if (cell.view === 'vue-shape-view') delete cell.view
        })
        graph.fromJSON(data)
        // 确保所有节点视觉和端口正确
        graph.getNodes().forEach(n => {
          updateNodeVisual(n)
          // 序列化可能丢失端口 group 定义，重新注入完整端口配置
          const nodeType = n.getData()?.nodeType || 'task'
          n.setProp('ports', { groups: { handle: handleGroup }, items: getPorts(nodeType) })
        })
        normalizeLoadedEdges()
        graph.zoomToFit({ padding: 60, maxScale: 1 })
        return
      }
    }
    // 空设计
    const start = graph.addNode({ shape: 'flow-start', x: 250, y: 40, width: 148, height: 40, data: { label: t('design.startNode'), nodeType: 'start' } })
    const end = graph.addNode({ shape: 'flow-end', x: 250, y: 380, width: 148, height: 40, data: { label: t('design.endNode'), nodeType: 'end' } })
    updateNodeVisual(start)
    updateNodeVisual(end)
    graph.centerContent()
  } catch (e) {
    console.error(e)
    ElMessage.error(t('design.loadFailed'))
    router.push('/design')
  }
}

// ====== 保存 ======
async function handleSave() {
  if (!graph) return

  // 1. 拓扑校验（阻断）
  const validationErrors = validateChain()
  if (validationErrors.length > 0) {
    ElMessage.warning(t('design.validationFailed', { errors: validationErrors.join('\n') }))
    return
  }

  // 2. 死环警告（不阻断）
  const cycleWarnings = detectCycleWarnings()
  if (cycleWarnings.length > 0) {
    try {
      await ElMessageBox.confirm(
          t('design.cycleConfirm', { warnings: cycleWarnings.join('\n') }),
          t('common.confirm'),
          { confirmButtonText: t('design.saveGraph'), cancelButtonText: t('common.cancel'), type: 'warning' }
      )
    } catch { return }
  }

  // 3. 已发布链的确认弹窗
  const boundChains = (design.value as any)?.boundChains
  if (boundChains && boundChains.some((c: any) => c.status === 3 || c.status === 4)) {
    try {
      await ElMessageBox.confirm(
          t('chains.designModifyConfirm'),
          t('common.confirm'),
          { confirmButtonText: t('design.saveGraph'), cancelButtonText: t('common.cancel'), type: 'warning' }
      )
    } catch { return }
  }

  saving.value = true
  try {
    const json = graph.toJSON()
    const chain = translateGraphToChain()
    await designApi.saveGraph(designCode, appCode, JSON.stringify(json), JSON.stringify(chain))
    ElMessage.success(t('design.saveGraphSuccess'))
  } catch { ElMessage.error(t('design.saveFailed')) }
  finally { saving.value = false }
}

function goBack() { router.push('/design') }

// ====== 生命周期 ======
onMounted(async () => {
  registerShapes()
  await nextTick()
  initGraph()
  await loadDesign()
})

onBeforeUnmount(() => {
  closeContextMenu()
  inlineEditor.show = false
  document.removeEventListener('click', inlineEditorOutsideClick)
  resizeObserver?.disconnect()
  graph?.dispose()
  graph = null
  draggingEp = null
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
  padding: 4px 12px;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
  flex-shrink: 0;
}

.toolbar-left { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.toolbar-title { font-size: 15px; }
.app-prefix { font-size: 13px; margin-right: 2px; }
.toolbar-center { display: flex; align-items: center; gap: 0; flex: 1; min-width: 0; overflow: hidden; }
.toolbar-right { display: flex; align-items: center; gap: 4px; flex-shrink: 0; }
.toolbar-divider { width: 1px; height: 16px; background: #cbd5e1; margin: 0 4px; flex-shrink: 0; }
.zoom-label { font-size: 12px; color: #475569; min-width: 36px; text-align: center; font-variant-numeric: tabular-nums; }

.stat-badge {
  font-size: 11px;
  color: #94a3b8;
  background: #f1f5f9;
  padding: 2px 10px;
  border-radius: 10px;
  white-space: nowrap;
}

.editor-body { display: flex; flex: 1; overflow: hidden; position: relative; }

.node-palette { width: 180px; border-right: 1px solid #e2e8f0; background: #f8fafc; flex-shrink: 0; overflow-y: auto; }
.palette-header { padding: 12px 16px; font-weight: 600; font-size: 13px; color: #475569; border-bottom: 1px solid #e2e8f0; }
.palette-list { padding: 8px; }

.palette-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  background: #fff; border-radius: 6px; margin-bottom: 6px; cursor: grab;
  box-shadow: 0 1px 2px rgba(0,0,0,0.04); transition: box-shadow 0.15s, transform 0.12s;
  user-select: none;
}
.palette-item:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.08); transform: translateY(-1px); }
.palette-item:active { cursor: grabbing; transform: translateY(0); }

.palette-icon {
  width: 24px; height: 24px; border-radius: 5px;
  display: flex; align-items: center; justify-content: center;
  color: #fff; flex-shrink: 0;
}
.palette-icon :deep(svg) { width: 14px; height: 14px; display: block; }
.palette-label { font-size: 12px; color: #0f172a; font-weight: 500; }

.canvas-area { flex: 1; position: relative; overflow: hidden; background: #fafbfc; }
.canvas-area.pan-mode { cursor: grab; }
.canvas-area.pan-mode:active { cursor: grabbing; }
.graph-container { width: 100%; height: 100%; }

.minimap-container {
  position: absolute; bottom: 12px; right: 12px;
  border: 1px solid #e2e8f0; border-radius: 6px; background: #fff;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08); z-index: 10; overflow: hidden;
}

.property-panel { width: 260px; border-left: 1px solid #e2e8f0; background: #f8fafc; flex-shrink: 0; overflow-y: auto; }
.panel-header { padding: 12px 16px; font-weight: 600; font-size: 13px; color: #475569; border-bottom: 1px solid #e2e8f0; display: flex; align-items: center; }
.panel-body { padding: 16px; }
.panel-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 16px; color: #94a3b8; font-size: 13px; }
</style>

<style>
/* 右键菜单 */
.x6-context-menu {
  position: fixed; z-index: 9999; min-width: 150px;
  background: #fff; border-radius: 8px; box-shadow: 0 8px 30px rgba(0,0,0,0.15);
  padding: 4px; border: 1px solid #e2e8f0; user-select: none;
}
.x6-context-menu .context-item {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px;
  font-size: 13px; color: #0f172a; border-radius: 4px; cursor: pointer; transition: background 0.1s;
}
.x6-context-menu .context-item:hover { background: #f1f5f9; }
.x6-context-menu .context-item.danger { color: #ef4444; }
.x6-context-menu .context-item.danger:hover { background: #fef2f2; }
.x6-context-menu .context-item .el-icon { font-size: 15px; }
.x6-context-menu .context-separator { height: 1px; background: #e2e8f0; margin: 4px 8px; }

/* 行内编辑器 */
.inline-editor-overlay {
  position: fixed; z-index: 10000; width: 180px;
}
.inline-editor-overlay .el-input__wrapper { background: #fff; box-shadow: 0 4px 16px rgba(0,0,0,0.15); border: 2px solid #3b82f6; border-radius: 6px; }
.inline-editor-overlay .el-input__inner { font-size: 13px; }

/* 连线端点拖拽手柄 */
.ep-handle {
  position: absolute; width: 16px; height: 16px; border-radius: 50%;
  background: #fff; border: 2px solid #3b82f6; cursor: grab;
  z-index: 100; box-shadow: 0 1px 4px rgba(0,0,0,0.2);
  pointer-events: all;
}
.ep-handle:active { cursor: grabbing; }
</style>
