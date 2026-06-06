import { computed, onMounted, onUnmounted, ref } from 'vue'

const MOBILE_BREAKPOINT = 768

/** 移动端简化分页 layout，避免 sizes/jumper 挤爆窄屏 */
export function useResponsivePagination(desktopLayout = 'total, sizes, prev, pager, next, jumper') {
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

  const paginationLayout = computed(() =>
    isMobile.value ? 'total, prev, pager, next' : desktopLayout,
  )

  return { paginationLayout, isMobile }
}
