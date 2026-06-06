<template>
  <div class="reset-password-page">
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
    <div class="auth-card">
      <div class="card-logo">
        <img src="/favicon.svg" alt="Z" />
      </div>

      <!-- Token 缺失 -->
      <template v-if="!token">
        <h2 class="card-title">{{ $t('resetPassword.invalidTitle') }}</h2>
        <p class="card-desc">{{ $t('resetPassword.tokenMissing') }}</p>
        <div class="links">
          <router-link to="/forgot">{{ $t('resetPassword.backToForgot') }}</router-link>
        </div>
      </template>

      <!-- 表单 -->
      <template v-else-if="!submitted">
        <h2 class="card-title">{{ $t('resetPassword.title') }}</h2>
        <p class="card-desc">{{ $t('resetPassword.description') }}</p>
        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-width="0"
          size="large"
          @keyup.enter="handleSubmit"
        >
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              :placeholder="$t('resetPassword.newPasswordPlaceholder')"
            />
          </el-form-item>
          <el-form-item prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              :placeholder="$t('resetPassword.confirmPasswordPlaceholder')"
            />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="loading" class="btn-action" @click="handleSubmit">
              {{ loading ? $t('resetPassword.submitting') : $t('resetPassword.submitBtn') }}
            </el-button>
          </el-form-item>
        </el-form>
        <div class="links">
          <router-link to="/login">{{ $t('common.backToLogin') }}</router-link>
        </div>
      </template>

      <!-- 成功 -->
      <template v-else>
        <h2 class="card-title">{{ $t('resetPassword.successTitle') }}</h2>
        <p class="card-desc">{{ $t('resetPassword.success') }}</p>
        <div class="links" style="margin-top:20px;">
          <router-link to="/login">{{ $t('resetPassword.goToLogin') }}</router-link>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const token = computed(() => (route.query.token as string) || '')

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitted = ref(false)
const form = reactive({
  password: '',
  confirmPassword: '',
})

const validateConfirm = (_rule: any, value: string, callback: any) => {
  if (value !== form.password) {
    callback(new Error(t('validation.passwordMismatch')))
  } else {
    callback()
  }
}

const rules: FormRules = {
  password: [
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
    await authApi.resetPassword({ token: token.value, password: form.password })
    submitted.value = true
    setTimeout(() => {
      router.push('/login')
    }, 3000)
  } catch {
    // 错误由 Axios 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.reset-password-page {
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

.auth-card {
  width: 400px; padding: 40px 40px 32px;
  background: #fff; border-radius: 16px;
  box-shadow: 0 8px 40px rgba(0,0,0,0.15);
  position: relative; z-index: 1;
}
.card-logo { text-align: center; margin-bottom: 16px; }
.card-logo img { width: 48px; height: 48px; }
.card-title {
  text-align: center; margin: 0 0 6px;
  font-size: 22px; color: #1d2129; font-weight: 600;
}
.card-desc {
  text-align: center; margin: 0 0 28px; font-size: 14px; color: #86909c;
}

.auth-card :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #d9dde3, inset 0 0 0 1000px #fff !important;
  border-radius: 8px;
}
.auth-card :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px #667eea, inset 0 0 0 1000px #fff !important;
}
.auth-card :deep(.el-input__inner) {
  caret-color: #333;
}
.auth-card :deep(.el-input__inner:-webkit-autofill),
.auth-card :deep(.el-input__inner:-webkit-autofill:hover),
.auth-card :deep(.el-input__inner:-webkit-autofill:focus) {
  -webkit-box-shadow: 0 0 0 1000px #fff inset !important;
  box-shadow: 0 0 0 1000px #fff inset !important;
  -webkit-text-fill-color: #333 !important;
}
.auth-card :deep(.el-input__prefix-inner .el-icon) {
  color: #c9cdd4;
}

.btn-action { width: 100%; height: 44px; font-size: 16px; border-radius: 8px; }

.links { text-align: center; font-size: 14px; margin-top: 16px; }
.links a { color: #667eea; text-decoration: none; }
.links a:hover { color: #5a6fd6; }

@media (max-width: 767px) {
  .auth-card {
    width: 100%;
    padding: 28px 20px 24px;
    border-radius: 12px;
  }
}
</style>
