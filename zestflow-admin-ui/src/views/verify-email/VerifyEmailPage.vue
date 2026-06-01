<template>
  <div class="verify-page">
    <div class="auth-card">
      <div class="card-logo">
        <img src="/favicon.svg" alt="Z" />
      </div>

      <!-- 验证中 -->
      <template v-if="status === 'loading'">
        <h2 class="card-title">{{ $t('verifyEmail.verifying') }}</h2>
        <div class="loading-spinner">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        </div>
      </template>

      <!-- 验证成功 -->
      <template v-else-if="status === 'success'">
        <h2 class="card-title">{{ $t('verifyEmail.success') }}</h2>
        <p class="card-desc">{{ $t('verifyEmail.successMsg') }}</p>
        <router-link to="/login" class="btn-link">{{ $t('verifyEmail.goToLogin') }}</router-link>
      </template>

      <!-- 验证失败 -->
      <template v-else-if="status === 'error'">
        <h2 class="card-title">{{ $t('verifyEmail.failed') }}</h2>
        <p class="card-desc">{{ errorMsg || $t('verifyEmail.failedMsg') }}</p>
        <router-link to="/login" class="btn-link">{{ $t('common.backToLogin') }}</router-link>
      </template>

      <!-- 缺少 token -->
      <template v-else>
        <h2 class="card-title">{{ $t('verifyEmail.invalid') }}</h2>
        <p class="card-desc">{{ $t('verifyEmail.tokenMissing') }}</p>
        <router-link to="/login" class="btn-link">{{ $t('common.backToLogin') }}</router-link>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { authApi } from '@/api/auth'

const { t } = useI18n()
const route = useRoute()

const status = ref<'loading' | 'success' | 'error' | 'missing'>('loading')
const errorMsg = ref('')

onMounted(async () => {
  const token = route.query.token as string
  if (!token) {
    status.value = 'missing'
    return
  }

  try {
    await authApi.verifyEmail(token)
    status.value = 'success'
    setTimeout(() => {
      window.location.href = '/login'
    }, 3000)
  } catch (e: any) {
    status.value = 'error'
    errorMsg.value = e?.response?.data?.message || t('verifyEmail.failedMsg')
  }
})
</script>

<style scoped>
.verify-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #4a5fc1 0%, #667eea 35%, #7c5cbf 70%, #764ba2 100%);
}

.auth-card {
  width: 420px; padding: 48px 40px;
  background: #fff; border-radius: 16px;
  box-shadow: 0 8px 40px rgba(0,0,0,0.15);
  text-align: center;
}
.card-logo { margin-bottom: 20px; }
.card-logo img { width: 48px; height: 48px; }
.card-title {
  margin: 0 0 8px;
  font-size: 22px; color: #1d2129; font-weight: 600;
}
.card-desc {
  margin: 0 0 24px; font-size: 14px; color: #86909c;
}
.loading-spinner { padding: 24px 0; color: #667eea; }
.btn-link {
  display: inline-block;
  padding: 8px 24px;
  background: linear-gradient(135deg, #4a5fc1, #667eea);
  color: #fff; text-decoration: none; border-radius: 6px; font-size: 14px;
}
.btn-link:hover { opacity: 0.9; }
</style>
