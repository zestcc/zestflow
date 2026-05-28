<template>
  <div class="register-page">
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
    <div class="lang-bar">
      <el-dropdown trigger="click" @command="switchLang">
        <span class="lang-btn">{{ locale === 'zh-CN' ? '中文' : 'EN' }}</span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item :disabled="locale === 'zh-CN'" command="zh-CN">简体中文</el-dropdown-item>
            <el-dropdown-item :disabled="locale === 'en'" command="en">English</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
    <div class="auth-card">
      <div class="card-logo">
        <img src="/favicon.svg" alt="Z" />
      </div>
      <h2 class="card-title">{{ $t('register.title') }}</h2>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        size="large"
        @keyup.enter="handleRegister"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" :placeholder="$t('common.username')" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="email">
          <el-input v-model="form.email" :placeholder="$t('common.email')" :prefix-icon="Message" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="$t('common.password')"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            :placeholder="$t('common.confirmPassword')"
            show-password
            :prefix-icon="Lock"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" class="btn-action" @click="handleRegister">
            {{ loading ? $t('register.registering') : $t('register.registerBtn') }}
          </el-button>
        </el-form-item>
      </el-form>
      <div class="links">
        <span>{{ $t('register.hasAccount') }}</span>
        <router-link to="/login">{{ $t('register.backLink') }}</router-link>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Message, Lock } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useLocale } from '@/i18n/useLocale'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const { locale, setLocale } = useLocale()

function switchLang(lang: string) {
  setLocale(lang)
}
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({
  username: '',
  email: '',
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
  username: [{ required: true, message: () => t('register.usernameRequired'), trigger: 'blur' }],
  email: [
    { required: true, message: () => t('register.emailRequired'), trigger: 'blur' },
    { type: 'email', message: () => t('validation.emailFormat'), trigger: 'blur' },
  ],
  password: [
    { required: true, message: () => t('register.passwordRequired'), trigger: 'blur' },
    { min: 6, message: () => t('validation.passwordMin'), trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: () => t('register.confirmRequired'), trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' },
  ],
}

async function handleRegister() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.register({
      username: form.username,
      email: form.email,
      password: form.password,
    })
    ElMessage.success(t('register.registerSuccess'))
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-page {
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

.lang-bar {
  position: absolute; top: 24px; right: 32px; z-index: 10;
}
.lang-btn {
  cursor: pointer; color: rgba(255,255,255,0.8); font-size: 14px;
  padding: 4px 12px; border: 1px solid rgba(255,255,255,0.25);
  border-radius: 4px; background: rgba(255,255,255,0.08); transition: all 0.2s;
}
.lang-btn:hover {
  background: rgba(255,255,255,0.15); border-color: rgba(255,255,255,0.4);
}

.auth-card {
  width: 420px; padding: 40px 40px 32px;
  background: #fff; border-radius: 16px;
  box-shadow: 0 8px 40px rgba(0,0,0,0.15);
  position: relative; z-index: 1;
}
.card-logo { text-align: center; margin-bottom: 16px; }
.card-logo img { width: 48px; height: 48px; }
.card-title {
  text-align: center; margin: 0 0 28px;
  font-size: 22px; color: #1d2129; font-weight: 600;
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

.links {
  text-align: center; font-size: 14px; color: #86909c; margin-top: 16px;
}
.links a { color: #667eea; text-decoration: none; margin-left: 4px; }
.links a:hover { color: #5a6fd6; }
</style>
