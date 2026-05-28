<template>
  <div class="profile-page">
    <!-- 页面头部 -->
    <div class="page-header">
      <h2>{{ $t('profile.title') }}</h2>
    </div>

    <el-row :gutter="24">
      <!-- 左列 -->
      <el-col :span="7">
        <el-card shadow="never" class="info-card">
          <div class="avatar-box">
            <div class="avatar-wrap" @click="triggerUpload">
              <el-avatar :size="100" :src="avatarUrl">
                {{ userStore.user?.username?.charAt(0)?.toUpperCase() }}
              </el-avatar>
              <div class="avatar-overlay">
                <el-icon :size="24"><Camera /></el-icon>
              </div>
            </div>
            <div class="avatar-name">{{ userStore.user?.username }}</div>
            <div class="avatar-role">管理员</div>
          </div>
          <el-divider style="margin: 16px 0" />
          <div class="meta-list">
            <div class="meta-item">
              <span class="meta-label">ID</span>
              <span class="meta-value">{{ userStore.user?.id }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">{{ $t('common.email') }}</span>
              <span class="meta-value">{{ userStore.user?.email }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 右列 -->
      <el-col :span="17">
        <el-card shadow="never" class="section-card">
          <div class="section-title">
            <el-icon :size="18"><EditPen /></el-icon>
            <span>{{ $t('profile.basicInfo') }}</span>
          </div>
          <el-form :model="profileForm" label-width="90px" ref="profileFormRef" :rules="profileRules" class="profile-form">
            <el-form-item :label="$t('common.username')" prop="username">
              <el-input v-model="profileForm.username" />
            </el-form-item>
            <el-form-item :label="$t('common.email')" prop="email">
              <el-input v-model="profileForm.email" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="profileLoading" @click="handleUpdateProfile">
                {{ $t('common.save') }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="section-card">
          <div class="section-title">
            <el-icon :size="18"><Lock /></el-icon>
            <span>{{ $t('profile.changePassword') }}</span>
          </div>
          <el-form :model="pwdForm" label-width="90px" ref="pwdFormRef" :rules="pwdRules" class="profile-form">
            <el-form-item :label="$t('profile.oldPassword')" prop="oldPassword">
              <el-input v-model="pwdForm.oldPassword" type="password" show-password />
            </el-form-item>
            <el-form-item :label="$t('profile.newPassword')" prop="newPassword">
              <el-input v-model="pwdForm.newPassword" type="password" show-password />
            </el-form-item>
            <el-form-item :label="$t('profile.confirmNewPassword')" prop="confirmPassword">
              <el-input v-model="pwdForm.confirmPassword" type="password" show-password />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="pwdLoading" @click="handleUpdatePassword">
                {{ $t('common.save') }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <input ref="fileInputRef" type="file" accept="image/*" hidden @change="handleFileChange" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Camera, EditPen, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'
import { authApi } from '@/api/auth'

const { t } = useI18n()
const userStore = useUserStore()

const fileInputRef = ref<HTMLInputElement>()
const profileFormRef = ref<FormInstance>()
const pwdFormRef = ref<FormInstance>()
const profileLoading = ref(false)
const pwdLoading = ref(false)

const avatarUrl = computed(() => {
  const avatar = userStore.user?.avatar
  if (!avatar) return ''
  if (avatar.startsWith('http')) return avatar
  return avatar
})

const profileForm = reactive({ username: '', email: '' })

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const profileRules: FormRules = {
  username: [{ required: true, message: () => t('register.usernameRequired'), trigger: 'blur' }],
  email: [
    { required: true, message: () => t('register.emailRequired'), trigger: 'blur' },
    { type: 'email', message: () => t('validation.emailFormat'), trigger: 'blur' },
  ],
}

const validateConfirm = (_rule: any, value: string, callback: any) => {
  if (value !== pwdForm.newPassword) {
    callback(new Error(t('profile.passwordMismatch')))
  } else {
    callback()
  }
}

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: () => t('profile.oldPasswordRequired'), trigger: 'blur' }],
  newPassword: [
    { required: true, message: () => t('profile.newPasswordRequired'), trigger: 'blur' },
    { min: 6, message: () => t('validation.passwordMin'), trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: () => t('profile.confirmRequired'), trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

onMounted(() => {
  if (userStore.user) {
    profileForm.username = userStore.user.username
    profileForm.email = userStore.user.email
  }
})

async function handleUpdateProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid) return

  profileLoading.value = true
  try {
    await userStore.updateProfile({
      username: profileForm.username,
      email: profileForm.email,
    })
    ElMessage.success(t('profile.updateSuccess'))
  } finally {
    profileLoading.value = false
  }
}

async function handleUpdatePassword() {
  const valid = await pwdFormRef.value?.validate().catch(() => false)
  if (!valid) return

  pwdLoading.value = true
  try {
    await authApi.updatePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    ElMessage.success(t('profile.passwordSuccess'))
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
  } finally {
    pwdLoading.value = false
  }
}

function triggerUpload() {
  fileInputRef.value?.click()
}

async function handleFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  const file = target.files?.[0]
  if (!file) return

  try {
    await authApi.uploadAvatar(file)
    await userStore.getUserInfo()
    ElMessage.success(t('profile.avatarSuccess'))
  } catch {
    // 错误由拦截器统一处理
  }
}
</script>

<style scoped>
.profile-page {
  max-width: 960px;
}

.page-header h2 {
  margin: 0 0 24px;
  font-size: 22px;
  color: #303133;
  font-weight: 600;
}

/* 左侧卡片 */
.info-card {
  border-radius: 10px;
  border: 1px solid #ebeef5;
}

.avatar-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 8px;
}

.avatar-wrap {
  position: relative;
  cursor: pointer;
  border-radius: 50%;
  overflow: hidden;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
  color: #fff;
  opacity: 0;
  transition: opacity 0.25s;
  border-radius: 50%;
}

.avatar-wrap:hover .avatar-overlay {
  opacity: 1;
}

.avatar-name {
  margin-top: 12px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.avatar-role {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  background: #f0f2f5;
  padding: 2px 12px;
  border-radius: 10px;
}

.meta-list {
  padding: 0 4px 4px;
}

.meta-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 13px;
}

.meta-label {
  color: #909399;
}

.meta-value {
  color: #303133;
}

/* 右侧卡片 */
.section-card {
  border-radius: 10px;
  border: 1px solid #ebeef5;
  margin-bottom: 16px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  padding-bottom: 4px;
}

.profile-form {
  margin-top: 8px;
}
</style>
