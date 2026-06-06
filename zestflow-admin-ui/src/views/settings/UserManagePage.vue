<template>
  <div class="user-manage">
    <div v-if="userStore.user?.isSuperAdmin === 1">
    <div class="page-header">
      <div class="page-header-row">
        <div class="page-stats-row">
          <span class="summary-total" style="font-weight:600;color:#409eff">{{ $t('settings.userManage') }} {{ total }}</span>
        </div>
        <el-button type="primary" @click="openCreate">{{ $t('settings.createUser') }}</el-button>
      </div>
    </div>

    <el-form :model="filter" inline size="default" class="responsive-filter-form" style="margin-bottom:12px">
      <el-form-item :label="$t('common.username')">
        <el-input v-model="filter.username" :placeholder="$t('common.username')" clearable class="page-filter-control--xs" />
      </el-form-item>
      <el-form-item :label="$t('common.email')">
        <el-input v-model="filter.email" :placeholder="$t('common.email')" clearable class="page-filter-control" />
      </el-form-item>
      <el-form-item :label="$t('common.status')">
        <el-select v-model="filter.status" :placeholder="$t('common.all')" clearable class="page-filter-control--sm">
          <el-option :label="$t('settings.enabled')" :value="1" />
          <el-option :label="$t('settings.disabled')" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item :label="$t('settings.isSuperAdmin')">
        <el-select v-model="filter.isSuperAdmin" :placeholder="$t('common.all')" clearable class="page-filter-control--sm">
          <el-option :label="$t('settings.yes')" :value="1" />
          <el-option :label="$t('settings.no')" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item class="filter-actions-item">
        <el-button type="primary" @click="handleSearch">{{ $t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ $t('common.reset') }}</el-button>
      </el-form-item>
    </el-form>

    <ResponsiveTable
      :data="userList"
      :columns="userColumns"
      :loading="loading"
      row-key="id"
      :show-actions="true"
      :actions-label="$t('common.actions')"
      :actions-width="230"
    >
      <template #status="{ row }">
        <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
          {{ row.status === 1 ? $t('settings.enabled') : $t('settings.disabled') }}
        </el-tag>
      </template>
      <template #isSuperAdmin="{ row }">
        <el-tag :type="row.isSuperAdmin === 1 ? 'warning' : 'info'" size="small">
          {{ row.isSuperAdmin === 1 ? $t('settings.yes') : $t('settings.no') }}
        </el-tag>
      </template>
      <template #appRoles="{ row }">{{ row.appRoles?.length || 0 }}</template>
      <template #actions="{ row }">
        <el-button text type="primary" size="small" class="action-btn" @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
        <el-button text type="primary" size="small" class="action-btn" @click="openAssignApps(row)">{{ $t('settings.assignApps') }}</el-button>
        <el-button text type="primary" size="small" class="action-btn" @click="handleResetPassword(row)">{{ $t('settings.resetPassword') }}</el-button>
        <el-button text type="danger" size="small" class="action-btn" @click="handleDelete(row)">{{ $t('common.delete') }}</el-button>
      </template>
    </ResponsiveTable>
    <div class="page-pagination-wrap">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        :layout="paginationLayout"
        @current-change="fetchList"
        @size-change="fetchList"
      />
    </div>

    <!-- 新建/编辑用户弹窗 -->
    <el-dialog
      v-model="userDialogVisible"
      :title="isEditingUser ? $t('settings.editUser') : $t('settings.createUser')"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form ref="userFormRef" :model="userForm" :rules="userRules" label-width="120px" @submit.prevent>
        <el-form-item :label="$t('common.username')" prop="username">
          <el-input v-model="userForm.username" maxlength="50" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="$t('common.email')" prop="email">
          <el-input v-model="userForm.email" maxlength="100" autocomplete="off" />
        </el-form-item>
        <el-form-item :label="$t('settings.isSuperAdmin')">
          <el-switch v-model="userForm.isSuperAdmin" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item v-if="isEditingUser" :label="$t('common.status')">
          <el-switch v-model="userForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="userSubmitting" @click="handleUserSubmit">
          {{ $t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 创建成功弹窗（显示账号密码） -->
    <el-dialog
      v-model="resultDialogVisible"
      :title="$t('settings.createResultTitle')"
      width="400px"
      :close-on-click-modal="false"
    >
      <div class="result-box">
        <div class="result-row">
          <span class="result-label">{{ $t('common.username') }}</span>
          <span class="result-value">{{ resultAccount }}</span>
        </div>
        <div class="result-row">
          <span class="result-label">{{ $t('settings.passwordLabel') }}</span>
          <span class="result-value result-password">{{ resultPassword }}</span>
        </div>
        <el-alert
          :title="$t('settings.savePasswordWarning')"
          type="warning"
          :closable="false"
          show-icon
          style="margin-top:16px"
        />
      </div>
      <template #footer>
        <el-button type="primary" @click="resultDialogVisible = false">{{ $t('settings.savedConfirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码成功弹窗 -->
    <el-dialog
      v-model="resetResultVisible"
      :title="$t('settings.passwordResetResultTitle')"
      width="400px"
      :close-on-click-modal="false"
    >
      <div class="result-box">
        <div class="result-row">
          <span class="result-label">{{ $t('settings.newPasswordFor') }}</span>
          <span class="result-value result-password">{{ resetResultPassword }}</span>
        </div>
        <el-alert
          :title="$t('settings.keepPasswordWarning')"
          type="warning"
          :closable="false"
          show-icon
          style="margin-top:16px"
        />
      </div>
      <template #footer>
        <el-button type="primary" @click="resetResultVisible = false">{{ $t('settings.savedConfirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 分配应用角色弹窗 -->
    <el-dialog
      v-model="assignDialogVisible"
      :title="$t('settings.assignApps')"
      width="600px"
      :close-on-click-modal="false"
    >
      <div v-if="assignTargetUser" class="assign-info">
        <strong>{{ assignTargetUser.username }}</strong>
        <el-tag v-if="assignTargetUser.email" size="small" class="assign-info-tag">
          {{ assignTargetUser.email }}
        </el-tag>
      </div>
      <ResponsiveTable
        :data="assignApps"
        :columns="assignAppColumns"
        row-key="appCode"
        :show-actions="true"
        :actions-label="$t('common.actions')"
        :actions-width="80"
        style="margin-top: 12px"
      >
        <template #actions="{ row }">
          <el-button text type="danger" size="small" @click="handleRemoveAssignment(assignApps.findIndex(a => a.appCode === row.appCode))">
            {{ $t('common.delete') }}
          </el-button>
        </template>
      </ResponsiveTable>
      <template #footer>
        <el-button type="primary" @click="openAddAssignment">
          {{ $t('settings.addAssignment') }}
        </el-button>
        <el-button @click="assignDialogVisible = false">{{ $t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 添加分配子弹窗 -->
    <el-dialog
      v-model="addAssignDialogVisible"
      :title="$t('settings.addAssignment')"
      width="420px"
      :close-on-click-modal="false"
    >
      <el-form ref="assignFormRef" :model="assignForm" :rules="assignRules" label-width="100px">
        <el-form-item :label="$t('settings.appName')" prop="appCode">
          <el-select v-model="assignForm.appCode" filterable style="width: 100%" :placeholder="$t('settings.selectApp')">
            <el-option
              v-for="m in availableAppOptions"
              :key="m.appCode"
              :label="m.appName || m.appCode"
              :value="m.appCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('settings.appRole')" prop="roleId">
          <el-select v-model="assignForm.roleId" filterable style="width: 100%" :placeholder="$t('settings.selectRole')">
            <el-option
              v-for="r in roleOptions"
              :key="r.id"
              :label="r.name"
              :value="r.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addAssignDialogVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="assignSubmitting" @click="handleAddAssignment">
          {{ $t('common.save') }}
        </el-button>
      </template>
    </el-dialog>
    </div>
    <div v-else style="text-align:center;padding:80px 0;color:#909399;font-size:16px">
      {{ $t('settings.superAdminOnly') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { userManageApi, roleApi } from '@/api/user-manage'
import { executorApi } from '@/api/executor'
import type { UserManageVO, UserUpdateDTO, RoleVO } from '@/api/user-manage'
import type { AppOption } from '@/api/executor'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import { useResponsivePagination } from '@/composables/useResponsivePagination'

const userStore = useUserStore()
const { t } = useI18n()
const { paginationLayout } = useResponsivePagination()

const userColumns = computed(() => [
  { prop: 'username', label: t('common.username'), showOverflowTooltip: true },
  { prop: 'email', label: t('common.email'), showOverflowTooltip: true },
  { prop: 'status', label: t('common.status'), width: 90, align: 'center' as const },
  { prop: 'isSuperAdmin', label: t('settings.isSuperAdmin'), width: 100, align: 'center' as const },
  { prop: 'appRoles', label: t('settings.assignedApps'), width: 120, align: 'center' as const },
])

const assignAppColumns = computed(() => [
  { prop: 'appName', label: t('settings.appName'), showOverflowTooltip: true },
  { prop: 'roleName', label: t('settings.appRole'), showOverflowTooltip: true },
])

const loading = ref(false)
const userList = ref<UserManageVO[]>([])
const total = ref(0)

// 筛选条件
const filter = ref({
  username: '',
  email: '',
  status: '' as number | string,
  isSuperAdmin: '' as number | string,
})

// 分页
const page = ref(1)
const pageSize = ref(10)

function handleSearch() {
  page.value = 1
  fetchList()
}

function handleReset() {
  filter.value = { username: '', email: '', status: '', isSuperAdmin: '' }
  page.value = 1
  fetchList()
}

// 用户表单
const userDialogVisible = ref(false)
const userSubmitting = ref(false)
const isEditingUser = ref(false)
const editingUserId = ref<number | null>(null)
const userFormRef = ref<any>(null)
const userForm = ref<any>({
  username: '',
  email: '',
  isSuperAdmin: 0,
})
const userRules = {
  username: [
    { required: true, message: () => t('register.usernameRequired'), trigger: 'blur' },
  ],
  email: [
    { required: true, message: () => t('register.emailRequired'), trigger: 'blur' },
    { type: 'email', message: () => t('validation.emailFormat'), trigger: 'blur' },
  ],
}

// 创建成功结果弹窗
const resultDialogVisible = ref(false)
const resultAccount = ref('')
const resultPassword = ref('')

// 重置密码结果弹窗
const resetResultVisible = ref(false)
const resetResultPassword = ref('')

// 分配应用角色
const assignDialogVisible = ref(false)
const assignTargetUser = ref<UserManageVO | null>(null)
const assignApps = ref<{ appCode: string; appName: string; roleId: number; roleName: string; defaultEntry?: boolean }[]>([])

// 添加分配
const addAssignDialogVisible = ref(false)
const assignSubmitting = ref(false)
const assignFormRef = ref<any>(null)
const roleOptions = ref<RoleVO[]>([])
const assignForm = ref({ appCode: undefined as string | undefined, roleId: undefined as number | undefined })
const assignRules = {
  appCode: [{ required: true, message: () => t('settings.selectApp'), trigger: 'change' }],
  roleId: [{ required: true, message: () => t('settings.selectRole'), trigger: 'change' }],
}

// 未分配的应用选项（过滤掉已分配的）
const availableAppOptions = ref<AppOption[]>([])
const appMap = ref<Record<string, string>>({})

async function fetchList() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      page: page.value,
      size: pageSize.value,
    }
    if (filter.value.username) params.username = filter.value.username
    if (filter.value.email) params.email = filter.value.email
    if (filter.value.status === 0 || filter.value.status === 1) params.status = filter.value.status
    if (filter.value.isSuperAdmin === 0 || filter.value.isSuperAdmin === 1) params.isSuperAdmin = filter.value.isSuperAdmin
    const res = await userManageApi.list(params)
    // 分页响应：res 为 PageResponse；兼容旧版全量数组
    if (Array.isArray(res)) {
      userList.value = res
      total.value = res.length
    } else {
      userList.value = res.records
      total.value = res.total
    }
  } finally {
    loading.value = false
  }
}

function openCreate() {
  isEditingUser.value = false
  editingUserId.value = null
  userForm.value = { username: '', email: '', isSuperAdmin: 0 }
  userDialogVisible.value = true
}

function openEdit(row: UserManageVO) {
  isEditingUser.value = true
  editingUserId.value = row.id
  userForm.value = {
    username: row.username,
    email: row.email,
    isSuperAdmin: row.isSuperAdmin,
    status: row.status,
  }
  userDialogVisible.value = true
}

async function handleUserSubmit() {
  const valid = await userFormRef.value?.validate().catch(() => false)
  if (!valid) return
  userSubmitting.value = true
  try {
    if (isEditingUser.value && editingUserId.value) {
      const dto: UserUpdateDTO = {
        username: userForm.value.username,
        email: userForm.value.email,
        status: userForm.value.status,
        isSuperAdmin: userForm.value.isSuperAdmin,
      }
      await userManageApi.update(editingUserId.value, dto)
      ElMessage.success(t('settings.updateSuccess'))
    } else {
      const res = await userManageApi.create({
        username: userForm.value.username,
        email: userForm.value.email,
        isSuperAdmin: userForm.value.isSuperAdmin,
      })
      resultAccount.value = res.username
      resultPassword.value = res.generatedPassword || ''
      resultDialogVisible.value = true
    }
    userDialogVisible.value = false
    await fetchList()
  } finally {
    userSubmitting.value = false
  }
}

function handleDelete(row: UserManageVO) {
  ElMessageBox.confirm(
    t('settings.deleteConfirm', { name: row.username }),
    t('settings.confirmDelete'),
    { confirmButtonText: t('settings.yes'), cancelButtonText: t('settings.no'), type: 'warning' },
  ).then(async () => {
    await userManageApi.delete(row.id)
    ElMessage.success(t('settings.deleteSuccess'))
    await fetchList()
  }).catch(() => {})
}

function resolveAppName(appCode: string): string {
  return appMap.value[appCode] || appCode
}

async function openAssignApps(row: UserManageVO) {
  assignTargetUser.value = row

  // 构建 appCode → appName 映射
  try {
    const apps = await executorApi.listApps()
    appMap.value = {}
    for (const a of apps) {
      appMap.value[a.appCode] = a.appName || a.appCode
    }
  } catch {
    appMap.value = {}
  }

  assignApps.value = (row.appRoles || []).map(r => ({
    appCode: r.appCode,
    appName: resolveAppName(r.appCode),
    roleId: r.roleId,
    roleName: r.roleName,
  }))

  // 超级管理员默认拥有所有应用权限（未持久化的默认条目）
  if (row.isSuperAdmin === 1) {
    try {
      const allRoles = await roleApi.list()
      const adminRole = allRoles.find(r => r.code === 'APP_ADMIN')
      const assignedIds = new Set(assignApps.value.map(a => a.appCode))
      for (const appCode of Object.keys(appMap.value)) {
        if (!assignedIds.has(appCode)) {
          assignApps.value.push({
            appCode,
            appName: appMap.value[appCode],
            roleId: adminRole?.id ?? -1,
            roleName: adminRole?.name ?? '应用管理员',
            defaultEntry: true,
          })
        }
      }
    } catch {}
  }

  assignDialogVisible.value = true
}

async function openAddAssignment() {
  assignForm.value = { appCode: undefined, roleId: undefined }
  try {
    const [apps, roles] = await Promise.all([executorApi.listApps(), roleApi.list()])
    roleOptions.value = roles
    // 过滤掉已分配的应用
    const assignedIds = new Set(assignApps.value.map(a => a.appCode))
    availableAppOptions.value = apps.filter(a => !assignedIds.has(a.appCode))
  } catch {
    return
  }
  addAssignDialogVisible.value = true
}

async function handleAddAssignment() {
  const valid = await assignFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!assignTargetUser.value) return
  assignSubmitting.value = true
  try {
    await userManageApi.assignAppRole({
      userId: assignTargetUser.value.id,
      appCode: assignForm.value.appCode!,
      roleId: assignForm.value.roleId!,
    })
    ElMessage.success(t('settings.assignSuccess'))
    addAssignDialogVisible.value = false
    // 刷新分配列表
    const updated = await userManageApi.getById(assignTargetUser.value.id)
    assignApps.value = (updated.appRoles || []).map(r => ({
      appCode: r.appCode,
      appName: resolveAppName(r.appCode),
      roleId: r.roleId,
      roleName: r.roleName,
    }))
    // 同时更新 userList
    const idx = userList.value.findIndex(u => u.id === assignTargetUser.value!.id)
    if (idx !== -1) userList.value[idx] = updated
  } finally {
    assignSubmitting.value = false
  }
}

async function handleRemoveAssignment(index: number) {
  if (!assignTargetUser.value) return
  const item = assignApps.value[index]

  // 默认条目（超级管理员默认全应用）只从本地移除，不调 API
  if (item.defaultEntry) {
    assignApps.value.splice(index, 1)
    return
  }

  try {
    await userManageApi.removeAppRole(assignTargetUser.value.id, item.appCode)
    ElMessage.success(t('settings.deleteSuccess'))
    assignApps.value.splice(index, 1)
    // 同步更新 userList
    const idx = userList.value.findIndex(u => u.id === assignTargetUser.value!.id)
    if (idx !== -1) {
      userList.value[idx].appRoles = userList.value[idx].appRoles?.filter(
        r => r.appCode !== item.appCode,
      )
    }
  } catch {}
}

async function handleResetPassword(row: UserManageVO) {
  try {
    await ElMessageBox.confirm(
      t('settings.resetPasswordConfirm', { username: row.username }),
      t('settings.resetPasswordTitle'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' },
    )
    const res = await userManageApi.resetPassword(row.id)
    resetResultPassword.value = res.generatedPassword
    resetResultVisible.value = true
  } catch {
    // 取消操作不做处理
  }
}

onMounted(fetchList)
</script>

<style scoped>
.assign-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid #ebeef5;
  font-size: 14px;
}
.action-btn.action-btn { padding: 2px 4px; margin-left: 0; }
.assign-info-tag {
  margin-left: 4px;
}
.empty-hint {
  text-align: center;
  color: #909399;
  padding: 24px 0;
  font-size: 14px;
}

.result-box {
  padding: 8px 0;
}
.result-row {
  display: flex;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
}
.result-label {
  width: 80px;
  font-size: 14px;
  color: #606266;
}
.result-value {
  font-size: 16px;
  color: #303133;
  font-weight: 600;
}
.result-password {
  font-family: 'Courier New', monospace;
  color: #e6a23c;
  letter-spacing: 1px;
}

/* autofill 背景色防护 */
.user-manage :deep(.el-input__inner:-webkit-autofill),
.user-manage :deep(.el-input__inner:-webkit-autofill:hover),
.user-manage :deep(.el-input__inner:-webkit-autofill:focus) {
  -webkit-box-shadow: 0 0 0 1000px #fff inset !important;
  box-shadow: 0 0 0 1000px #fff inset !important;
  -webkit-text-fill-color: #333 !important;
}
</style>
