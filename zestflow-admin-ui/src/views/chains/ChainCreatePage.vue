<template>
  <div class="chain-create">
    <div class="page-header">
      <h2>{{ $t('chains.createTitle') }}</h2>
    </div>
    <div class="form-wrapper">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="120px" style="max-width:600px" @submit.prevent>
        <el-form-item :label="$t('chains.name')" prop="name">
          <el-input v-model="form.name" maxlength="100" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="$t('chains.app')" prop="appCode">
          <el-select v-model="form.appCode" filterable style="width:100%" :placeholder="$t('chains.selectApp')">
            <el-option v-for="m in modules" :key="m.appCode" :label="m.appName || m.appCode" :value="m.appCode" />
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
import { executorApi, type AppOption } from '@/api/executor'

const { t } = useI18n()
const router = useRouter()

const modules = ref<AppOption[]>([])
const formRef = ref<any>(null)
const submitting = ref(false)
const form = ref({ name: '', appCode: '', description: '' })
const rules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('chains.name') }), trigger: 'blur' }],
  appCode: [{ required: true, message: () => t('chains.selectModule'), trigger: 'change' }],
}

onMounted(async () => {
  try {
    modules.value = await executorApi.listApps()
  } catch { /* ignore */ }
})

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid || !form.value.appCode) return
  submitting.value = true
  try {
    const res = await chainApi.create({
      name: form.value.name,
      appCode: form.value.appCode,
      description: form.value.description || undefined,
    } as ChainCreateDTO)
    ElMessage.success(t('chains.createChain') + '成功，' + t('chains.code') + '：' + res.code)
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

@media (max-width: 767px) {
  .chain-create {
    padding: 12px;
  }

  .form-wrapper {
    padding: 0;
  }

  .page-header h2 {
    font-size: 16px;
  }
}
</style>
