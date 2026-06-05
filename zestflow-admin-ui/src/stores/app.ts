import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getFeatures } from '@/api/system'
import { executorApi } from '@/api/executor'

const APP_CODE_STORAGE_KEY = 'currentAppCode'

function readStoredAppCode(): string {
  try {
    return localStorage.getItem(APP_CODE_STORAGE_KEY) || ''
  } catch {
    return ''
  }
}

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const playgroundEnabled = ref(false)
  const hasOnlineApps = ref(false)
  /** 全局当前应用编码，切换后写入 localStorage，各业务页进入时复用 */
  const currentAppCode = ref(readStoredAppCode())

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  async function fetchFeatures() {
    try {
      const res: any = await getFeatures()
      playgroundEnabled.value = res.playground?.enabled === true
    } catch {
      playgroundEnabled.value = false
    }
  }

  async function fetchOnlineApps() {
    try {
      const apps = await executorApi.listApps(true)
      hasOnlineApps.value = apps.length > 0
    } catch {
      hasOnlineApps.value = false
    }
  }

  function setCurrentAppCode(code: string) {
    if (!code) return
    currentAppCode.value = code
    try {
      localStorage.setItem(APP_CODE_STORAGE_KEY, code)
    } catch {
      /* ignore */
    }
  }

  function clearCurrentAppCode() {
    currentAppCode.value = ''
    try {
      localStorage.removeItem(APP_CODE_STORAGE_KEY)
    } catch {
      /* ignore */
    }
  }

  /** 解析并记住 appCode：路由/显式优先 > 已存储 > 列表首项 */
  function syncAppCode(apps: { appCode: string }[], prefer?: string): string {
    if (!apps.length) return ''
    const codes = new Set(apps.map(a => a.appCode))
    let code = ''
    if (prefer && codes.has(prefer)) {
      code = prefer
    } else if (currentAppCode.value && codes.has(currentAppCode.value)) {
      code = currentAppCode.value
    } else {
      code = apps[0].appCode
    }
    setCurrentAppCode(code)
    return code
  }

  return {
    sidebarCollapsed,
    playgroundEnabled,
    hasOnlineApps,
    currentAppCode,
    toggleSidebar,
    fetchFeatures,
    fetchOnlineApps,
    setCurrentAppCode,
    clearCurrentAppCode,
    syncAppCode,
  }
})
