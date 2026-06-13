<template>
    <div class="design-editor-x6">
    <ExecutorReadCacheAlert :stale="readCacheStale" />
    <!-- 顶部工具栏 -->
    <div class="editor-toolbar" :class="{ 'editor-toolbar--mobile': isMobileView }">
      <div class="toolbar-left">
        <el-button text @click="goBack">
          <el-icon><ArrowLeft /></el-icon> {{ $t('design.back') }}
        </el-button>
        <span v-if="appName" class="app-prefix">{{ appName }}</span>
        <span class="toolbar-title">{{ design?.name }}</span>
        <el-tag v-if="design" :type="enableStatusTagType(design.status)" size="small">
          {{ enableStatusLabel(design.status) }}
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
          <el-option
            v-for="item in designLineTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
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
        <el-tooltip :content="copilotEnabled ? $t('ai.aiAssistant') : $t('ai.notConfigured')">
          <el-button
            class="toolbar-ai-btn"
            :disabled="!copilotEnabled"
            @click="showCopilot = true"
          >
            <el-icon><MagicStick /></el-icon> {{ $t('ai.aiAssistant') }}
          </el-button>
        </el-tooltip>
        <el-tooltip :content="$t('ai.aiDetectHint')">
          <el-button
            class="toolbar-ai-detect-btn"
            :loading="aiDetecting"
            @click="handleAiDetect"
          >
            <el-icon><CircleCheck /></el-icon> {{ $t('ai.aiDetect') }}
          </el-button>
        </el-tooltip>
        <el-button class="toolbar-save-btn" type="primary" :loading="saving" @click="handleSave">
          <el-icon><Check /></el-icon> {{ $t('design.saveGraph') }}
        </el-button>
      </div>
    </div>

    <!-- 编辑器主体 -->
    <div
      class="editor-body"
      :class="{ 'editor-body--compact': isCompactEditor, 'editor-body--tablet': isTabletEditor }"
      @contextmenu.prevent="onCanvasContextMenu"
    >
      <!-- 左侧节点面板（桌面/平板常驻；手机为底部抽屉） -->
      <div
        class="node-palette"
        :class="{
          'node-palette--compact': isCompactEditor,
          'node-palette--tablet': isTabletEditor,
          'node-palette--sheet-open': isCompactEditor && nodeSheetOpen,
          'node-palette--touch': isTouchPalette,
        }"
      >
        <div class="palette-header">
          <span>{{ $t('design.nodes') }}</span>
          <el-button v-if="isCompactEditor" text size="small" @click="nodeSheetOpen = false">
            <el-icon><Close /></el-icon>
          </el-button>
        </div>
        <div class="palette-list">
          <div v-for="group in paletteGroups" :key="group.category" class="palette-group">
            <div class="palette-group-title">{{ paletteCategoryLabel(group.category) }}</div>
            <div
              v-for="nt in group.nodes"
              :key="nt.type"
              class="palette-item"
              :class="{ 'palette-item--pending': pendingPlaceNode?.type === nt.type }"
              :draggable="!isTouchPalette"
              @dragstart="onDragStart($event, nt)"
              @click="!isTouchPalette && onPaletteItemTap(nt)"
              @touchend.prevent="isTouchPalette && onPaletteItemTouchEnd($event, nt)"
            >
              <div class="palette-icon" :style="{ background: nt.color }" v-html="nt.icon" />
              <div class="palette-label">{{ typeLabel(nt.type) }}</div>
              <span v-if="isTouchPalette" class="palette-tap-hint">{{ $t('design.tapToAdd') }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 中间画布 -->
      <div
        class="canvas-area"
        :class="{ 'canvas-area--placing': !!pendingPlaceNode, 'canvas-area--touch': isOverlayEditor }"
        ref="canvasContainerRef"
        @dragover.prevent="onDragOver"
        @drop.prevent="onDrop"
      >
        <div ref="graphContainerRef" class="graph-container" />
        <div v-if="!isOverlayEditor" ref="minimapContainerRef" class="minimap-container" />
        <!-- 连线端点拖拽手柄 -->
        <div
            v-for="ep in endpointHandles" :key="ep.side"
            class="ep-handle"
            :style="{ left: ep.x + 'px', top: ep.y + 'px' }"
            @mousedown.prevent="onEpDragStart($event, ep.side)"
        />
        <!-- 触摸端放置提示 -->
        <div v-if="pendingPlaceNode" class="place-node-banner">
          <span>{{ $t('design.tapCanvasToPlace', { label: typeLabel(pendingPlaceNode.type) }) }}</span>
          <el-button size="small" text @click="cancelPlaceMode">{{ $t('design.cancelPlace') }}</el-button>
        </div>
        <!-- 触摸端选中操作条 -->
        <div
          v-if="isOverlayEditor && selectedCount > 0 && !pendingPlaceNode"
          class="selection-action-bar"
        >
          <span class="selection-action-bar__count">{{ $t('design.selected') }} {{ selectedCount }}</span>
          <el-button type="danger" round size="small" @click="handleDeleteSelected">
            <el-icon><Delete /></el-icon>
            {{ deleteSelectionLabel }}
          </el-button>
          <el-button round size="small" @click="clearGraphSelection">
            {{ $t('design.deselect') }}
          </el-button>
        </div>
        <!-- 触摸端浮动操作（手机：节点+属性） -->
        <div v-if="isCompactEditor" class="canvas-fab-bar">
          <el-button
            round
            type="primary"
            :class="{ 'fab-btn--active': nodeSheetOpen }"
            @click="toggleNodeSheet"
          >
            <el-icon><Plus /></el-icon>
            {{ $t('design.addNode') }}
          </el-button>
          <el-button
            round
            :type="propertySheetOpen ? 'primary' : 'default'"
            @click="togglePropertySheet"
          >
            <el-icon><Setting /></el-icon>
            {{ $t('design.properties') }}
          </el-button>
        </div>
      </div>

      <button
        v-if="!isPropertyOverlay && propertyPanelCollapsed"
        type="button"
        class="property-panel-expand"
        :title="$t('design.expandProperties')"
        @click="togglePropertyPanelCollapse"
      >
        <el-icon><DArrowLeft /></el-icon>
        <span>{{ $t('design.properties') }}</span>
      </button>

      <!-- 属性面板（桌面/平板内联可收起；手机底部抽屉） -->
      <div
        class="property-panel"
        :class="{
          'property-panel--overlay': isPropertyOverlay,
          'property-panel--sheet-open': isPropertyOverlay && propertySheetOpen,
          'property-panel--collapsed': !isPropertyOverlay && propertyPanelCollapsed,
        }"
      >
        <div class="panel-header">
          <span style="display:flex;align-items:center;gap:6px;flex-wrap:wrap;flex:1;min-width:0">
            {{ selectedEdgeData ? $t('design.selectedEdge') : selectedNodeData ? $t('design.selectedNode') : $t('design.properties') }}
            <el-tag v-if="selectedNodeData" size="small" :color="nodeColor(selectedNodeData.nodeType)" style="color:#fff;border:none;margin-left:6px">
              {{ typeLabel(selectedNodeData.nodeType) }}
            </el-tag>
            <el-button v-if="selectedNodeData && canBindMainComponent(selectedNodeData)" size="small" type="primary" plain @click="openBindDialog" style="margin-left:4px">
              {{ $t('design.bindComponent') }}
            </el-button>
          </span>
          <el-button v-if="isPropertyOverlay" text size="small" @click="propertySheetOpen = false">
            <el-icon><Close /></el-icon>
          </el-button>
          <el-button
            v-else
            text
            size="small"
            :title="propertyPanelCollapsed ? $t('design.expandProperties') : $t('design.collapseProperties')"
            @click="togglePropertyPanelCollapse"
          >
            <el-icon><DArrowRight v-if="!propertyPanelCollapsed" /><DArrowLeft v-else /></el-icon>
          </el-button>
        </div>
        <!-- 节点属性 -->
        <div v-if="selectedNodeData" class="panel-body">
          <el-form size="small" label-position="top">
            <!-- 判断元件：脚本 / 绑定双模式 -->
            <template v-if="normalizeNodeType(selectedNodeData.nodeType) === 'CONDITION'">
              <el-form-item :label="$t('design.predicateMode')">
                <el-radio-group v-model="selectedNodeData.predicateMode" @change="onPredicateModeChange">
                  <el-radio v-for="item in predicateModeOptions" :key="item.value" :value="item.value">
                    {{ item.label }}
                  </el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item :label="$t('design.componentId')">
                <el-input
                    v-if="selectedNodeData.predicateMode === 'script'"
                    v-model="selectedNodeData.componentId"
                    :placeholder="$t('design.inlinePredIdPlaceholder')"
                    style="font-family:monospace"
                    @input="onDataChange"
                />
                <template v-else>
                  <el-link v-if="selectedNodeData.componentId" type="primary" :underline="'never'" style="font-family:monospace;cursor:pointer" @click="openCompDetail(selectedNodeData)">
                    {{ selectedNodeData.componentId }}
                  </el-link>
                  <span v-else style="color:#bbb;font-size:12px">{{ $t('design.autoFill') }}</span>
                </template>
              </el-form-item>
              <el-form-item :label="$t('design.componentName')">
                <el-input
                    v-if="selectedNodeData.predicateMode === 'script'"
                    v-model="selectedNodeData.componentName"
                    :placeholder="$t('design.inlinePredNamePlaceholder')"
                    @input="onDataChange"
                />
                <el-input v-else :model-value="selectedNodeData.componentName || ''" disabled :placeholder="$t('design.autoFill')" />
              </el-form-item>
              <el-form-item v-if="selectedNodeData.predicateMode === 'bind'">
                <el-button size="small" type="primary" plain style="width:100%" @click="openBindDialog">
                  {{ $t('design.bindComponent') }}
                </el-button>
              </el-form-item>
              <template v-if="selectedNodeData.predicateMode === 'script'">
                <el-form-item :label="$t('design.trueBranch')">
                  <el-input v-model="selectedNodeData.trueLabel" placeholder="True" @change="syncConditionBranchLabels" />
                </el-form-item>
                <el-form-item :label="$t('design.falseBranch')">
                  <el-input v-model="selectedNodeData.falseLabel" placeholder="False" @change="syncConditionBranchLabels" />
                </el-form-item>
                <el-form-item>
                  <template #label>
                    <span>{{ $t('design.predicateScript') }}</span>
                    <AiExpressionAssist
                      :model-value="selectedNodeData.predicateScript || ''"
                      :disabled="!copilotEnabled"
                      :get-context="getCopilotContext"
                      :field-label="$t('design.predicateScript')"
                      @update:model-value="onPredicateScriptAiApply"
                    />
                  </template>
                  <el-input
                      v-model="selectedNodeData.predicateScript"
                      type="textarea"
                      :rows="4"
                      :placeholder="$t('design.predicateScriptPlaceholder')"
                      @input="onDataChange"
                  />
                </el-form-item>
                <div style="font-size:11px;color:#909399;line-height:1.5;margin-bottom:8px">{{ $t('design.predicateScriptHint') }}</div>
              </template>
            </template>
            <el-form-item v-if="showsStandardBindPanel(selectedNodeData.nodeType)" :label="$t('design.componentId')">
              <el-link v-if="selectedNodeData.componentId" type="primary" :underline="'never'" style="font-family:monospace;cursor:pointer" @click="openCompDetail(selectedNodeData)">
                {{ selectedNodeData.componentId }}
              </el-link>
              <span v-else style="color:#bbb;font-size:12px">{{ $t('design.autoFill') }}</span>
            </el-form-item>
            <el-form-item v-if="showsStandardBindPanel(selectedNodeData.nodeType)" :label="$t('design.componentName')">
              <el-input :model-value="selectedNodeData.componentName || ''" disabled :placeholder="$t('design.autoFill')" />
            </el-form-item>
            <el-form-item v-if="showsStandardBindPanel(selectedNodeData.nodeType)" :label="$t('design.executeStrategy')">
              <el-select v-model="selectedNodeData.executeStrategy" style="width:100%" @change="onDataChange">
                <el-option v-for="item in executeStrategyOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="showsStandardBindPanel(selectedNodeData.nodeType)" :label="$t('design.transactionPropagation')">
              <el-select v-model="selectedNodeData.transactionPropagation" style="width:100%" @change="onDataChange">
                <el-option v-for="item in transactionPropagationOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <div style="font-size:11px;color:#909399;line-height:1.5;margin-top:4px">{{ $t('design.transactionPropagationHint') }}</div>
            </el-form-item>
            <!-- 参数解析器链 -->
            <div v-if="showsStandardBindPanel(selectedNodeData.nodeType)" style="padding:0 0 8px 0;width:100%">
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
            <div v-if="showsStandardBindPanel(selectedNodeData.nodeType)" style="padding:0 0 4px 0;width:100%">
              <div style="font-size:12px;color:#606266;margin-bottom:4px">{{ $t('components.typeParamValidator') }}</div>
              <el-tag v-if="selectedNodeData.paramValidatorName" type="info" size="small" closable style="margin-bottom:4px" @close="clearValidator()">
                {{ selectedNodeData.paramValidatorName }}
              </el-tag>
              <el-button size="small" type="primary" plain @click="openBindDialog('validator')" style="width:100%">
                {{ $t('design.bindComponent') }}
              </el-button>
            </div>
            <!-- 前置处理器 -->
            <div v-if="showsStandardBindPanel(selectedNodeData.nodeType)" style="padding:0 0 8px 0;width:100%">
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
            <div v-if="showsStandardBindPanel(selectedNodeData.nodeType)" style="padding:0 0 8px 0;width:100%">
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
            <!-- 类型专属 config（对标 Camunda/n8n 字段配置） -->
            <template v-for="field in selectedNodeTypeFields" :key="field.key">
              <el-form-item :label="$t(`design.nodeFields.${field.i18nKey}`)">
                <el-select
                    v-if="field.input === 'select'"
                    v-model="selectedNodeData[field.key]"
                    style="width:100%"
                    @change="onDataChange"
                >
                  <el-option
                      v-for="opt in field.options || []"
                      :key="opt.value"
                      :label="$t(`design.nodeFields.${opt.labelKey}`)"
                      :value="opt.value"
                  />
                </el-select>
                <el-input-number
                    v-else-if="field.input === 'number'"
                    v-model="selectedNodeData[field.key]"
                    :min="0"
                    controls-position="right"
                    style="width:100%"
                    @change="onDataChange"
                />
                <el-input
                    v-else-if="field.input === 'textarea'"
                    v-model="selectedNodeData[field.key]"
                    type="textarea"
                    :rows="3"
                    :placeholder="field.placeholder"
                    @input="onDataChange"
                />
                <el-input
                    v-else
                    v-model="selectedNodeData[field.key]"
                    :placeholder="field.placeholder"
                    @input="onDataChange"
                />
              </el-form-item>
            </template>
            <el-form-item v-if="hasScriptField(selectedNodeData.nodeType)">
              <template #label>
                <span>{{ $t('design.script') }}</span>
                <AiExpressionAssist
                  :model-value="selectedNodeData.script || ''"
                  :disabled="!copilotEnabled"
                  :get-context="getCopilotContext"
                  :field-label="$t('design.script')"
                  @update:model-value="onNodeScriptAiApply"
                />
              </template>
              <el-input v-model="selectedNodeData.script" type="textarea" :rows="3" :placeholder="$t('design.scriptPlaceholder')" @input="onDataChange" />
            </el-form-item>
            <el-form-item v-if="hasDescription(selectedNodeData.nodeType)" :label="$t('design.description')">
              <el-input v-model="selectedNodeData.description" type="textarea" :rows="3" @input="onDataChange" />
            </el-form-item>
            <!-- 子链节点：子链编码 -->
            <el-form-item v-if="normalizeNodeType(selectedNodeData.nodeType) === 'SUB_CHAIN'" :label="$t('design.subChainCode')">
              <el-input v-model="selectedNodeData.subChainCode" :placeholder="$t('design.selectChain')" @input="onDataChange" />
            </el-form-item>
            <!-- 迭代器节点：数据源 + 迭代项名 -->
            <template v-if="normalizeNodeType(selectedNodeData.nodeType) === 'ITERATOR'">
              <el-form-item :label="$t('design.iteratorDataSource')">
                <el-input v-model="selectedNodeData.iteratorDataSource" :placeholder="$t('design.iteratorDataSourcePlaceholder')" @input="onDataChange" />
              </el-form-item>
              <el-form-item :label="$t('design.iteratorItemName')">
                <el-input v-model="selectedNodeData.iteratorItemName" placeholder="item" @input="onDataChange" />
              </el-form-item>
            </template>
          </el-form>
          <div class="panel-actions">
            <el-button type="danger" plain style="width:100%" @click="handleDeleteSelected">
              <el-icon><Delete /></el-icon> {{ $t('design.deleteNode') }}
            </el-button>
          </div>
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
          <div class="panel-actions">
            <el-button type="danger" plain style="width:100%" @click="handleDeleteSelected">
              <el-icon><Delete /></el-icon> {{ $t('design.deleteEdge') }}
            </el-button>
          </div>
        </div>
        <div v-else class="panel-body">
          <div class="panel-header" style="margin-bottom:12px">{{ $t('design.chainSettings') }}</div>
          <el-form size="small" label-position="top">
            <el-form-item :label="$t('design.chainTransaction')">
              <el-switch v-model="chainSettings.transactionEnabled" />
            </el-form-item>
            <el-form-item v-if="chainSettings.transactionEnabled" :label="$t('design.transactionPropagation')">
              <el-select v-model="chainSettings.transactionPropagation" style="width:100%">
                <el-option v-for="item in chainTransactionPropagationOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <div style="font-size:11px;color:#909399;line-height:1.5;margin-top:4px">{{ $t('design.chainTransactionHint') }}</div>
            </el-form-item>
          </el-form>
        </div>
      </div>

      <div
        v-if="isPropertyOverlay && ((isCompactEditor && nodeSheetOpen) || propertySheetOpen)"
        class="editor-sheet-backdrop"
        @click="closeCompactSheets"
      />
    </div>

    <!-- 手机端底部保存栏（拇指区，比顶栏更好点） -->
    <div v-if="isCompactEditor" class="editor-mobile-save-bar">
      <el-button type="primary" :loading="saving" @click="handleSave">
        <el-icon><Check /></el-icon> {{ $t('design.saveGraph') }}
      </el-button>
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
      <el-form inline size="default" class="responsive-filter-form" style="margin-bottom:12px">
        <el-form-item>
          <el-input v-model="bindDialog.keyword" :placeholder="$t('design.searchComponent')" clearable class="page-filter-control" @keyup.enter="fetchBindList" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="bindDialog.groupFilter" :placeholder="$t('design.allGroups')" clearable class="page-filter-control--sm" @change="fetchBindList">
            <el-option v-for="g in bindGroupOptions" :key="g" :label="g" :value="g" />
          </el-select>
        </el-form-item>
        <el-form-item class="filter-actions-item">
          <el-button type="primary" @click="fetchBindList">{{ $t('design.search') }}</el-button>
        </el-form-item>
      </el-form>
      <ResponsiveTable
        :data="bindFilteredList"
        :columns="bindColumns"
        :loading="bindDialog.loading"
        row-key="componentId"
        @row-click="onBindSelect"
      >
        <template #componentId="{ row }">
          <el-link type="primary" :underline="'never'" style="font-family:monospace;font-weight:500;cursor:pointer" @click.stop="openCompDetail(row)">
            {{ row.componentId }}
          </el-link>
        </template>
        <template #componentType="{ row }">
          <el-tag :type="bindTypeTagType(row.componentType)" size="small">
            {{ bindTypeLabel(row.componentType) }}
          </el-tag>
        </template>
        <template #timeout="{ row }">{{ row.timeout === -1 ? '-' : row.timeout }}</template>
        <template #async="{ row }">
          <el-tag :type="row.async ? 'warning' : 'info'" size="small">
            {{ row.async ? $t('components.yes') : $t('components.no') }}
          </el-tag>
        </template>
        <template #status="{ row }">
          <el-tag :type="registryStatusTagType(row.status)" size="small">
            {{ registryStatusLabel(row.status) }}
          </el-tag>
        </template>
        <template #selected="{ row }">
          <el-tag v-if="bindDialog.selectedIds.includes(row.componentId)" type="success" size="small">{{ $t('design.selected') }}</el-tag>
        </template>
        <template #tags="{ row }">
          <el-tag v-if="row.tagDefs && row.tagDefs.length" size="small" type="info">{{ row.tagDefs.length }}</el-tag>
        </template>
      </ResponsiveTable>
      <div class="page-pagination-wrap">
        <el-pagination
            v-model:current-page="bindDialog.page"
            v-model:page-size="bindDialog.pageSize"
            :total="bindDialog.total"
            :page-sizes="[5, 10, 20]"
            :layout="paginationLayout"
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
    <el-drawer v-model="compDrawer.visible" :title="$t('design.componentDetail')" :size="compDrawerSize" class="detail-drawer" destroy-on-close>
      <div v-if="compDrawer.data" class="detail-drawer-body">
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
            <el-tag :type="registryStatusTagType(compDrawer.data.status)" size="small">
              {{ registryStatusLabel(compDrawer.data.status) }}
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
            <ResponsiveTable
              :data="compDrawer.data.tagDefs"
              :columns="compTagColumns"
              :row-key="compTagRowKey"
            >
              <template #value="{ row }">
                <el-tag size="small" type="info">{{ row.value }}</el-tag>
              </template>
            </ResponsiveTable>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>

    <!-- AI 检测报告 -->
    <el-dialog
      v-model="aiDetectDialog.visible"
      :title="$t('ai.aiDetectReport')"
      width="640px"
      top="8vh"
      class="ai-detect-dialog"
      destroy-on-close
    >
      <div class="ai-detect-summary" :class="aiDetectDialog.passed ? 'ai-detect-summary--pass' : 'ai-detect-summary--fail'">
        <el-icon class="ai-detect-summary__icon">
          <CircleCheck v-if="aiDetectDialog.passed" />
          <WarningFilled v-else />
        </el-icon>
        <div class="ai-detect-summary__text">
          <div class="ai-detect-summary__title">
            {{ aiDetectDialog.passed ? $t('ai.aiDetectPassed') : $t('ai.aiDetectFailed') }}
          </div>
          <div class="ai-detect-summary__desc">
            {{ aiDetectDialog.passed ? $t('ai.aiDetectPassedDesc') : $t('ai.aiDetectFailedDesc', { count: aiDetectDialog.issueCount }) }}
          </div>
        </div>
      </div>
      <div v-if="aiDetectDialog.sections.length > 0" class="ai-detect-sections">
        <div v-for="(section, si) in aiDetectDialog.sections" :key="si" class="ai-detect-section">
          <div class="ai-detect-section__header">
            <span class="ai-detect-section__title">{{ section.title }}</span>
            <el-tag :type="section.items.length === 0 ? 'success' : section.severity" size="small" effect="plain">
              {{ section.items.length === 0 ? $t('ai.aiDetectSectionOk') : $t('ai.errorCount', { count: section.items.length }) }}
            </el-tag>
          </div>
          <ul v-if="section.items.length > 0" class="ai-detect-issue-list">
            <li v-for="(item, ii) in section.items" :key="ii">{{ item }}</li>
          </ul>
        </div>
      </div>
      <template #footer>
        <el-button @click="aiDetectDialog.visible = false">{{ $t('common.close') }}</el-button>
        <el-button
          v-if="aiDetectDialog.isBootstrap && copilotEnabled"
          type="warning"
          plain
          @click="openComposeFromDetect"
        >
          <el-icon><MagicStick /></el-icon> {{ $t('design.composeFromPattern') }}
        </el-button>
        <el-button
          v-if="!aiDetectDialog.passed && copilotEnabled"
          type="primary"
          plain
          @click="openCopilotFromDetect"
        >
          <el-icon><MagicStick /></el-icon> {{ $t('ai.fixErrors') }}
        </el-button>
      </template>
    </el-dialog>

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

    <AiCopilotDrawer
      v-model="showCopilot"
      :enabled="copilotEnabled"
      :get-context="getCopilotContext"
      :playground-chain-code="playgroundChainCode"
      :playground-app-code="appCode"
      @apply-proposal="applyAiProposal"
      @highlight-diff="applyAiDiffHighlight"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, nextTick, reactive, computed, watch } from 'vue'
