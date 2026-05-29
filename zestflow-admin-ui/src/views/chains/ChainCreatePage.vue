<template>
  <div class="chain-create">
    <div class="page-header">
      <h2>{{ $t('chains.createTitle') }}</h2>
    </div>
    <div class="form-wrapper">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" style="max-width:600px" @submit.prevent>
        <el-form-item :label="$t('chains.code')" prop="code">
          <el-input v-model="form.code" maxlength="50" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="$t('chains.name')" prop="name">
          <el-input v-model="form.name" maxlength="100" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="$t('chains.module')" prop="moduleId">
          <el-select v-model="form.moduleId" filterable style="width:100%" :placeholder="$t('chains.selectModule')">
            <el-option v-for="m in modules" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('chains.description')" prop="description">
          <el-input v-model="form.description" type="textarea" maxlength="500" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">{{ $t('common.save') }}</el-button>
          <el-button @click="$router.push('/chains')">{{ $t('common.cancel') }}</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { chainApi, type ChainCreateDTO } from '@/api/chain'
import { moduleApi, type ModuleVO } from '@/api/module'

const { t } = useI18n()
const router = useRouter()

const modules = ref<ModuleVO[]>([])
const formRef = ref<any>(null)
const submitting = ref(false)
const form = ref({ code: '', name: '', moduleId: undefined as number | undefined, description: '' })
const rules = {
  code: [{ required: true, message: () => t('validation.required', { field: t('chains.code') }), trigger: 'blur' }],
  name: [{ required: true, message: () => t('validation.required', { field: t('chains.name') }), trigger: 'blur' }],
  moduleId: [{ required: true, message: () => t('chains.selectModule'), trigger: 'change' }],
}

onMounted(async () => {
  try {
    modules.value = await moduleApi.list()
  } catch { /* ignore */ }
})

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !form.value.moduleId) return
  submitting.value = true
  try {
    const dto: ChainCreateDTO = {
      code: form.value.code,
      name: form.value.name,
      moduleId: form.value.moduleId,
      description: form.value.description || undefined,
    }
    await chainApi.create(dto)
    ElMessage.success(t('chains.createChain') + '成功')
    router.push('/chains')
  } finally { submitting.value = false }
}
</script>

<style scoped>
.chain-create {
  background: #fff;
  padding: 20px;
  border-radius: 4px;
}
.page-header { margin-bottom: 20px; }
.page-header h2 { margin: 0; font-size: 18px; color: #303133; }
.form-wrapper { padding: 0 20px; }
</style>
