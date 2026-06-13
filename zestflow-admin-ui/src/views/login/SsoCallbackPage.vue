<template>
  <div class="sso-callback-page">
    <p>{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { useUserStore } from '@/stores/user'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const message = ref(t('login.ssoCallbackProcessing'))

onMounted(async () => {
  const code = route.query.code as string
  const state = route.query.state as string
  if (!code || !state) {
    message.value = t('login.ssoCallbackMissingParams')
    ElMessage.error(message.value)
    router.replace({ name: 'Login' })
    return
  }
  try {
    await userStore.loginBySso({ code, state })
    ElMessage.success(t('login.ssoLoginSuccess'))
    router.replace((route.query.redirect as string) || '/dashboard')
  } catch {
    message.value = t('login.ssoLoginFailed')
    router.replace({ name: 'Login' })
  }
})
</script>

<style scoped>
.sso-callback-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #666;
}
</style>
