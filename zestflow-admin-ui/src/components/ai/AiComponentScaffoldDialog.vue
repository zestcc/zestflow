<template>
  <el-dialog
    v-model="visible"
    :title="$t('ai.scaffold.title')"
    width="680px"
    append-to-body
    destroy-on-close
    @closed="resetForm"
  >
    <el-form :model="form" label-width="110px" size="default">
      <el-form-item :label="$t('components.selectApp')" required>
        <el-select v-model="form.appCode" filterable class="page-filter-control">
          <el-option v-for="a in apps" :key="a.appCode" :label="a.appName || a.appCode" :value="a.appCode" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('components.componentId')" required>
        <el-input v-model="form.componentId" placeholder="deductStock" style="font-family:monospace" />
      </el-form-item>
      <el-form-item :label="$t('components.componentType')" required>
        <el-select v-model="form.componentType">
          <el-option label="EXECUTOR" value="EXECUTOR" />
          <el-option label="PREDICATE" value="PREDICATE" />
          <el-option label="SELECTOR" value="SELECTOR" />
          <el-option label="LOADER" value="LOADER" />
          <el-option label="PARSER" value="PARSER" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('components.groupName')">
        <el-input v-model="form.groupName" placeholder="order" />
      </el-form-item>
      <el-form-item :label="$t('components.description')" required>
        <el-input v-model="form.description" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>

    <div v-if="scaffoldResult" class="scaffold-output">
      <div class="scaffold-output-header">
        <span>{{ scaffoldResult.summary }}</span>
        <el-button size="small" @click="copyCode">{{ $t('ai.scaffold.copy') }}</el-button>
      </div>
      <ul v-if="scaffoldResult.checklist?.length" class="scaffold-checklist">
        <li v-for="(item, i) in scaffoldResult.checklist" :key="i">{{ item }}</li>
      </ul>
      <pre class="scaffold-code">{{ scaffoldResult.fullJavaCode }}</pre>
    </div>

    <template #footer>
      <el-button @click="visible = false">{{ $t('common.close') }}</el-button>
      <el-button type="primary" :loading="loading" @click="handleGenerate">
        {{ $t('ai.scaffold.generate') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { aiApi, type AiComponentScaffoldResponse } from '@/api/ai'
import { executorApi, type AppOption } from '@/api/executor'

const { t } = useI18n()
const visible = ref(false)
const loading = ref(false)
const apps = ref<AppOption[]>([])
const scaffoldResult = ref<AiComponentScaffoldResponse | null>(null)

const form = reactive({
  appCode: '',
  componentId: '',
  componentType: 'EXECUTOR',
  groupName: 'default',
  description: '',
})

async function open(appCode?: string) {
  visible.value = true
  scaffoldResult.value = null
  if (apps.value.length === 0) {
    try {
      apps.value = await executorApi.listApps()
    } catch { /* ignore */ }
  }
  form.appCode = appCode || apps.value[0]?.appCode || ''
}

function resetForm() {
  form.componentId = ''
  form.componentType = 'EXECUTOR'
  form.groupName = 'default'
  form.description = ''
  scaffoldResult.value = null
}

async function handleGenerate() {
  if (!form.appCode || !form.componentId.trim() || !form.description.trim()) {
    ElMessage.warning(t('ai.scaffold.required'))
    return
  }
  loading.value = true
  try {
    scaffoldResult.value = await aiApi.scaffoldComponent({
      appCode: form.appCode,
      componentId: form.componentId.trim(),
      componentType: form.componentType,
      groupName: form.groupName.trim() || 'default',
      description: form.description.trim(),
    })
  } catch {
    ElMessage.error(t('ai.scaffold.failed'))
  } finally {
    loading.value = false
  }
}

async function copyCode() {
  if (!scaffoldResult.value?.fullJavaCode) return
  try {
    await navigator.clipboard.writeText(scaffoldResult.value.fullJavaCode)
    ElMessage.success(t('ai.scaffold.copied'))
  } catch {
    ElMessage.error(t('ai.templates.copyFailed'))
  }
}

defineExpose({ open })
</script>

<style scoped>
.scaffold-output {
  margin-top: 12px;
  border-top: 1px solid #ebeef5;
  padding-top: 12px;
}
.scaffold-output-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
}
.scaffold-checklist {
  margin: 0 0 8px;
  padding-left: 18px;
  font-size: 12px;
  color: #606266;
}
.scaffold-code {
  max-height: 320px;
  overflow: auto;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px;
  font-size: 11px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>
