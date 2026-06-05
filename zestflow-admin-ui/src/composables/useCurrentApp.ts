import { computed } from 'vue'
import { useAppStore } from '@/stores/app'
import type { AppOption } from '@/api/executor'

/** 全局当前应用选择：切换时持久化，进入各业务页时自动恢复 */
export function useCurrentApp() {
  const appStore = useAppStore()

  const currentAppCode = computed({
    get: () => appStore.currentAppCode,
    set: (code: string) => appStore.setCurrentAppCode(code),
  })

  function syncFromApps(apps: AppOption[], prefer?: string): string {
    return appStore.syncAppCode(apps, prefer)
  }

  return {
    currentAppCode,
    syncFromApps,
    setCurrentAppCode: appStore.setCurrentAppCode,
  }
}
