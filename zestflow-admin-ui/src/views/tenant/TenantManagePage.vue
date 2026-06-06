<template>
  <div class="tenant-manage-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ $t('tenant.title') }}</span>
          <el-button type="primary" @click="openCreate">{{ $t('common.create') }}</el-button>
        </div>
      </template>

      <!-- 筛选栏 -->
      <el-form :inline="true" size="default" class="responsive-filter-form">
        <el-form-item :label="$t('common.keyword')">
          <el-input
            v-model="filter.keyword"
            :placeholder="$t('tenant.keywordPlaceholder')"
            clearable
            class="page-filter-control"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item class="filter-actions-item">
          <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
          <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
        </el-form-item>
      </el-form>

      <ResponsiveTable
        :data="tableData"
        :columns="tenantColumns"
        :loading="loading"
        row-key="id"
        :show-actions="true"
        :actions-label="$t('common.actions')"
        :actions-width="180"
      >
        <template #code="{ row }">
          <el-button type="primary" link @click="openDetail(row)">{{ row.code }}</el-button>
        </template>
        <template #status="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? $t('tenant.active') : $t('tenant.inactive') }}
          </el-tag>
        </template>
        <template #actions="{ row }">
          <el-button type="primary" link size="small" @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
          <el-button type="danger" link size="small" :disabled="row.id === 1" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
        </template>
      </ResponsiveTable>

      <!-- 分页 -->
      <div class="page-pagination-wrap">
        <el-pagination
          v-if="total > 0"
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          :layout="paginationLayout"
          background
          @current-change="fetchData"
        />
      </div>
    </el-card>

    <!-- 新建/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEditing ? $t('tenant.edit') : $t('tenant.create')"
      :width="600"
      :close-on-click-modal="false"
      @closed="dialogClosed"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item :label="$t('tenant.name')" prop="name">
          <el-input v-model="form.name" :placeholder="$t('tenant.namePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('tenant.code')" prop="code" v-if="!isEditing">
          <el-input v-model="form.code" :placeholder="$t('tenant.codePlaceholder')" />
        </el-form-item>
        <el-form-item :label="$t('tenant.description')" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            :placeholder="$t('tenant.descPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="$t('common.status')" prop="status" v-if="isEditing">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">{{ $t('tenant.active') }}</el-radio>
            <el-radio :value="0">{{ $t('tenant.inactive') }}</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ $t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="$t('tenant.detail')"
      :size="drawerSize"
      class="detail-drawer"
      @closed="currentTenant = null"
    >
      <div v-if="currentTenant" class="detail-drawer-body">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item :label="$t('tenant.code')">{{ currentTenant.code }}</el-descriptions-item>
          <el-descriptions-item :label="$t('tenant.name')">{{ currentTenant.name }}</el-descriptions-item>
          <el-descriptions-item :label="$t('tenant.description')">{{ currentTenant.description || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('common.status')">
            <el-tag :type="currentTenant.status === 1 ? 'success' : 'info'" size="small">
              {{ currentTenant.status === 1 ? $t('tenant.active') : $t('tenant.inactive') }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.createdBy')">{{ currentTenant.createdBy || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('common.updatedBy')">{{ currentTenant.updatedBy || '-' }}</el-descriptions-item>
          <el-descriptions-item :label="$t('common.createdAt')">{{ currentTenant.createdAt }}</el-descriptions-item>
          <el-descriptions-item :label="$t('common.updatedAt')">{{ currentTenant.updatedAt }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import type { TenantVO } from '@/api/tenant'
import { tenantApi } from '@/api/tenant'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import { useResponsiveDrawerSize } from '@/composables/useResponsiveDrawerSize'
import { useResponsivePagination } from '@/composables/useResponsivePagination'

const { t } = useI18n()
const { drawerSize } = useResponsiveDrawerSize(400)
const { paginationLayout } = useResponsivePagination()

const tenantColumns = computed(() => [
  { prop: 'code', label: t('tenant.code'), minWidth: 160, showOverflowTooltip: true },
  { prop: 'name', label: t('tenant.name'), minWidth: 160, showOverflowTooltip: true },
  { prop: 'description', label: t('tenant.description'), minWidth: 200, showOverflowTooltip: true },
  { prop: 'status', label: t('common.status'), width: 100, align: 'center' as const },
  { prop: 'createdBy', label: t('common.createdBy'), width: 120, showOverflowTooltip: true },
  { prop: 'createdAt', label: t('common.createdAt'), width: 160, showOverflowTooltip: true },
])

const loading = ref(false)
const saving = ref(false)
const tableData = ref<TenantVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filter = reactive({
  keyword: '',
})

const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)

const drawerVisible = ref(false)
const currentTenant = ref<TenantVO | null>(null)

const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  code: '',
  description: '',
  status: 1,
})

const formRules: FormRules = {
  name: [{ required: true, message: () => t('validation.required', { field: t('tenant.name') }), trigger: 'blur' }],
  code: [{ required: true, message: () => t('validation.required', { field: t('tenant.code') }), trigger: 'blur' }],
}

onMounted(() => {
  fetchData()
})

async function fetchData() {
  loading.value = true
  try {
    const res: TenantVO[] = await tenantApi.listAll()
    if (filter.keyword) {
      const kw = filter.keyword.toLowerCase()
      tableData.value = res.filter(t => t.code.toLowerCase().includes(kw) || t.name.toLowerCase().includes(kw))
    } else {
      tableData.value = res
    }
    total.value = tableData.value.length
  } catch {
    // handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchData()
}

function handleReset() {
  filter.keyword = ''
  page.value = 1
  fetchData()
}

function openCreate() {
  isEditing.value = false
  editingId.value = null
  form.name = ''
  form.code = ''
  form.description = ''
  form.status = 1
  dialogVisible.value = true
}

function openEdit(row: TenantVO) {
  isEditing.value = true
  editingId.value = row.id
  form.name = row.name
  form.code = row.code
  form.description = row.description || ''
  form.status = row.status ?? 1
  dialogVisible.value = true
}

function dialogClosed() {
  fetchData()
}

async function handleSave() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  saving.value = true
  try {
    if (isEditing.value && editingId.value) {
      await tenantApi.update(editingId.value, {
        name: form.name,
        description: form.description,
        status: form.status,
      })
      ElMessage.success(t('common.updateSuccess'))
    } else {
      await tenantApi.create({
        name: form.name,
        code: form.code,
        description: form.description,
      })
      ElMessage.success(t('common.updateSuccess'))
    }
    dialogVisible.value = false
  } catch {
    // handled by interceptor
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: TenantVO) {
  try {
    await ElMessageBox.confirm(
      t('tenant.deleteConfirm', { name: row.name }),
      t('common.confirm'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
    await tenantApi.delete(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    fetchData()
  } catch {
    // cancelled or error
  }
}

function openDetail(row: TenantVO) {
  currentTenant.value = row
  drawerVisible.value = true
}
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