import { storeToRefs } from 'pinia'
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
import { useDict } from '@/composables/useDict'
import { useDictLabel } from '@/composables/useDictLabel'
import { useResponsiveDrawerSize } from '@/composables/useResponsiveDrawerSize'
import { useResponsivePagination } from '@/composables/useResponsivePagination'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import AiCopilotDrawer from '@/components/ai/AiCopilotDrawer.vue'
import AiExpressionAssist from '@/components/ai/AiExpressionAssist.vue'
import { aiApi, isCopilotAvailable } from '@/api/ai'
import { useAiCopilotStore } from '@/stores/aiCopilot'
import { applyChainDefinitionToGraph, type ChainDefinitionDTO } from '@/utils/chainApply'
import {
  AI_DIFF_STYLES,
  classifyNodeDiff,
  computeChainDiff,
  type ChainDiffSummary,
} from '@/utils/chainDiff'
import {
  refreshEdgeRouting,
  refreshNodeEdgeRouting,
  snapEdgeEndpoints,
} from '@/utils/flowPortAssign'
import { componentApi } from '@/api/component'
import { executorApi } from '@/api/executor'
import ExecutorReadCacheAlert from '@/components/ExecutorReadCacheAlert.vue'
import { consumeExecutorReadCacheMeta } from '@/composables/useExecutorReadCache'
import {
  normalizeNodeType,
  mapNodeTypeToDto,
  getShapeForNodeType,
  getNodeSize,
  isRectPortType,
  canBindNodeType,
} from '@/utils/nodeType'
import {
  NODE_TYPE_REGISTRY,
  paletteNodeTypes,
  paletteNodeTypesByCategory,
  type NodeCategory,
  getNodeTypeMeta,
  defaultNodeFieldValues,
  extractConfigFromNodeData,
  hydrateNodeDataFromConfig,
} from '@/config/nodeTypeRegistry'
import { registerFlowShapes } from '@/utils/flowShapes'
import {
  ArrowLeft, Check, Pointer, Back, Right,
  CopyDocument, DocumentAdd,
  ZoomIn, ZoomOut, FullScreen, ScaleToOriginal,
  Delete, Select, Edit, Picture,
  ArrowRight, ArrowDown, Sort, Rank, Plus, Setting, Close, DArrowRight, DArrowLeft, MagicStick,
  CircleCheck, WarningFilled,
} from '@element-plus/icons-vue'

