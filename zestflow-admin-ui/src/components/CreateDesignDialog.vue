<template>
  <el-dialog
    v-model="dialogVisible"
    :title="$t('design.createDesign')"
    width="600px"
    :close-on-click-modal="false"
    :append-to-body="appendToBody"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item :label="$t('design.app')" prop="appCode">
        <el-select v-model="form.appCode" filterable style="width:100%" :placeholder="$t('design.selectApp')" :disabled="disableAppSelect">
          <el-option v-for="m in appOptions" :key="m.appCode" :label="m.appName || m.appCode" :value="m.appCode" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('design.name')" prop="name">
        <el-input v-model="form.name" maxlength="100" autocomplete="off" />
      </el-form-item>
      <el-form-item :label="$t('design.designer')" prop="designer">
        <el-input v-model="form.designer" maxlength="50" autocomplete="off" />
      </el-form-item>
      <el-form-item :label="$t('design.description')" prop="description">
        <el-input v-model="form.description" type="textarea" maxlength="500" />
      </el-form-item>
    </el-form>
    <template #footer>
      <slot name="footer" :saving="saving" :handleSave="handleSave">
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ $t('common.save') }}</el-button>
      </slot>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { designApi, type DesignVO } from '@/api/design'
import type { AppOption } from '@/api/executor'

const { t } = useI18n()

const props = withDefaults(defineProps<{
  visible: boolean
  appOptions: AppOption[]
  defaultAppCode?: string
  defaultName?: string
  disableAppSelect?: boolean
  appendToBody?: boolean
}>(), {
  appendToBody: false,
  disableAppSelect: false,
})

const emit = defineEmits<{
  'update:visible': [value: boolean]
  saved: [design: DesignVO]
}>()

const formRef = ref<any>(null)
const saving = ref(false)
const savedOnce = ref(false)
const form = ref({ name: '', description: '', designer: '', appCode: '' })

const rules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('design.name') }), trigger: 'blur' }],
  appCode: [{ required: true, message: () => t('design.selectModule'), trigger: 'change' }],
}

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

watch(() => props.visible, (v) => {
  if (v) {
    savedOnce.value = false
    form.value = { name: props.defaultName || '', description: '', designer: '', appCode: props.defaultAppCode || '' }
  }
})

async function handleSave() {
  if (savedOnce.value) return null
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return null
  saving.value = true
  try {
    const res = await designApi.create({
      name: form.value.name,
      description: form.value.description || undefined,
      designer: form.value.designer || undefined,
      appCode: form.value.appCode,
    })
    savedOnce.value = true
    emit('saved', res)
    return res
  } finally {
    saving.value = false
  }
}
</script>
