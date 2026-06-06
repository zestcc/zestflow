import { computed, onMounted, onUnmounted, ref } from 'vue'

const MOBILE_BREAKPOINT = 768

/**
 * 抽屉宽度：移动端全屏，桌面端固定像素。
 */
export function useResponsiveDrawerSize(desktopSize: number | string = 480) {
  const isMobile = ref(false)

  function checkMobile() {
    isMobile.value = window.innerWidth < MOBILE_BREAKPOINT
  }

  onMounted(() => {
    checkMobile()
    window.addEventListener('resize', checkMobile)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', checkMobile)
  })

  const drawerSize = computed(() => (isMobile.value ? '100%' : desktopSize))

  return { drawerSize, isMobile }
}
