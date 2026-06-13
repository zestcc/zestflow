<template>
  <div class="sso-callback-page">
    <p>{{ message }}</p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const message = ref('正在完成 SSO 登录...')

onMounted(async () => {
  const code = route.query.code as string
  const state = route.query.state as string
  if (!code || !state) {
    message.value = 'SSO 回调参数缺失'
    ElMessage.error(message.value)
    router.replace({ name: 'Login' })
    return
  }
  try {
    await userStore.loginBySso({ code, state })
    ElMessage.success('SSO 登录成功')
    router.replace((route.query.redirect as string) || '/dashboard')
  } catch {
    message.value = 'SSO 登录失败'
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
