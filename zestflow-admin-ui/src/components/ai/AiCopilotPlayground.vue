<template>
  <div v-if="enabled" class="ai-copilot-playground">
    <div class="pg-header" @click="expanded = !expanded">
      <span class="pg-title">{{ $t('ai.playground.title') }}</span>
      <el-tag size="small" type="info">{{ scenes.length }}</el-tag>
      <el-icon class="pg-toggle" :class="{ rotated: expanded }"><ArrowDown /></el-icon>
    </div>

    <div v-show="expanded" class="pg-body">
      <el-select
        v-model="selectedSceneCode"
        filterable
        size="small"
        class="pg-scene-select"
        :placeholder="$t('ai.playground.selectScene')"
        @change="onSceneChange"
      >
        <el-option
          v-for="s in scenes"
          :key="s.sceneCode"
          :label="s.name"
          :value="s.sceneCode"
        />
      </el-select>

      <el-input
        v-model="requestBody"
        type="textarea"
        :rows="4"
        class="pg-body-input"
        :placeholder="$t('ai.playground.bodyPlaceholder')"
      />

      <div class="pg-actions">
        <el-button
          type="primary"
          size="small"
          :loading="executing"
          :disabled="!selectedSceneCode"
          @click="handleExecute"
        >
          {{ $t('ai.playground.execute') }}
        </el-button>
        <el-button size="small" :disabled="!lastResult" @click="openFullPlayground">
          {{ $t('ai.playground.openFull') }}
        </el-button>
      </div>

      <div v-if="lastResult" class="pg-result">
        <el-tag :type="executionResultTagType(lastResult.status)" size="small">
          {{ executionResultLabel(lastResult.status) }}
        </el-tag>
        <span class="pg-cost">{{ lastResult.costMs }}ms</span>
        <pre class="pg-response">{{ formatResponse }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import { listAllPlaygroundScenes, getPlaygroundSceneByCode, type PlaygroundSceneVO } from '@/api/playground-scene'
import { executePlaygroundScene, type PlaygroundExecuteResult } from '@/api/playground'
import { useDictLabel } from '@/composables/useDictLabel'

const props = defineProps<{
  enabled: boolean
  appCode?: string
  chainCode?: string
}>()

const emit = defineEmits<{
  'execute-success': [result: PlaygroundExecuteResult]
}>()

const router = useRouter()
const { labelOf: executionResultLabel, tagTypeOf: executionResultTagType } = useDictLabel('execution_result')
const expanded = ref(false)
const scenes = ref<PlaygroundSceneVO[]>([])
const selectedSceneCode = ref('')
const requestBody = ref('{\n  \n}')
const executing = ref(false)
const lastResult = ref<PlaygroundExecuteResult | null>(null)

const formatResponse = computed(() => {
  if (!lastResult.value) return ''
  const payload = lastResult.value.result ?? lastResult.value
  if (typeof payload === 'string') return payload
  try {
    return JSON.stringify(payload, null, 2)
  } catch {
    return String(payload)
  }
})

async function loadScenes() {
  if (!props.enabled || !props.appCode) {
    scenes.value = []
    return
  }
  try {
    const all = await listAllPlaygroundScenes(props.appCode)
    const list = Array.isArray(all) ? all : (all as any)?.data ?? []
    scenes.value = props.chainCode
      ? list.filter((s: PlaygroundSceneVO) => s.chainCode === props.chainCode)
      : list
    if (!selectedSceneCode.value && scenes.value.length > 0) {
      selectedSceneCode.value = scenes.value[0].sceneCode
      await onSceneChange(selectedSceneCode.value)
    }
  } catch {
    scenes.value = []
  }
}

async function onSceneChange(sceneCode: string) {
  if (!sceneCode) return
  try {
    const res = await getPlaygroundSceneByCode(sceneCode)
    const scene = (res as any)?.data ?? res
    if (scene?.requestBody) {
      requestBody.value = scene.requestBody
    }
  } catch { /* ignore */ }
}

async function handleExecute() {
  if (!selectedSceneCode.value) return
  executing.value = true
  lastResult.value = null
  try {
    let params: Record<string, any>
    try {
      params = JSON.parse(requestBody.value)
    } catch {
      ElMessage.warning('JSON 格式无效')
      return
    }
    lastResult.value = await executePlaygroundScene(selectedSceneCode.value, params)
    if (lastResult.value?.status === 1) {
      emit('execute-success', lastResult.value)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '试跑失败')
  } finally {
    executing.value = false
  }
}

function openFullPlayground() {
  router.push({
    name: 'Playground',
    query: {
      appCode: props.appCode,
      chainCode: props.chainCode,
      sceneCode: selectedSceneCode.value || undefined,
    },
  })
}

watch(
  () => [props.enabled, props.appCode, props.chainCode],
  () => { void loadScenes() },
  { immediate: true },
)
</script>

<style scoped>
.ai-copilot-playground {
  border-top: 1px solid #ebeef5;
  background: #fff;
}

.pg-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  cursor: pointer;
  user-select: none;
}

.pg-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  flex: 1;
}

.pg-toggle {
  transition: transform 0.2s;
}

.pg-toggle.rotated {
  transform: rotate(180deg);
}

.pg-body {
  padding: 0 12px 12px;
}

.pg-scene-select {
  width: 100%;
  margin-bottom: 8px;
}

.pg-body-input :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.pg-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.pg-result {
  margin-top: 10px;
  padding: 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
}

.pg-cost {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.pg-response {
  margin: 8px 0 0;
  max-height: 160px;
  overflow: auto;
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
