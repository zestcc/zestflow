<template>
  <div class="force-pwd-page">
    <div class="bg-decor">
      <div class="bg-grid"></div>
      <div class="bg-glow"></div>
      <div class="bg-flow tl">
        <svg viewBox="0 0 120 80" class="flow-svg">
          <g stroke="rgba(255,255,255,0.08)" stroke-width="1" fill="none">
            <path d="M15,40 L50,40 L50,15 L85,15"/>
            <path d="M50,40 L50,65 L85,65"/>
          </g>
          <g>
            <circle cx="15" cy="40" r="5" fill="rgba(255,255,255,0.06)"/>
            <rect x="78" y="8" width="14" height="14" rx="3" fill="rgba(255,255,255,0.06)"/>
            <rect x="78" y="58" width="14" height="14" rx="3" fill="rgba(255,255,255,0.04)"/>
          </g>
        </svg>
      </div>
      <div class="bg-flow tr">
        <svg viewBox="0 0 100 70" class="flow-svg">
          <g stroke="rgba(255,255,255,0.08)" stroke-width="1" fill="none">
            <path d="M10,35 L45,35 L45,15 L80,15"/>
            <path d="M45,35 L45,55 L80,55"/>
          </g>
          <g>
            <circle cx="10" cy="35" r="4" fill="rgba(255,255,255,0.05)"/>
            <rect x="74" y="8" width="12" height="14" rx="3" fill="rgba(255,255,255,0.06)"/>
            <rect x="74" y="48" width="12" height="14" rx="3" fill="rgba(255,255,255,0.04)"/>
          </g>
        </svg>
      </div>
      <div class="bg-flow ml">
        <svg viewBox="0 0 90 110" class="flow-svg">
          <g stroke="rgba(255,255,255,0.07)" stroke-width="1" fill="none">
            <path d="M15,15 L15,55 L50,55"/>
            <path d="M15,55 L15,95 L50,95"/>
            <path d="M15,35 L50,35"/>
          </g>
          <g>
            <circle cx="15" cy="15" r="4" fill="rgba(255,255,255,0.05)"/>
            <circle cx="15" cy="55" r="3" fill="rgba(255,255,255,0.04)"/>
            <circle cx="15" cy="95" r="3" fill="rgba(255,255,255,0.04)"/>
            <rect x="44" y="48" width="12" height="14" rx="3" fill="rgba(255,255,255,0.05)"/>
            <rect x="44" y="88" width="12" height="14" rx="3" fill="rgba(255,255,255,0.04)"/>
            <rect x="44" y="28" width="12" height="14" rx="3" fill="rgba(255,255,255,0.05)"/>
          </g>
        </svg>
      </div>
      <div class="bg-flow mr">
        <svg viewBox="0 0 80 100" class="flow-svg">
          <g stroke="rgba(255,255,255,0.07)" stroke-width="1" fill="none">
            <path d="M10,20 L10,50 L45,50"/>
            <path d="M10,80 L10,50"/>
          </g>
          <g>
            <circle cx="10" cy="20" r="3.5" fill="rgba(255,255,255,0.05)"/>
            <circle cx="10" cy="80" r="3" fill="rgba(255,255,255,0.04)"/>
            <rect x="39" y="43" width="12" height="14" rx="3" fill="rgba(255,255,255,0.05)"/>
          </g>
        </svg>
      </div>
      <div class="bg-flow br">
        <svg viewBox="0 0 180 120" class="flow-svg">
          <g stroke="rgba(255,255,255,0.12)" stroke-width="1.2" fill="none">
            <path d="M25,25 L65,25 L65,60 L105,60"/>
            <path d="M25,25 L25,95 L65,95 L65,60"/>
            <path d="M105,60 L145,60 L145,25"/>
            <path d="M105,60 L105,95 L145,95"/>
          </g>
          <g>
            <circle cx="25" cy="25" r="8" fill="rgba(255,255,255,0.08)"/>
            <circle cx="25" cy="95" r="6" fill="rgba(255,255,255,0.05)"/>
            <rect x="93" y="50" width="24" height="20" rx="5" fill="rgba(255,255,255,0.08)"/>
            <rect x="133" y="15" width="20" height="20" rx="5" fill="rgba(255,255,255,0.05)"/>
            <rect x="133" y="85" width="20" height="20" rx="5" fill="rgba(255,255,255,0.05)"/>
          </g>
        </svg>
      </div>
      <div class="bg-flow bl">
        <svg viewBox="0 0 130 90" class="flow-svg">
          <g stroke="rgba(255,255,255,0.09)" stroke-width="1" fill="none">
            <path d="M15,45 L50,45 L50,20 L85,20"/>
            <path d="M50,45 L50,70 L85,70"/>
            <path d="M15,45 L15,20 L50,20"/>
          </g>
          <g>
            <circle cx="15" cy="45" r="6" fill="rgba(255,255,255,0.06)"/>
            <circle cx="15" cy="20" r="4" fill="rgba(255,255,255,0.04)"/>
            <rect x="76" y="12" width="16" height="16" rx="4" fill="rgba(255,255,255,0.06)"/>
            <rect x="76" y="62" width="16" height="16" rx="4" fill="rgba(255,255,255,0.04)"/>
          </g>
        </svg>
      </div>
    </div>

    <div class="force-card">
      <div class="card-logo">
        <img src="/favicon.svg" alt="Z" />
      </div>
      <h2 class="card-title">{{ $t('forcePassword.title') }}</h2>
      <p class="card-subtitle">{{ $t('forcePassword.subtitle') }}</p>
      <div class="username-display">
        <el-icon :size="16"><User /></el-icon>
        <span>{{ userStore.user?.username }}</span>
      </div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        size="large"
        @keyup.enter="handleSubmit"
      >
        <el-form-item prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            :placeholder="$t('forcePassword.newPasswordPlaceholder')"
            show-password
            autocomplete="new-password"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            :placeholder="$t('forcePassword.confirmPlaceholder')"
            show-password
            autocomplete="new-password"
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="btn-submit" @click="handleSubmit">
            {{ loading ? $t('forcePassword.submitting') : $t('forcePassword.submit') }}
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ newPassword: '', confirmPassword: '' })