const { t } = useI18n()
const { drawerSize: compDrawerSize } = useResponsiveDrawerSize(500)
const { paginationLayout } = useResponsivePagination()

const bindColumns = computed(() => [
  { prop: 'componentId', label: t('design.colComponentId'), width: 170, showOverflowTooltip: true },
  { prop: 'componentName', label: t('design.colName'), minWidth: 80, showOverflowTooltip: true },
  { prop: 'componentType', label: t('design.colType'), width: 80, align: 'center' as const },
  { prop: 'groupName', label: t('design.colGroup'), width: 90, showOverflowTooltip: true },
  { prop: 'executorSource', label: t('design.colSource'), width: 140, showOverflowTooltip: true },
  { prop: 'timeout', label: t('design.colTimeout'), width: 70, align: 'center' as const },
  { prop: 'async', label: t('design.colAsync'), width: 55, align: 'center' as const },
  { prop: 'status', label: t('design.colStatus'), width: 70, align: 'center' as const },
  { prop: 'cachedAt', label: t('design.colCachedAt'), width: 140, showOverflowTooltip: true },
  { prop: 'selected', label: t('design.colSelected'), width: 55, align: 'center' as const },
  { prop: 'tags', label: t('design.colTags'), width: 100, align: 'center' as const },
])

const compTagColumns = computed(() => [
  { prop: 'name', label: t('design.detailTagName'), showOverflowTooltip: true },
  { prop: 'value', label: t('design.detailTagValue'), width: 140, showOverflowTooltip: true },
])

function compTagRowKey(row: { name: string }) {
  return row.name
}
const route = useRoute()
const router = useRouter()
const designCode = route.params.id as string
const appCode = route.query.appCode as string || ''

// ====== 响应式状态 ======
const design = ref<any>(null)
const readCacheStale = ref(false)
const appName = ref('')
const saving = ref(false)
const aiDetecting = ref(false)
const showCopilot = ref(false)
const aiCopilotStore = useAiCopilotStore()
const { pendingProposal } = storeToRefs(aiCopilotStore)
const aiDiffHighlightBackup = new Map<string, { stroke?: string; strokeWidth?: number; strokeDasharray?: string | number }>()
const copilotEnabled = ref(false)
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
const selectedEdgeStyle = ref<'straight' | 'polyline' | 'curve'>('polyline')
const defaultEdgeStyle = ref<'straight' | 'polyline' | 'curve'>('polyline')
const gridSnapEnabled = ref(false)
const panModeEnabled = ref(false)
const endpointHandles = ref<{ side: 'source' | 'target'; x: number; y: number }[]>([])
const TABLET_BREAKPOINT = 768
const COMPACT_EDITOR_BREAKPOINT = 1024
const isMobileView = ref(false)
const isCompactEditor = ref(false)
const isTabletEditor = ref(false)
const nodeSheetOpen = ref(false)
const propertySheetOpen = ref(false)
const pendingPlaceNode = ref<{ type: string; label: string } | null>(null)
let draggingEp: { side: 'source' | 'target' } | null = null

const isOverlayEditor = computed(() => isCompactEditor.value || isTabletEditor.value)
const isPropertyOverlay = computed(() => isCompactEditor.value)
const isTouchPalette = computed(() => isOverlayEditor.value)
const propertyPanelCollapsed = ref(false)
const deleteSelectionLabel = computed(() => {
  if (selectedCount.value > 1) return t('design.deleteSelection', { count: selectedCount.value })
  if (selectedEdgeData.value) return t('design.deleteEdge')
  if (selectedNodeData.value) return t('design.deleteNode')
  return t('design.deleteSelection', { count: selectedCount.value })
})

const playgroundChainCode = computed(() => {
  const chains = design.value?.boundChains
  if (Array.isArray(chains) && chains.length > 0) {
    return chains[0].code || ''
  }
  return ''
})

function isBootstrapChain(): boolean {
  const chains = design.value?.boundChains
  if (Array.isArray(chains) && chains.some((c: any) => (c.deliveryLifecycle || 'bootstrap') === 'bootstrap')) {
    return true
  }
  try {
    let cd = design.value?.chainData
    if (typeof cd === 'string') cd = JSON.parse(cd)
    const lc = cd?.config?.lifecycle
    return !lc || lc === 'bootstrap'
  } catch {
    return true
  }
}

function openComposeCopilot() {
  showCopilot.value = true
}

function openComposeFromDetect() {
  aiDetectDialog.visible = false
  openComposeCopilot()
}

async function loadCopilotConfig() {
  try {
    const cfg = await aiApi.getConfig()
    copilotEnabled.value = isCopilotAvailable(cfg)
  } catch {
    copilotEnabled.value = false
  }
}

function getCopilotContext() {
  return {
    designId: designCode,
    chainCode: playgroundChainCode.value,
    appCode,
    currentChainData: JSON.stringify(translateGraphToChain()),
    graphData: graph ? JSON.stringify(serializeGraphData()) : '',
  }
}

function onPredicateScriptAiApply(value: string) {
  if (!selectedNodeData.value) return
  selectedNodeData.value.predicateScript = value
  onDataChange()
}

function onNodeScriptAiApply(value: string) {
  if (!selectedNodeData.value) return
  selectedNodeData.value.script = value
  onDataChange()
}

async function loadTemplateFromQuery() {
  const raw = route.query.aiTemplateId
  const tplId = typeof raw === 'string' ? Number(raw.trim()) : NaN
  if (!Number.isFinite(tplId) || tplId <= 0) return
  const autoApply = route.query.aiAutoApply === '1' || route.query.aiAutoApply === 'true'
  try {
    const tpl = await aiApi.getTemplate(tplId)
    if (autoApply) {
      applyAiProposal(tpl.chainData)
      aiCopilotStore.clearProposal()
      ElMessage.success(t('ai.templates.appliedToCanvas'))
      return
    }
    aiCopilotStore.setPendingProposal(tpl.chainData, tpl.promptSummary || tpl.name)
    showCopilot.value = true
    ElMessage.success(t('ai.templates.loadedInCopilot'))
  } catch {
    ElMessage.error(t('ai.templates.loadFailed'))
  }
}

function getGraphLayoutMetrics() {
  if (!graph) {
    return { centerX: 280, nextY: 120, rowGap: 72 }
  }
  const nodes = graph.getNodes()
  if (nodes.length === 0) {
    return { centerX: 280, nextY: 120, rowGap: 72 }
  }
  let maxY = 0
  let sumX = 0
  nodes.forEach(n => {
    const box = n.getBBox()
    maxY = Math.max(maxY, box.y + box.height)
    sumX += box.x + box.width / 2
  })
  return {
    centerX: sumX / nodes.length,
    nextY: maxY + 40,
    rowGap: 72,
  }
}

