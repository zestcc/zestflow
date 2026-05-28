<template>
  <div class="user-manage">
    <div class="page-header">
      <div class="stats-summary">
        <span class="summary-total" style="font-weight:600;color:#409eff">用户 {{ filteredList.length }}</span>
      </div>
      <el-button type="primary" @click="openCreate">
        {{ $t('settings.createUser') }}
      </el-button>
    </div>

    <!-- 筛选条件 -->
    <el-form :model="filter" inline size="default" style="margin-bottom:12px">
      <el-form-item label="用户名">
        <el-input v-model="filter.username" placeholder="输入用户名" clearable style="width:140px" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="filter.email" placeholder="输入邮箱" clearable style="width:180px" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filter.status" placeholder="全部" clearable style="width:100px">
          <el-option :label="$t('settings.enabled')" :value="1" />
          <el-option :label="$t('settings.disabled')" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item label="超管">
        <el-select v-model="filter.isSuperAdmin" placeholder="全部" clearable style="width:100px">
          <el-option :label="$t('settings.yes')" :value="1" />
          <el-option :label="$t('settings.no')" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table
      :data="paginatedList"
      v-loading="loading"
      stripe border
      style="width: 100%"
      :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}"
    >
      <el-table-column prop="username" :label="$t('common.username')" show-overflow-tooltip />
      <el-table-column prop="email" :label="$t('common.email')" show-overflow-tooltip />
      <el-table-column prop="status" :label="$t('common.status')" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? $t('settings.enabled') : $t('settings.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('settings.isSuperAdmin')" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.isSuperAdmin === 1 ? 'warning' : 'info'" size="small">
            {{ row.isSuperAdmin === 1 ? $t('settings.yes') : $t('settings.no') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="$t('settings.assignedModules')" width="120" align="center">
        <template #default="{ row }">
          {{ row.moduleRoles?.length || 0 }}
        </template>
      </el-table-column>
      <el-table-column :label="$t('common.actions')" width="330" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openEdit(row)">
            {{ $t('common.edit') }}
          </el-button>
          <el-button text type="primary" size="small" @click="openAssignModules(row)">
            {{ $t('settings.assignModules') }}
          </el-button>
          <el-button text type="primary" size="small" @click="handleResetPassword(row)">
            {{ $t('settings.resetPassword') }}
          </el-button>
          <el-button text type="danger" size="small" @click="handleDelete(row)">
            {{ $t('common.delete') }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <div style="display:flex;justify-content:flex-end;margin-top:12px">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="filteredList.length"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
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
      title="创建成功"
      width="400px"
      :close-on-click-modal="false"
    >
      <div class="result-box">
        <div class="result-row">
          <span class="result-label">用户名</span>
          <span class="result-value">{{ resultAccount }}</span>
        </div>
        <div class="result-row">
          <span class="result-label">密码</span>
          <span class="result-value result-password">{{ resultPassword }}</span>
        </div>
        <el-alert
          title="请妥善保存账号密码，关闭后将无法再次查看密码"
          type="warning"
          :closable="false"
          show-icon
          style="margin-top:16px"
        />
      </div>
      <template #footer>
        <el-button type="primary" @click="resultDialogVisible = false">我已保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码成功弹窗 -->
    <el-dialog
      v-model="resetResultVisible"
      title="密码已重置"
      width="400px"
      :close-on-click-modal="false"
    >
      <div class="result-box">
        <div class="result-row">
          <span class="result-label">新密码</span>
          <span class="result-value result-password">{{ resetResultPassword }}</span>
        </div>
        <el-alert
          title="请妥善保管新密码，关闭后将无法再次查看"
          type="warning"
          :closable="false"
          show-icon
          style="margin-top:16px"
        />
      </div>
      <template #footer>
        <el-button type="primary" @click="resetResultVisible = false">我已保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配模块角色弹窗 -->
    <el-dialog
      v-model="assignDialogVisible"
      :title="$t('settings.assignModules')"
      width="600px"
      :close-on-click-modal="false"
    >
      <div v-if="assignTargetUser" class="assign-info">
        <strong>{{ assignTargetUser.username }}</strong>
        <el-tag v-if="assignTargetUser.email" size="small" class="assign-info-tag">
          {{ assignTargetUser.email }}
        </el-tag>
      </div>
      <el-table :data="assignModules" stripe border style="width: 100%; margin-top: 12px" :header-cell-style="{background:'#f5f7fa',color:'#303133',fontWeight:600}">
        <el-table-column prop="moduleName" :label="$t('settings.moduleName')" />
        <el-table-column prop="roleName" :label="$t('settings.moduleRole')" />
        <el-table-column :label="$t('common.actions')" width="80" align="center">
          <template #default="{ row, $index }">
            <el-button text type="danger" size="small" @click="handleRemoveAssignment($index)">
              {{ $t('common.delete') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
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
        <el-form-item :label="$t('settings.moduleName')" prop="moduleId">
          <el-select v-model="assignForm.moduleId" filterable style="width: 100%" :placeholder="$t('settings.selectModule')">
            <el-option
              v-for="m in availableModuleOptions"
              :key="m.id"
              :label="m.name"
              :value="m.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('settings.moduleRole')" prop="roleId">
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
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { userManageApi, roleApi } from '@/api/user-manage'
import { moduleApi } from '@/api/module'
import type { UserManageVO, UserUpdateDTO, RoleVO, AssignModuleRoleDTO } from '@/api/user-manage'
import type { ModuleVO } from '@/api/module'

const { t } = useI18n()

const loading = ref(false)
const userList = ref<UserManageVO[]>([])

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

const filteredList = computed(() => {
  let list = userList.value
  const f = filter.value
  if (f.username) list = list.filter(u => u.username.includes(f.username))
  if (f.email) list = list.filter(u => u.email.includes(f.email))
  if (f.status === 0 || f.status === 1) list = list.filter(u => u.status === f.status)
  if (f.isSuperAdmin === 0 || f.isSuperAdmin === 1) list = list.filter(u => u.isSuperAdmin === f.isSuperAdmin)
  return list
})

const paginatedList = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return filteredList.value.slice(start, start + pageSize.value)
})

function handleSearch() {
  page.value = 1
}

function handleReset() {
  filter.value = { username: '', email: '', status: '', isSuperAdmin: '' }
  page.value = 1
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

// 分配模块角色
const assignDialogVisible = ref(false)
const assignTargetUser = ref<UserManageVO | null>(null)
const assignModules = ref<{ moduleId: number; moduleName: string; roleId: number; roleName: string; defaultEntry?: boolean }[]>([])

// 添加分配
const addAssignDialogVisible = ref(false)
const assignSubmitting = ref(false)
const assignFormRef = ref<any>(null)
const roleOptions = ref<RoleVO[]>([])
const assignForm = ref({ moduleId: undefined as number | undefined, roleId: undefined as number | undefined })
const assignRules = {
  moduleId: [{ required: true, message: () => t('settings.selectModule'), trigger: 'change' }],
  roleId: [{ required: true, message: () => t('settings.selectRole'), trigger: 'change' }],
}

// 未分配的模块选项（过滤掉已分配的）
const availableModuleOptions = ref<ModuleVO[]>([])

async function fetchList() {
  loading.value = true
  try {
    userList.value = await userManageApi.list()
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
      ElMessage.success(t('common.edit') + '成功')
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
    ElMessage.success(t('common.delete') + '成功')
    await fetchList()
  }).catch(() => {})
}

async function openAssignModules(row: UserManageVO) {
  assignTargetUser.value = row
  assignModules.value = (row.moduleRoles || []).map(r => ({
    moduleId: r.moduleId,
    moduleName: r.moduleName,
    roleId: r.roleId,
    roleName: r.roleName,
    defaultEntry: false,
  }))

  // 超级管理员默认拥有所有模块权限（未持久化的默认条目）
  if (row.isSuperAdmin === 1) {
    try {
      const [allModules, allRoles] = await Promise.all([moduleApi.list(), roleApi.list()])
      const adminRole = allRoles.find(r => r.code === 'MODULE_ADMIN')
      const assignedIds = new Set(assignModules.value.map(m => m.moduleId))
      for (const m of allModules) {
        if (!assignedIds.has(m.id)) {
          assignModules.value.push({
            moduleId: m.id,
            moduleName: m.name,
            roleId: adminRole?.id ?? -1,
            roleName: adminRole?.name ?? '模块管理员',
            defaultEntry: true,
          })
        }
      }
    } catch {}
  }

  assignDialogVisible.value = true
}

async function openAddAssignment() {
  assignForm.value = { moduleId: undefined, roleId: undefined }
  try {
    const [modules, roles] = await Promise.all([moduleApi.list(), roleApi.list()])
    roleOptions.value = roles
    // 过滤掉已分配的模块
    const assignedIds = new Set(assignModules.value.map(m => m.moduleId))
    availableModuleOptions.value = modules.filter(m => !assignedIds.has(m.id))
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
    const dto: AssignModuleRoleDTO = {
      userId: assignTargetUser.value.id,
      moduleId: assignForm.value.moduleId!,
      roleId: assignForm.value.roleId!,
    }
    await userManageApi.assignModuleRole(dto)
    ElMessage.success(t('settings.addAssignment') + '成功')
    addAssignDialogVisible.value = false
    // 刷新分配列表
    const updated = await userManageApi.getById(assignTargetUser.value.id)
    assignModules.value = (updated.moduleRoles || []).map(r => ({
      moduleId: r.moduleId,
      moduleName: r.moduleName,
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
  const item = assignModules.value[index]

  // 默认条目（超级管理员默认全模块）只从本地移除，不调 API
  if ((item as any).defaultEntry) {
    assignModules.value.splice(index, 1)
    return
  }

  try {
    await userManageApi.removeModuleRole(assignTargetUser.value.id, item.moduleId)
    ElMessage.success(t('common.delete') + '成功')
    assignModules.value.splice(index, 1)
    // 同步更新 userList
    const idx = userList.value.findIndex(u => u.id === assignTargetUser.value!.id)
    if (idx !== -1) {
      userList.value[idx].moduleRoles = userList.value[idx].moduleRoles?.filter(
        r => r.moduleId !== item.moduleId,
      )
    }
  } catch {}
}

async function handleResetPassword(row: UserManageVO) {
  try {
    await ElMessageBox.confirm(
      `确定要重置用户「${row.username}」的密码吗？重置后系统将自动生成新密码。`,
      '确认重置密码',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' },
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
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.stats-summary {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}

.assign-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-bottom: 4px;
  border-bottom: 1px solid #ebeef5;
  font-size: 14px;
}
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