const validateConfirm = (_rule: any, value: string, callback: any) => {
  if (value !== form.newPassword) {
    callback(new Error(t('validation.passwordMismatch')))
  } else {
    callback()
  }
}

const rules: FormRules = {
  newPassword: [
    { required: true, message: () => t('forcePassword.newPasswordRequired'), trigger: 'blur' },
    { min: 6, message: () => t('validation.passwordMin'), trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: () => t('forcePassword.confirmRequired'), trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authApi.forceChangePassword(form.newPassword)
    ElMessage.success(t('forcePassword.success'))
    const savedUsername = userStore.user?.username || ''
    userStore.clearMustChangePassword()
    userStore.token = ''
    userStore.user = null
    userStore.mustChangePassword = false
    localStorage.removeItem('token')
    router.replace({ name: 'Login', query: { username: savedUsername, t: Date.now().toString() } })
  } catch {
    // 错误已由 API 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.force-pwd-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #4a5fc1 0%, #667eea 35%, #7c5cbf 70%, #764ba2 100%);
  position: relative;
  overflow: hidden;
}

.bg-decor { position: absolute; inset: 0; pointer-events: none; }
.bg-grid {
  position: absolute; inset: 0;
  background-image:
    linear-gradient(rgba(255,255,255,0.04) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.04) 1px, transparent 1px);
  background-size: 40px 40px;
}
.bg-glow {
  position: absolute;
  top: -20%; left: 50%; transform: translateX(-50%);
  width: 700px; height: 700px;
  background: radial-gradient(circle, rgba(255,255,255,0.1) 0%, transparent 60%);
  border-radius: 50%;
}
.bg-flow { position: absolute; }
.bg-flow.tl { top: 40px; left: 50px; width: 100px; }
.bg-flow.tr { top: 130px; right: 70px; width: 85px; }
.bg-flow.ml { top: 38%; left: 30px; width: 95px; }
.bg-flow.mr { top: 52%; right: 50px; width: 75px; }
.bg-flow.bl { bottom: 60px; left: 40px; width: 110px; }
.bg-flow.br { bottom: 30px; right: 80px; width: 130px; }
.flow-svg { width: 100%; height: auto; }

.force-card {
  width: 400px;
  padding: 40px 40px 32px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 8px 40px rgba(0, 0, 0, 0.15);
  position: relative;
  z-index: 1;
}
.card-logo { text-align: center; margin-bottom: 16px; }
.card-logo img { width: 48px; height: 48px; }
.card-title {
  text-align: center; margin: 0 0 4px;
  font-size: 22px; color: #1d2129; font-weight: 600;
}
.card-subtitle {
  text-align: center; margin: 0 0 28px;
  font-size: 14px; color: #86909c;
}
.username-display {
  display: flex; align-items: center; justify-content: center; gap: 6px;
  margin-bottom: 20px; font-size: 15px; color: #303133; font-weight: 500;
}

.force-card :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #d9dde3, inset 0 0 0 1000px #fff !important;
  border-radius: 8px;
}
.force-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #667eea, inset 0 0 0 1000px #fff !important;
}
.force-card :deep(.el-input__inner) { caret-color: #333; }
.force-card :deep(.el-input__inner:-webkit-autofill),
.force-card :deep(.el-input__inner:-webkit-autofill:hover),
.force-card :deep(.el-input__inner:-webkit-autofill:focus) {
  -webkit-box-shadow: 0 0 0 1000px #fff inset !important;
  box-shadow: 0 0 0 1000px #fff inset !important;
  -webkit-text-fill-color: #333 !important;
}
.force-card :deep(.el-input__prefix-inner .el-icon) { color: #c9cdd4; }

.btn-submit {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 8px;
}

@media (max-width: 767px) {
  .force-card {
    width: 100%;
    padding: 28px 20px 24px;
    border-radius: 12px;
  }
}
</style>