function addChainEdgeFromProposal(source: string, target: string, label?: string) {
  if (!graph) return
  const edge = graph.addEdge({
    source: { cell: source, port: 'b' },
    target: { cell: target, port: 't' },
    ...edgeStyleGraphOptions(defaultEdgeStyle.value),
    attrs: {
      line: { stroke: '#94a3b8', strokeWidth: 2, targetMarker: { name: 'classic', size: 8 } },
      wrap: EDGE_HIT_WRAP_ATTRS,
    },
  })
  applyEdgeHitWrap(edge as Edge)
  if (label) setEdgeLabelSafe(edge, label)
  snapEdgeEndpoints(edge as Edge, graph, true)
  refreshEdgeRouting(edge as Edge)
}

function hasChainEdge(source: string, target: string, label?: string): boolean {
  if (!graph) return false
  return graph.getEdges().some(e => {
    if (e.getSourceCellId() !== source || e.getTargetCellId() !== target) return false
    const edgeLabel = String(e.getLabels()?.[0]?.attrs?.label?.text || '')
    return (label || '') === edgeLabel
  })
}

function clearAiDiffHighlight() {
  if (!graph) return
  aiDiffHighlightBackup.forEach((backup, nodeId) => {
    const cell = graph!.getCellById(nodeId)
    if (!cell?.isNode()) return
    const node = cell as Node
    if (backup.stroke !== undefined) node.attr('body/stroke', backup.stroke)
    else node.attr('body/stroke', 'none')
    if (backup.strokeWidth !== undefined) node.attr('body/strokeWidth', backup.strokeWidth)
    else node.attr('body/strokeWidth', 0)
    if (backup.strokeDasharray !== undefined) node.attr('body/strokeDasharray', backup.strokeDasharray)
    else node.attr('body/strokeDasharray', 0)
  })
  aiDiffHighlightBackup.clear()
  graph.getEdges().forEach(e => {
    e.attr('line/stroke', '#94a3b8')
    e.attr('line/strokeWidth', 2)
    e.attr('line/strokeDasharray', 0)
  })
}

function applyAiDiffHighlight(diff: ChainDiffSummary | null) {
  if (!graph) return
  clearAiDiffHighlight()
  if (!diff) return

  graph.getNodes().forEach(n => {
    const kind = classifyNodeDiff(n.id, diff)
    if (!kind) return
    if (!aiDiffHighlightBackup.has(n.id)) {
      aiDiffHighlightBackup.set(n.id, {
        stroke: n.attr('body/stroke'),
        strokeWidth: n.attr('body/strokeWidth'),
        strokeDasharray: n.attr('body/strokeDasharray'),
      })
    }
    const style = AI_DIFF_STYLES[kind]
    n.attr('body/stroke', style.stroke)
    n.attr('body/strokeWidth', style.strokeWidth)
    n.attr('body/strokeDasharray', style.strokeDasharray || 0)
  })

  graph.getEdges().forEach(e => {
    const source = e.getSourceCellId() || ''
    const target = e.getTargetCellId() || ''
    const label = String(e.getLabels()?.[0]?.attrs?.label?.text || '')
    const key = `${source}->${target}:${label}`
    if (diff.edgeKeysAdded.includes(key)) {
      e.attr('line/stroke', '#22c55e')
      e.attr('line/strokeWidth', 3)
    } else if (diff.edgeKeysRemoved.includes(key)) {
      e.attr('line/stroke', '#ef4444')
      e.attr('line/strokeWidth', 3)
      e.attr('line/strokeDasharray', '6,3')
    }
  })
}

function applyAiProposal(proposedChainData: string) {
  if (!graph) return
  let chain: ChainDefinitionDTO
  try {
    chain = typeof proposedChainData === 'string' ? JSON.parse(proposedChainData) : proposedChainData
  } catch {
    ElMessage.error(t('ai.invalidProposal'))
    return
  }

  graph.batchUpdate(() => {
    applyChainDefinitionToGraph(chain, {
      getNodeById: (id) => {
        const cell = graph!.getCellById(id)
        if (!cell?.isNode()) return null
        return {
          getData: () => cell.getData() || {},
          setData: (data) => {
            cell.setData(data)
            updateNodeVisual(cell as Node)
          },
        }
      },
      addNode: (options) => {
        const node = graph!.addNode({
          id: options.id,
          shape: options.shape,
          x: options.x,
          y: options.y,
          width: options.width,
          height: options.height,
          data: options.data,
        })
        updateNodeVisual(node as Node)
        const nt = normalizeNodeType(options.data?.nodeType || 'NORMAL')
        node.setProp('ports', { groups: { handle: handleGroup }, items: getPorts(nt) })
      },
      addEdge: addChainEdgeFromProposal,
      hasEdge: hasChainEdge,
      getLayoutMetrics: getGraphLayoutMetrics,
    }, generateInlinePredId)
  })

  design.value.chainData = typeof proposedChainData === 'string'
    ? proposedChainData
    : JSON.stringify(chain)
  hydrateNodeConfigFromChainData()
  clearAiDiffHighlight()
  ElMessage.success(t('ai.applySuccess'))
}

defineExpose({ translateGraphToChain, getCopilotContext })

function checkViewport() {
  const width = window.innerWidth
  isMobileView.value = width < TABLET_BREAKPOINT
  const nextCompact = width < TABLET_BREAKPOINT
  const nextTablet = width >= TABLET_BREAKPOINT && width < COMPACT_EDITOR_BREAKPOINT
  if ((isCompactEditor.value || isTabletEditor.value) && !nextCompact && !nextTablet) {
    nodeSheetOpen.value = false
    propertySheetOpen.value = false
    pendingPlaceNode.value = null
  }
  isCompactEditor.value = nextCompact
  isTabletEditor.value = nextTablet
  applyTouchGraphInteraction()
}

function toggleNodeSheet() {
  propertySheetOpen.value = false
  nodeSheetOpen.value = !nodeSheetOpen.value
}

function togglePropertySheet() {
  nodeSheetOpen.value = false
  propertySheetOpen.value = !propertySheetOpen.value
}

function togglePropertyPanelCollapse() {
  if (isPropertyOverlay.value) {
    propertySheetOpen.value = !propertySheetOpen.value
    return
  }
  propertyPanelCollapsed.value = !propertyPanelCollapsed.value
}

function closeCompactSheets() {
  nodeSheetOpen.value = false
  propertySheetOpen.value = false
}

function cancelPlaceMode() {
  pendingPlaceNode.value = null
}

type PaletteNode = { type: string; label: string; color: string; icon: string }

function onPaletteItemTap(nt: PaletteNode) {
  if (!isTouchPalette.value) return
  pendingPlaceNode.value = { type: nt.type, label: nt.label }
  if (isCompactEditor.value) nodeSheetOpen.value = false
  ElMessage.info(t('design.tapToPlace'))
}

function onPaletteItemTouchEnd(e: TouchEvent, nt: PaletteNode) {
  if (!isTouchPalette.value) return
  e.preventDefault()
  onPaletteItemTap(nt)
}

watch([selectedNodeData, selectedEdgeData], ([node, edge]) => {
  if (isPropertyOverlay.value && (node || edge)) {
    if (isCompactEditor.value) nodeSheetOpen.value = false
    propertySheetOpen.value = true
  } else if ((node || edge) && propertyPanelCollapsed.value) {
    propertyPanelCollapsed.value = false
  }
})

const { options: executeStrategyOptions } = useDict('execute_strategy')
const { options: designLineTypeOptions } = useDict('design_line_type')
const { options: predicateModeOptions } = useDict('predicate_mode')
const { labelOf: enableStatusLabel, tagTypeOf: enableStatusTagType } = useDictLabel('enable_status')
const { labelOf: registryStatusLabel, tagTypeOf: registryStatusTagType } = useDictLabel('registry_status')
const { labelOf: designNodeTypeLabel } = useDictLabel('design_node_type')

const chainSettings = reactive({
  transactionEnabled: false,
  transactionPropagation: 'REQUIRED',
})

const { options: transactionPropagationDict } = useDict('transaction_propagation')

const fallbackTransactionPropagationOptions = computed(() => [
  { value: 'INHERIT', label: t('design.txInherit') },
  { value: 'REQUIRED', label: t('design.txRequired') },
  { value: 'REQUIRES_NEW', label: t('design.txRequiresNew') },
  { value: 'NESTED', label: t('design.txNested') },
  { value: 'NOT_SUPPORTED', label: t('design.txNotSupported') },
])

const transactionPropagationOptions = computed(() => {
  if (transactionPropagationDict.value.length === 0) {
    return fallbackTransactionPropagationOptions.value
  }
  return transactionPropagationDict.value.map(d => ({ value: d.value, label: d.label }))
})

const chainTransactionPropagationOptions = computed(() =>
  transactionPropagationOptions.value.filter(o => o.value !== 'INHERIT')
)

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

type AiDetectSection = {
  title: string
  severity: 'danger' | 'warning'
  items: string[]
}

const aiDetectDialog = reactive({
  visible: false,
  passed: true,
  issueCount: 0,
  isBootstrap: false,
  sections: [] as AiDetectSection[],
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
let touchPinchCleanup: (() => void) | null = null
let touchLongPressCleanup: (() => void) | null = null

const EDGE_HIT_WRAP_ATTRS = {
  connection: true,
  strokeWidth: 14,
  strokeLinejoin: 'round',
  stroke: 'transparent',
  fill: 'none',
}
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

function openContextMenuAt(clientX: number, clientY: number) {
  if (!graph) return
  closeContextMenu()
  const cell = (graph as any).getCellAt(clientX, clientY)
  if (cell) {
    graph.cleanSelection()
    graph.select(cell.id)
  }
  contextMenu.isNode = !!cell && cell.isNode()
  contextMenu.isEdge = !!cell && cell.isEdge()
  contextMenu.cell = cell
  contextMenu.x = clientX
  contextMenu.y = clientY
  contextMenu.visible = true
  contextMenuCloseHandler = () => { closeContextMenu() }
  setTimeout(() => document.addEventListener('click', contextMenuCloseHandler!), 0)
}

function onCanvasContextMenu(e: MouseEvent) {
  openContextMenuAt(e.clientX, e.clientY)
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

// ====== 节点类型定义（注册表驱动） ======
const nodeColors: Record<string, string> = Object.fromEntries(
  NODE_TYPE_REGISTRY.map(m => [m.type, m.color]),
)

const nodeTypes = computed(() => paletteNodeTypes().map(m => ({
  type: m.type,
  label: t(`design.${m.i18nKey}`),
  color: m.color,
  icon: m.icon,
})))

const paletteGroups = computed(() => paletteNodeTypesByCategory().map(g => ({
  category: g.category,
  nodes: g.nodes.map(m => ({
    type: m.type,
    label: t(`design.${m.i18nKey}`),
    color: m.color,
    icon: m.icon,
  })),
})))

function paletteCategoryLabel(category: NodeCategory) {
  return t(`design.paletteCategory.${category}`)
}

const selectedNodeTypeFields = computed(() => {
  const nt = selectedNodeData.value?.nodeType
  return getNodeTypeMeta(nt)?.fields.filter(f => f.inConfig) || []
})

function nodeColor(type: string) {
  return nodeColors[normalizeNodeType(type)] || '#3b82f6'
}

function typeLabel(type: string) {
  const nt = normalizeNodeType(type)
  const dictLabel = designNodeTypeLabel(nt)
  if (dictLabel && dictLabel !== nt) return dictLabel
  const meta = getNodeTypeMeta(nt)
  if (meta) {
    const key = `design.${meta.i18nKey}`
    const translated = t(key)
    if (translated !== key) return translated
  }
  return nt
}

function hasDescription(type: string) {
  return getNodeTypeMeta(type)?.hasDescription ?? false
}

function hasScriptField(type: string) {
  return getNodeTypeMeta(type)?.hasScript ?? false
}

function showsStandardBindPanel(nodeType: string) {
  return canBindComponent(nodeType) && nodeType !== 'CONDITION'
}

function canBindMainComponent(data: { nodeType?: string; predicateMode?: string }) {
  if (!data?.nodeType) return false
  if (data.nodeType === 'CONDITION') return data.predicateMode === 'bind'
  return canBindComponent(data.nodeType)
}

function generateInlinePredId() {
  return `INLINE_PRED_${Date.now().toString(36).toUpperCase()}`
}

function isInlinePredicateId(componentId?: string) {
  return !!componentId && String(componentId).startsWith('INLINE_PRED_')
}

function ensureConditionDefaults(data: Record<string, any>) {
  if (data.nodeType !== 'CONDITION') return data
  if (!data.predicateMode) {
    if (data.predicateScript?.trim()) {
      data.predicateMode = 'script'
    } else if (data.componentId && !isInlinePredicateId(data.componentId)) {
      data.predicateMode = 'bind'
    } else {
      data.predicateMode = 'bind'
    }
  }
  if (
    data.predicateMode === 'script'
    && !data.predicateScript?.trim()
    && data.componentId
    && !isInlinePredicateId(data.componentId)
  ) {
    data.predicateMode = 'bind'
  }
  if (!data.trueLabel) data.trueLabel = 'True'
  if (!data.falseLabel) data.falseLabel = 'False'
  if (data.predicateMode === 'script' && !data.componentId) data.componentId = generateInlinePredId()
  if (data.predicateScript === undefined) data.predicateScript = ''
  return data
}

function onPredicateModeChange() {
  if (!selectedNodeData.value) return
  const data = selectedNodeData.value
  if (data.predicateMode === 'script') {
    if (!data.componentId) data.componentId = generateInlinePredId()
    if (!data.trueLabel) data.trueLabel = 'True'
    if (!data.falseLabel) data.falseLabel = 'False'
  } else {
    data.predicateScript = ''
  }
  onDataChange()
}

/** 同步 True/False 标签到出线（按出线顺序：第一条 True，第二条 False） */
function syncConditionBranchLabels() {
  if (!graph || !selectedCell || !selectedNodeData.value) return
  onDataChange()
  const data = selectedNodeData.value
  const edges = graph.getEdges()
      .filter(e => e.getSourceCellId() === selectedCell.id)
      .sort((a, b) => a.id.localeCompare(b.id))
  if (edges.length >= 1 && data.trueLabel) setEdgeLabelSafe(edges[0], data.trueLabel)
  if (edges.length >= 2 && data.falseLabel) setEdgeLabelSafe(edges[1], data.falseLabel)
}

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
  const nt = normalizeNodeType(nodeType)
  const map: Record<string, string> = {
    NORMAL: 'EXECUTOR',
    CONDITION: 'PREDICATE',
    SELECTOR: 'SELECTOR',
    LOADER: 'LOADER',
    PARSER: 'PARSER',
    TRANSFORMER: 'TRANSFORMER',
    FILTER: 'FILTER',
    AGGREGATOR: 'AGGREGATOR',
    SPLITTER: 'SPLITTER',
    HTTP_CLIENT: 'HTTP_CLIENT',
    MQ_PRODUCER: 'MQ_PRODUCER',
    MQ_CONSUMER: 'MQ_CONSUMER',
    CACHE_READER: 'CACHE_READER',
    CACHE_WRITER: 'CACHE_WRITER',
    FORK: 'FORK',
    JOIN: 'JOIN',
    TRY_CATCH: 'TRY_CATCH',
    WHILE: 'WHILE',
    APPROVAL: 'APPROVAL',
    NOTIFICATION: 'NOTIFICATION',
    LOGGER: 'LOGGER',
    DELAY: 'DELAY',
    SCRIPT: 'EXECUTOR',
    SUB_CHAIN: 'EXECUTOR',
    ITERATOR: 'EXECUTOR',
  }
  return map[nt] || ''
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
    if (normalizeNodeType(selectedNodeData.value.nodeType) === 'CONDITION') {
      selectedNodeData.value.predicateMode = 'bind'
      selectedNodeData.value.predicateScript = ''
    }
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
  const nt = normalizeNodeType(type)
  if (isRectPortType(nt)) {
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
  // 菱形（条件/循环）：4 个顶点
  if (nt === 'CONDITION' || nt === 'WHILE') {
    return [
      { id: 't', group: 'handle', args: { x: '50%', y: '0%' } },
      { id: 'r', group: 'handle', args: { x: '100%', y: '50%' } },
      { id: 'b', group: 'handle', args: { x: '50%', y: '100%' } },
      { id: 'l', group: 'handle', args: { x: '0%', y: '50%' } },
    ]
  }
  // 六边形（多条件/选择器）：6 个顶点
  if (nt === 'SELECTOR') {
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
  const nodeType = node.getData()?.nodeType || 'NORMAL'
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

// ====== 注册 X6 原生形状（注册表批量注册） ======
function registerShapes() {
  function reg(name: string, def: any) {
    try { Graph.registerNode(name, def) } catch { /* ignore duplicate HMR */ }
  }
  registerFlowShapes(
    reg,
    NODE_TYPE_REGISTRY,
    (i18nKey, fallback) => {
      const key = `design.${i18nKey}`
      const tr = t(key)
      return tr !== key ? tr : fallback
    },
    { start: t('design.startNode'), end: t('design.endNode') },
  )
}

// ====== 更新节点视觉 ======
function updateNodeVisual(node: Node) {
  const data = node.getData() || {}
  const nt = normalizeNodeType(data.nodeType || 'NORMAL')
  node.attr('label/text', data.label || typeLabel(nt))
}

type GraphViewport = { zoom: number; tx: number; ty: number }

/** 导出画布 JSON（含节点坐标 + 视口缩放/平移） */
function serializeGraphData(): Record<string, unknown> {
  if (!graph) return { cells: [] }
  const json = graph.toJSON() as Record<string, unknown>
  const t = graph.translate()
  return {
    ...json,
    viewport: {
      zoom: graph.zoom(),
      tx: t.tx,
      ty: t.ty,
    },
  }
}

function restoreGraphViewport(viewport?: Partial<GraphViewport> | null): boolean {
  if (!graph || !viewport || typeof viewport.zoom !== 'number' || viewport.zoom <= 0) return false
  graph.zoom(viewport.zoom, { absolute: true })
  graph.translate(
    typeof viewport.tx === 'number' ? viewport.tx : 0,
    typeof viewport.ty === 'number' ? viewport.ty : 0,
  )
  zoomLevel.value = graph.zoom()
  return true
}

function edgeStyleGraphOptions(style: 'straight' | 'polyline' | 'curve') {
  const isPolyline = style === 'polyline'
  return {
    router: isPolyline
      ? { name: 'manhattan', args: { padding: { top: 15, bottom: 15, left: 15, right: 15 }, step: 10 } }
      : { name: 'normal' },
    connector: { name: style === 'straight' ? 'normal' : isPolyline ? 'rounded' : 'smooth' },
  }
}

function applyDefaultEdgeStyleToGraph(style: 'straight' | 'polyline' | 'curve') {
  if (!graph) return
  const { router, connector } = edgeStyleGraphOptions(style)
  graph.options.connecting.router = router
  graph.options.connecting.connector = connector
  ;(graph.options as any).defaultEdge.router = router as any
  ;(graph.options as any).defaultEdge.connector = connector as any
  ;(graph.options.connecting as any).sourceAnchor = undefined
}

/** 加载后仅做 orth 迁移；已保存 edgeStyle 原样还原，其余只补元数据不改 router */
function normalizeLoadedEdges() {
  if (!graph) return
  graph.getEdges().forEach(e => {
    const savedStyle = e.getData()?.edgeStyle as 'straight' | 'polyline' | 'curve' | undefined
    if (savedStyle) {
      applyEdgeStyleToEdge(e, savedStyle)
      return
    }
    const r = e.getRouter()
    if (r?.name === 'orth') {
      e.setRouter({ name: 'manhattan', args: { padding: { top: 15, bottom: 15, left: 15, right: 15 }, step: 10 } })
      e.setConnector('rounded')
      e.setData({ ...(e.getData() || {}), edgeStyle: 'polyline' })
      return
    }
    e.setData({ ...(e.getData() || {}), edgeStyle: inferEdgeStyle(e) })
  })
}

// ====== 获取形状名 ======
function getShapeForType(nodeType: string): string {
  return getShapeForNodeType(nodeType)
}

function getTouchDistance(touches: TouchList) {
  const dx = touches[0].clientX - touches[1].clientX
  const dy = touches[0].clientY - touches[1].clientY
  return Math.hypot(dx, dy)
}

function teardownTouchPinchZoom() {
  touchPinchCleanup?.()
  touchPinchCleanup = null
}

function teardownTouchLongPress() {
  touchLongPressCleanup?.()
  touchLongPressCleanup = null
}

function applyEdgeHitWrap(edge: Edge) {
  edge.attr('wrap', EDGE_HIT_WRAP_ATTRS)
}

function applyAllEdgeHitWraps() {
  graph?.getEdges().forEach(applyEdgeHitWrap)
}

function setupTouchLongPress(container: HTMLElement) {
  teardownTouchLongPress()
  let timer: ReturnType<typeof setTimeout> | null = null
  let startX = 0
  let startY = 0

  const clearTimer = () => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  const onTouchStart = (e: TouchEvent) => {
    if (e.touches.length !== 1 || pendingPlaceNode.value) return
    const touch = e.touches[0]
    startX = touch.clientX
    startY = touch.clientY
    clearTimer()
    timer = setTimeout(() => {
      timer = null
      openContextMenuAt(startX, startY)
      navigator.vibrate?.(15)
    }, 480)
  }

  const onTouchMove = (e: TouchEvent) => {
    if (!timer || e.touches.length !== 1) return
    const touch = e.touches[0]
    if (Math.hypot(touch.clientX - startX, touch.clientY - startY) > 12) clearTimer()
  }

  const onTouchEnd = () => clearTimer()
  const onTouchCancel = () => clearTimer()

  container.addEventListener('touchstart', onTouchStart, { passive: true })
  container.addEventListener('touchmove', onTouchMove, { passive: true })
  container.addEventListener('touchend', onTouchEnd)
  container.addEventListener('touchcancel', onTouchCancel)

  touchLongPressCleanup = () => {
    clearTimer()
    container.removeEventListener('touchstart', onTouchStart)
    container.removeEventListener('touchmove', onTouchMove)
    container.removeEventListener('touchend', onTouchEnd)
    container.removeEventListener('touchcancel', onTouchCancel)
  }
}

function setupTouchPinchZoom(container: HTMLElement) {
  teardownTouchPinchZoom()
  let pinchState: { dist: number; scale: number } | null = null

  const onTouchStart = (e: TouchEvent) => {
    if (pendingPlaceNode.value) return
    if (e.touches.length === 2 && graph) {
      e.preventDefault()
      pinchState = { dist: getTouchDistance(e.touches), scale: graph.zoom() }
    }
  }
  const onTouchMove = (e: TouchEvent) => {
    if (!graph || !pinchState || e.touches.length !== 2) return
    e.preventDefault()
    const dist = getTouchDistance(e.touches)
    if (dist <= 0) return
    const cx = (e.touches[0].clientX + e.touches[1].clientX) / 2
    const cy = (e.touches[0].clientY + e.touches[1].clientY) / 2
    const nextScale = pinchState.scale * (dist / pinchState.dist)
    graph.zoom(nextScale, { absolute: true, center: { x: cx, y: cy } })
  }
  const onTouchEnd = (e: TouchEvent) => {
    if (e.touches.length < 2) pinchState = null
  }

  container.addEventListener('touchstart', onTouchStart, { passive: false })
  container.addEventListener('touchmove', onTouchMove, { passive: false })
  container.addEventListener('touchend', onTouchEnd)
  container.addEventListener('touchcancel', onTouchEnd)

  touchPinchCleanup = () => {
    container.removeEventListener('touchstart', onTouchStart)
    container.removeEventListener('touchmove', onTouchMove)
    container.removeEventListener('touchend', onTouchEnd)
    container.removeEventListener('touchcancel', onTouchEnd)
  }
}

function applyTouchGraphInteraction() {
  if (!graph) return
  const touchMode = isOverlayEditor.value
  graph.options.panning.enabled = true
  graph.options.panning.eventTypes = touchMode || panModeEnabled.value
    ? ['leftMouseDown']
    : ['rightMouseDown']
  graph.options.mousewheel.enabled = true
  graph.options.mousewheel.zoomAtMousePosition = true

  const selection = graph.getPlugin<any>('selection')
  if (selection) {
    selection.options.rubberband = !touchMode
  }

  if (canvasContainerRef.value) {
    canvasContainerRef.value.classList.toggle('pan-mode', touchMode || panModeEnabled.value)
  }

  if (touchMode && graphContainerRef.value) {
    setupTouchPinchZoom(graphContainerRef.value)
    setupTouchLongPress(graphContainerRef.value)
    applyAllEdgeHitWraps()
  } else {
    teardownTouchPinchZoom()
    teardownTouchLongPress()
  }
}

// ====== 初始化 Graph ======
function initGraph() {
  if (!graphContainerRef.value) return

  const touchMode = isOverlayEditor.value

  graph = new Graph({
    container: graphContainerRef.value,
    grid: { visible: true, size: 20, type: 'dot' },
    panning: { enabled: true, eventTypes: touchMode ? ['leftMouseDown'] : ['rightMouseDown'] },
    mousewheel: { enabled: true, zoomAtMousePosition: true },
    interacting: { edgeLabelMovable: true },
    connecting: {
      ...edgeStyleGraphOptions(defaultEdgeStyle.value),
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
      ...edgeStyleGraphOptions(defaultEdgeStyle.value),
      attrs: {
        line: { stroke: '#94a3b8', strokeWidth: 2, targetMarker: { name: 'classic', size: 8 } },
        wrap: EDGE_HIT_WRAP_ATTRS,
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
    enabled: true, multiple: true, rubberEdge: true, rubberNode: true,
    rubberband: !touchMode, showNodeSelectionBox: true,
  }))
  if (!touchMode && minimapContainerRef.value) {
    graph.use(new MiniMap({ container: minimapContainerRef.value, width: 200, height: 140 }))
  }
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
      if (cell.isNode()) {
        selectedCell = cell
        selectedNodeData.value = ensureConditionDefaults({ ...cell.getData() })
      }
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

  graph.on('edge:added', ({ edge }) => {
    applyEdgeHitWrap(edge)
    const data = edge.getData() || {}
    if (!data.edgeStyle) {
      applyEdgeStyleToEdge(edge as Edge, defaultEdgeStyle.value)
    }
  })

  graph.on('edge:click', ({ edge }) => {
    graph?.cleanSelection(); graph?.select(edge.id)
    updateEndpointHandles(edge)
  })

  graph.on('edge:dblclick', ({ edge, e }) => {
    triggerEdgeLabelEdit(edge, e)
  })

  graph.on('blank:click', ({ e, x, y }) => {
    if (pendingPlaceNode.value) {
      const pn = pendingPlaceNode.value
      pendingPlaceNode.value = null
      if (e && typeof e.clientX === 'number' && typeof e.clientY === 'number') {
        addNodeAtClient(pn.type, pn.label, e.clientX, e.clientY)
      } else {
        addNodeAtLocal(pn.type, pn.label, x, y)
      }
      return
    }
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

  // 节点移动后仅刷新路由，不强制改端口
  graph.on('node:moved', ({ node }) => {
    if (!graph) return
    refreshNodeEdgeRouting(graph, node)
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

  applyTouchGraphInteraction()
  if (touchMode && canvasContainerRef.value) {
    canvasContainerRef.value.classList.add('pan-mode')
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
function onDragStart(event: DragEvent, nt: PaletteNode) {
  if (event.dataTransfer) {
    event.dataTransfer.setData('text/plain', JSON.stringify({ type: nt.type, label: nt.label }))
    event.dataTransfer.effectAllowed = 'copy'
  }
}

function onDragOver(event: DragEvent) {
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'copy'
}


function buildNodeData(type: string, label: string) {
  const nt = normalizeNodeType(type)
  const meta = getNodeTypeMeta(nt)
  const fieldDefaults = meta ? defaultNodeFieldValues(meta) : {}
  return ensureConditionDefaults({
    label, nodeType: nt, description: '', preComponents: [], postComponents: [], paramResolvers: [],
    paramValidatorId: '', paramValidatorName: '', executeStrategy: 'NORMAL',
    transactionPropagation: 'INHERIT', script: '', subChainCode: '',
    iteratorDataSource: '', iteratorItemName: 'item',
    predicateMode: nt === 'CONDITION' ? 'bind' : undefined,
    predicateScript: nt === 'CONDITION' ? '' : undefined,
    trueLabel: nt === 'CONDITION' ? 'True' : undefined,
    falseLabel: nt === 'CONDITION' ? 'False' : undefined,
    componentId: nt === 'CONDITION' ? '' : '',
    ...fieldDefaults,
  })
}

function addNodeAtLocal(type: string, label: string, localX: number, localY: number) {
  if (!graph) return null
  const nt = normalizeNodeType(type)
  const shape = getShapeForType(nt)
  const [w, h] = getNodeSize(nt)
  const node = graph.addNode({
    shape,
    x: localX - w / 2,
    y: localY - h / 2,
    width: w,
    height: h,
    data: buildNodeData(nt, label),
  })
  updateNodeVisual(node)
  node.setProp('ports', { groups: { handle: handleGroup }, items: getPorts(nt) })
  return node
}

function addNodeAtClient(type: string, label: string, clientX: number, clientY: number) {
  if (!graph) return null
  const pos = graph.clientToLocal(clientX, clientY)
  return addNodeAtLocal(type, label, pos.x, pos.y)
}

function onDrop(event: DragEvent) {
  const raw = event.dataTransfer?.getData('text/plain')
  if (!raw) return
  const { type, label } = JSON.parse(raw)
  addNodeAtClient(type, label, event.clientX, event.clientY)
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
  applyEdgeStyleToEdge(edge, style)
}

function applyEdgeStyleToEdge(edge: Edge, style: 'straight' | 'polyline' | 'curve') {
  const isPolyline = style === 'polyline'
  edge.setRouter(isPolyline ? { name: 'manhattan', args: { padding: { top: 15, bottom: 15, left: 15, right: 15 }, step: 10 } } : { name: 'normal' })
  edge.setConnector(style === 'straight' ? 'normal' : isPolyline ? 'rounded' : 'smooth')
  edge.setData({ ...(edge.getData() || {}), edgeStyle: style })
}

function inferEdgeStyle(edge: Edge): 'straight' | 'polyline' | 'curve' {
  const saved = edge.getData()?.edgeStyle
  if (saved === 'straight' || saved === 'polyline' || saved === 'curve') return saved
  const r = edge.getRouter()
  const c = edge.getConnector()
  if (r?.name === 'manhattan' || r?.name === 'orth') return 'polyline'
  if (r?.name === 'normal' && c?.name === 'smooth') return 'curve'
  if (r?.name === 'normal' && (!c?.name || c?.name === 'normal')) return 'straight'
  return 'polyline'
}

function ensureEdgeStylesPersisted() {
  graph?.getEdges().forEach(e => {
    const data = e.getData() || {}
    if (!data.edgeStyle) {
      e.setData({ ...data, edgeStyle: inferEdgeStyle(e) })
    }
  })
}

function onDefaultEdgeStyleChange(style: 'straight' | 'polyline' | 'curve') {
  applyDefaultEdgeStyleToGraph(style)
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
  if (isOverlayEditor.value) {
    ElMessage.info(t('design.touchPanHint'))
    return
  }
  panModeEnabled.value = !panModeEnabled.value
  if (panModeEnabled.value) {
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
  if (cells.length > 0) {
    graph.removeCells(cells)
    selectedCell = null
    selectedNodeData.value = null
    selectedEdgeData.value = null
    hideEndpointHandles()
  }
}

function handleDeleteSelected() {
  if (!graph || selectedCount.value === 0) return
  removeSelected()
  if (isPropertyOverlay.value) propertySheetOpen.value = false
}

function clearGraphSelection() {
  if (!graph) return
  graph.cleanSelection()
  selectedCell = null
  selectedNodeData.value = null
  selectedEdgeData.value = null
  hideEndpointHandles()
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
  return mapNodeTypeToDto(nodeType)
}

/** 翻译 X6 图为 ChainDefinitionDTO JSON */
function translateGraphToChain(): any {
  if (!graph) return { nodes: [], edges: [] }

  // 链级元数据
  const root: Record<string, any> = {}
  if (design.value?.name) root.name = design.value.name
  if (designCode) root.code = designCode

  const chainConfig: Record<string, any> = {}
  if (chainSettings.transactionEnabled) {
    chainConfig.transaction = {
      enabled: true,
      propagation: chainSettings.transactionPropagation || 'REQUIRED',
    }
  }
  if (Object.keys(chainConfig).length > 0) {
    root.config = chainConfig
  }

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
        if (data.transactionPropagation && data.transactionPropagation !== 'INHERIT') {
          config.transactionPropagation = data.transactionPropagation
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
        if (normalizeNodeType(data.nodeType) === 'ITERATOR') {
          const icfg: Record<string, any> = {}
          if (data.iteratorDataSource) icfg.dataSource = data.iteratorDataSource
          if (data.iteratorItemName && data.iteratorItemName !== 'item') icfg.itemName = data.iteratorItemName
          if (Object.keys(icfg).length > 0) {
            node.config = { ...(node.config || {}), ...icfg }
          }
        }

        // 描述
        if (data.description) node.description = data.description

        // 判断元件：内联脚本 / 绑定模式
        if (normalizeNodeType(data.nodeType) === 'CONDITION') {
          const predCfg: Record<string, any> = {}
          const mode = data.predicateMode || 'bind'
          predCfg.predicateMode = mode
          if (mode === 'script') {
            if (data.predicateScript) predCfg.predicateScript = data.predicateScript
            if (data.trueLabel) predCfg.trueLabel = data.trueLabel
            if (data.falseLabel) predCfg.falseLabel = data.falseLabel
          }
          node.config = { ...(node.config || {}), ...predCfg }
        }

        // 执行策略配置
        if (Object.keys(config).length > 0) node.config = { ...(node.config || {}), ...config }

        const typeCfg = extractConfigFromNodeData(data.nodeType, data)
        if (Object.keys(typeCfg).length > 0) {
          node.config = { ...(node.config || {}), ...typeCfg }
        }

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
  nodes.forEach(n => {
    const data = ensureConditionDefaults({ ...(n.getData() || {}) })
    const nodeType = normalizeNodeType(data.nodeType)
    if (!canBindNodeType(nodeType)) return
    if (nodeType === 'CONDITION' && data.predicateMode === 'script') {
      if (!data.componentId?.trim()) {
        errors.push(t('design.inlinePredIdRequired', { label: data.label || n.id }))
      }
      if (!data.predicateScript?.trim()) {
        errors.push(t('design.predicateScriptRequired', { label: data.label || n.id }))
      }
      if (!data.trueLabel?.trim() || !data.falseLabel?.trim()) {
        errors.push(t('design.branchLabelRequired', { label: data.label || n.id }))
      }
      return
    }
    if (!data.componentId) {
      errors.push(t('design.nodeNotBound', { label: data.label || n.id, type: typeLabel(nodeType) }))
    }
  })

  // 5. 条件节点出线检查
  nodes.forEach(n => {
    const data = n.getData() || {}
    const nodeType = normalizeNodeType(data.nodeType)
    if (nodeType !== 'CONDITION' && nodeType !== 'SELECTOR') return
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

function openCopilotFromDetect() {
  aiDetectDialog.visible = false
  showCopilot.value = true
}

/** AI 检测：拓扑校验 + 交付门禁（保存前不再弹窗打断） */
async function handleAiDetect() {
  if (!graph) {
    ElMessage.warning(t('design.canvasNotInit'))
    return
  }
  aiDetecting.value = true
  const sections: AiDetectSection[] = []
  try {
    const topologyErrors = validateChain()
    sections.push({
      title: t('ai.aiDetectTopology'),
      severity: 'danger',
      items: topologyErrors,
    })

    const cycleWarnings = detectCycleWarnings()
    sections.push({
      title: t('ai.aiDetectCycles'),
      severity: 'warning',
      items: cycleWarnings,
    })

    const bootstrapItems = isBootstrapChain()
      ? [t('design.bootstrapBannerDesc')]
      : []
    sections.push({
      title: t('ai.aiDetectBootstrap'),
      severity: 'warning',
      items: bootstrapItems,
    })

    const deliveryItems: string[] = []
    if (appCode) {
      try {
        const chain = translateGraphToChain()
        const delivery = await aiApi.validateDelivery({
          appCode,
          chainCode: playgroundChainCode.value || designCode,
          chainData: JSON.stringify(chain),
          graphData: JSON.stringify(serializeGraphData()),
          strictMode: true,
        })
        if (!delivery.passed) {
          deliveryItems.push(...(delivery.blocking || []), ...(delivery.warnings || []))
        }
      } catch {
        deliveryItems.push(t('ai.aiDetectDeliveryUnavailable'))
      }
    } else {
      deliveryItems.push(t('ai.aiDetectNoAppCode'))
    }
    sections.push({
      title: t('ai.aiDetectDelivery'),
      severity: 'danger',
      items: deliveryItems,
    })

    const deliveryBlocking = deliveryItems.filter(
      (i) => i !== t('ai.aiDetectNoAppCode') && i !== t('ai.aiDetectDeliveryUnavailable'),
    )
    const issueCount = topologyErrors.length + cycleWarnings.length + deliveryBlocking.length + bootstrapItems.length
    aiDetectDialog.sections = sections
    aiDetectDialog.issueCount = issueCount
    aiDetectDialog.isBootstrap = bootstrapItems.length > 0
    aiDetectDialog.passed = topologyErrors.length === 0
      && deliveryBlocking.length === 0
      && cycleWarnings.length === 0
      && bootstrapItems.length === 0
    aiDetectDialog.visible = true
  } finally {
    aiDetecting.value = false
  }
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

function hydrateNodeTransactionFromChainData() {
  if (!graph || !design.value?.chainData) return
  try {
    let chainData = design.value.chainData
    if (typeof chainData === 'string') chainData = JSON.parse(chainData)
    const nodes = chainData?.nodes
    if (!Array.isArray(nodes)) return
    const txMap = new Map<string, string>()
    nodes.forEach((n: any) => {
      const prop = n?.config?.transactionPropagation
      if (n?.id && prop) txMap.set(n.id, prop)
    })
    graph.getNodes().forEach(n => {
      const prop = txMap.get(n.id)
      if (prop) {
        n.setData({ ...(n.getData() || {}), transactionPropagation: prop })
      }
    })
  } catch { /* ignore */ }
}

function hydrateNodeConfigFromChainData() {
  if (!graph || !design.value?.chainData) return
  try {
    let chainData = design.value.chainData
    if (typeof chainData === 'string') chainData = JSON.parse(chainData)
    const nodes = chainData?.nodes
    if (!Array.isArray(nodes)) return
    const cfgMap = new Map<string, Record<string, any>>()
    nodes.forEach((n: any) => {
      if (n?.id && n?.config) cfgMap.set(n.id, n.config)
    })
    graph.getNodes().forEach(n => {
      const cfg = cfgMap.get(n.id)
      if (!cfg) return
      const data = { ...(n.getData() || {}) }
      hydrateNodeDataFromConfig(data.nodeType || 'NORMAL', data, cfg)
      n.setData(data)
    })
  } catch { /* ignore */ }
}

function hydrateChainSettingsFromDesign() {
  chainSettings.transactionEnabled = false
  chainSettings.transactionPropagation = 'REQUIRED'
  if (!design.value?.chainData) return
  try {
    let chainData = design.value.chainData
    if (typeof chainData === 'string') chainData = JSON.parse(chainData)
    const tx = chainData?.config?.transaction
    if (tx) {
      chainSettings.transactionEnabled = !!tx.enabled
      if (tx.propagation) chainSettings.transactionPropagation = String(tx.propagation).toUpperCase()
    }
  } catch { /* ignore */ }
}

// ====== 加载设计 ======
async function loadDesign() {
  try {
    const raw = await designApi.getByCode(designCode, appCode)
    design.value = consumeExecutorReadCacheMeta(raw, readCacheStale) as typeof design.value
    // 获取应用名称
    if (appCode) {
      try {
        const apps = await executorApi.listApps()
        const mod = apps.find((a: any) => a.appCode === appCode)
        if (mod) appName.value = mod.appName || mod.appCode
      } catch { /* ignore */ }
    }
    if (!graph) return
    hydrateChainSettingsFromDesign()
    if (design.value.graphData) {
      let data = design.value.graphData
      if (typeof data === 'string') data = JSON.parse(data)
      const viewport = (data as { viewport?: GraphViewport })?.viewport
      const cells = (data as { cells?: any[] })?.cells ?? []
      if (cells.length > 0) {
        cells.forEach((cell: any) => {
          if (cell.data?.nodeType) {
            cell.data.nodeType = normalizeNodeType(cell.data.nodeType)
          }
          if (cell.shape === 'flow-node' && cell.data?.nodeType) {
            cell.shape = getShapeForType(cell.data.nodeType)
          }
          if (cell.view === 'vue-shape-view') delete cell.view
        })
        graph.fromJSON({ cells })
        graph.getNodes().forEach(n => {
          updateNodeVisual(n)
          const nodeType = normalizeNodeType(n.getData()?.nodeType || 'NORMAL')
          n.setProp('ports', { groups: { handle: handleGroup }, items: getPorts(nodeType) })
          if (nodeType === 'CONDITION') {
            n.setData(ensureConditionDefaults({ ...(n.getData() || {}), nodeType }))
          } else if (n.getData()?.nodeType !== nodeType) {
            n.setData({ ...(n.getData() || {}), nodeType })
          }
        })
        normalizeLoadedEdges()
        hydrateNodeTransactionFromChainData()
        hydrateNodeConfigFromChainData()
        applyAllEdgeHitWraps()
        await nextTick()
        if (!restoreGraphViewport(viewport)) {
          graph.zoomToFit({ padding: 60, maxScale: 1 })
          zoomLevel.value = graph.zoom()
        }
        return
      }
    }
    // 空设计
    const start = graph.addNode({ shape: 'flow-start', x: 250, y: 40, width: 148, height: 40, data: { label: t('design.startNode'), nodeType: 'start' } })
    const end = graph.addNode({ shape: 'flow-end', x: 250, y: 380, width: 148, height: 40, data: { label: t('design.endNode'), nodeType: 'end' } })
    updateNodeVisual(start)
    updateNodeVisual(end)
    start.setProp('ports', { groups: { handle: handleGroup }, items: getPorts('start') })
    end.setProp('ports', { groups: { handle: handleGroup }, items: getPorts('end') })
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

  const validationErrors = validateChain()
  const flowValid = validationErrors.length === 0

  if (!flowValid) {
    try {
      await ElMessageBox.confirm(
          t('design.saveDespiteInvalid', { errors: validationErrors.join('\n') }),
          t('common.confirm'),
          { confirmButtonText: t('design.saveGraph'), cancelButtonText: t('common.cancel'), type: 'warning' }
      )
    } catch { return }
  }

  // 死环警告（不阻断）
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
    ensureEdgeStylesPersisted()
    const json = serializeGraphData()
    const chain = translateGraphToChain()
    await designApi.saveGraph(designCode, appCode, JSON.stringify(json), JSON.stringify(chain))
    ElMessage.success(flowValid ? t('design.saveGraphSuccess') : t('design.saveGraphDesigning'))
  } catch { ElMessage.error(t('design.saveFailed')) }
  finally { saving.value = false }
}

function goBack() { router.push('/design') }

// ====== 生命周期 ======
onMounted(async () => {
  checkViewport()
  window.addEventListener('resize', checkViewport)
  registerShapes()
  await nextTick()
  initGraph()
  await loadDesign()
  void loadCopilotConfig()
  await loadTemplateFromQuery()
})

watch(pendingProposal, (proposal) => {
  if (!proposal || !graph) {
    clearAiDiffHighlight()
    return
  }
  applyAiDiffHighlight(computeChainDiff(JSON.stringify(translateGraphToChain()), proposal))
})

onBeforeUnmount(() => {
  clearAiDiffHighlight()
  window.removeEventListener('resize', checkViewport)
  closeContextMenu()
  inlineEditor.show = false
  document.removeEventListener('click', inlineEditorOutsideClick)
  resizeObserver?.disconnect()
  teardownTouchPinchZoom()
  teardownTouchLongPress()
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
.toolbar-right { display: flex; align-items: center; gap: 6px; flex-shrink: 0; }
.toolbar-ai-btn,
.toolbar-ai-detect-btn {
  border-radius: 6px;
  font-weight: 500;
  font-size: 13px;
  padding: 6px 12px;
  height: 32px;
  transition: box-shadow 0.15s, transform 0.12s;
}
.toolbar-ai-btn:not(.is-disabled):hover,
.toolbar-ai-detect-btn:not(.is-disabled):hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.08);
}
.toolbar-ai-btn {
  background: linear-gradient(135deg, #f5f3ff 0%, #ede9fe 100%);
  border: 1px solid #ddd6fe;
  color: #6d28d9;
}
.toolbar-ai-btn.is-disabled {
  opacity: 0.55;
}
.toolbar-ai-detect-btn {
  background: linear-gradient(135deg, #eff6ff 0%, #dbeafe 100%);
  border: 1px solid #bfdbfe;
  color: #1d4ed8;
}
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

.editor-body { display: flex; flex: 1; overflow: hidden; position: relative; min-height: 0; }

.editor-mobile-save-bar {
  display: none;
}

.node-palette { width: 180px; border-right: 1px solid #e2e8f0; background: #f8fafc; flex-shrink: 0; overflow-y: auto; }
.node-palette--touch {
  overscroll-behavior: contain;
  overscroll-behavior-x: none;
  touch-action: pan-y;
}
.node-palette--touch .palette-item {
  cursor: pointer;
  touch-action: manipulation;
  -webkit-user-drag: none;
}
.node-palette--touch .palette-tap-hint {
  display: inline;
}
.palette-header { padding: 12px 16px; font-weight: 600; font-size: 13px; color: #475569; border-bottom: 1px solid #e2e8f0; }
.palette-list { padding: 8px; }
.palette-group { margin-bottom: 10px; }
.palette-group-title {
  padding: 4px 8px 6px;
  font-size: 11px;
  font-weight: 600;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

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
.canvas-area--touch .graph-container { touch-action: none; }
.graph-container { width: 100%; height: 100%; }

.minimap-container {
  position: absolute; bottom: 12px; right: 12px;
  border: 1px solid #e2e8f0; border-radius: 6px; background: #fff;
  box-shadow: 0 2px 12px rgba(0,0,0,0.08); z-index: 10; overflow: hidden;
}

.property-panel { width: 260px; border-left: 1px solid #e2e8f0; background: #f8fafc; flex-shrink: 0; overflow-y: auto; transition: width 0.2s ease, opacity 0.2s ease; }
.property-panel--collapsed {
  width: 0;
  min-width: 0;
  border-left: none;
  opacity: 0;
  overflow: hidden;
  pointer-events: none;
}
.property-panel-expand {
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  z-index: 25;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 10px 6px;
  border: 1px solid #e2e8f0;
  border-right: none;
  border-radius: 8px 0 0 8px;
  background: #fff;
  color: #475569;
  font-size: 11px;
  line-height: 1.2;
  cursor: pointer;
  box-shadow: -4px 0 16px rgba(15, 23, 42, 0.08);
}
.property-panel-expand:hover { background: #f8fafc; color: #3b82f6; }
.panel-header { padding: 12px 16px; font-weight: 600; font-size: 13px; color: #475569; border-bottom: 1px solid #e2e8f0; display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.panel-body { padding: 16px; }
.panel-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 48px 16px; color: #94a3b8; font-size: 13px; }

.ai-detect-summary {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 10px;
  margin-bottom: 16px;
}
.ai-detect-summary--pass {
  background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
  border: 1px solid #bbf7d0;
}
.ai-detect-summary--fail {
  background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
  border: 1px solid #fde68a;
}
.ai-detect-summary__icon {
  font-size: 28px;
  flex-shrink: 0;
  margin-top: 2px;
}
.ai-detect-summary--pass .ai-detect-summary__icon { color: #16a34a; }
.ai-detect-summary--fail .ai-detect-summary__icon { color: #d97706; }
.ai-detect-summary__title {
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
  margin-bottom: 4px;
}
.ai-detect-summary__desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
}
.ai-detect-sections {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 42vh;
  overflow-y: auto;
}
.ai-detect-section {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}
.ai-detect-section__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 14px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}
.ai-detect-section__title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.ai-detect-issue-list {
  margin: 0;
  padding: 10px 14px 10px 30px;
  font-size: 13px;
  color: #475569;
  line-height: 1.6;
}
.ai-detect-issue-list li + li {
  margin-top: 6px;
}

.palette-tap-hint {
  display: none;
  margin-left: auto;
  font-size: 11px;
  color: #64748b;
  flex-shrink: 0;
}

.place-node-banner {
  position: absolute;
  top: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 60;
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: calc(100% - 24px);
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.88);
  color: #fff;
  font-size: 12px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.25);
  pointer-events: auto;
}

.selection-action-bar {
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 72px;
  z-index: 56;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 8px 28px rgba(15, 23, 42, 0.14);
  border: 1px solid #e2e8f0;
  pointer-events: auto;
}

.selection-action-bar__count {
  flex: 1;
  min-width: 0;
  font-size: 12px;
  color: #475569;
  white-space: nowrap;
}

.panel-actions {
  padding: 12px 16px 16px;
  border-top: 1px solid #e2e8f0;
  margin-top: 8px;
}

.canvas-area--placing .graph-container {
  cursor: crosshair;
}

.canvas-fab-bar {
  display: none;
}

.editor-sheet-backdrop {
  display: none;
}

@media (max-width: 1023px) {
  .design-editor-x6 {
    height: calc(100vh - 52px);
    height: calc(100dvh - 52px);
  }

  .editor-body--tablet .minimap-container,
  .editor-body--compact .minimap-container {
    display: none;
  }

  .property-panel--overlay {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    width: 100%;
    max-height: min(62vh, 520px);
    z-index: 210;
    border-radius: 16px 16px 0 0;
    box-shadow: 0 -10px 40px rgba(15, 23, 42, 0.18);
    transform: translateY(100%);
    transition: transform 0.24s ease;
    border: none;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    pointer-events: none;
  }

  .property-panel--overlay.property-panel--sheet-open {
    transform: translateY(0);
    pointer-events: auto;
  }

  .property-panel--overlay .panel-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    flex-shrink: 0;
  }

  .property-panel--overlay .panel-body {
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
    flex: 1;
    min-height: 0;
  }

  .palette-item--pending {
    box-shadow: 0 0 0 2px #3b82f6;
  }
}

@media (min-width: 768px) and (max-width: 1023px) {
  .editor-body--tablet {
    flex-direction: row;
  }

  .editor-body--tablet .node-palette--tablet {
    width: 120px;
    flex-shrink: 0;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
    border-right: 1px solid #e2e8f0;
    display: flex;
    flex-direction: column;
  }

  .editor-body--tablet .palette-header {
    padding: 8px 10px;
    font-size: 11px;
    flex-shrink: 0;
  }

  .editor-body--tablet .palette-list {
    padding: 6px;
    display: flex;
    flex-direction: column;
    gap: 6px;
    flex: 1;
    min-height: 0;
    overflow-y: auto;
  }

  .editor-body--tablet .palette-item {
    flex-direction: column;
    align-items: center;
    text-align: center;
    padding: 8px 4px;
    margin-bottom: 0;
    cursor: pointer;
    gap: 4px;
  }

  .editor-body--tablet .palette-label {
    font-size: 10px;
    line-height: 1.2;
    word-break: break-all;
  }

  .editor-body--tablet .canvas-area {
    flex: 1;
    min-width: 0;
  }

  .editor-body--tablet .property-panel {
    width: 220px;
  }

  .editor-body--tablet .property-panel--collapsed {
    width: 0;
  }
}

@media (max-width: 767px) {
  .editor-body--compact {
    flex-direction: column;
  }

  .editor-body--compact .canvas-area {
    flex: 1;
    width: 100%;
    min-height: 0;
  }

  .editor-body--compact .node-palette--compact {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    width: 100%;
    max-height: min(62vh, 520px);
    z-index: 210;
    border-radius: 16px 16px 0 0;
    box-shadow: 0 -10px 40px rgba(15, 23, 42, 0.18);
    transform: translateY(100%);
    transition: transform 0.24s ease;
    border: none;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    pointer-events: none;
  }

  .editor-body--compact .node-palette--compact.node-palette--sheet-open {
    transform: translateY(0);
    pointer-events: auto;
  }

  .editor-body--compact .palette-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    flex-shrink: 0;
  }

  .editor-body--compact .palette-list {
    display: flex;
    flex-direction: column;
    gap: 8px;
    overflow-y: auto;
    -webkit-overflow-scrolling: touch;
    padding: 8px 12px 16px;
    flex: 1;
    min-height: 0;
  }

  .editor-body--compact .palette-item {
    margin-bottom: 0;
    cursor: pointer;
    flex-shrink: 0;
  }

  .editor-body--compact .palette-tap-hint {
    display: inline;
  }

  .canvas-fab-bar {
    display: flex;
    position: absolute;
    left: 12px;
    right: 12px;
    bottom: 12px;
    z-index: 55;
    gap: 8px;
    pointer-events: none;
  }

  .canvas-fab-bar .el-button {
    pointer-events: auto;
    flex: 1;
    min-width: 0;
    margin: 0;
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.12);
  }

  .editor-body--compact .canvas-fab-bar .fab-btn--active {
    box-shadow: 0 8px 24px rgba(59, 130, 246, 0.35);
  }

  .editor-body--compact .selection-action-bar {
    bottom: 72px;
  }

  .editor-sheet-backdrop {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 200;
    background: rgba(15, 23, 42, 0.35);
  }
}

@media (max-width: 767px) {
  .design-editor-x6 {
    border-radius: 0;
    margin: 0 -12px;
    width: calc(100% + 24px);
  }

  .editor-toolbar,
  .editor-toolbar--mobile {
    flex-wrap: wrap;
    align-items: flex-start;
    padding: 6px 8px;
    gap: 6px;
  }

  .toolbar-left {
    flex: 1 1 100%;
    min-width: 0;
    gap: 4px;
  }

  .toolbar-left .el-button {
    padding-left: 4px;
    padding-right: 4px;
  }

  .toolbar-title {
    font-size: 13px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
    min-width: 0;
  }

  .app-prefix {
    display: none;
  }

  .toolbar-center {
    flex: 1 1 100%;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
    flex-wrap: nowrap;
    padding-bottom: 2px;
  }

  .toolbar-center .el-dropdown,
  .toolbar-center .el-select {
    display: none;
  }

  .toolbar-right {
    flex: 1 1 100%;
    justify-content: flex-end;
    gap: 8px;
  }

  .toolbar-save-btn {
    display: none;
  }

  .editor-mobile-save-bar {
    display: block;
    flex-shrink: 0;
    padding: 8px 12px calc(8px + env(safe-area-inset-bottom, 0px));
    border-top: 1px solid #e2e8f0;
    background: #fff;
    z-index: 60;
  }

  .editor-mobile-save-bar .el-button {
    width: 100%;
    margin: 0;
  }
}
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

@media (max-width: 1023px) {
  .x6-context-menu {
    min-width: 180px;
    padding: 6px;
  }
  .x6-context-menu .context-item {
    min-height: 44px;
    padding: 10px 14px;
    font-size: 14px;
  }
}

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
