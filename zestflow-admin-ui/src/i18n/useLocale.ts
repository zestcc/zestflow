import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import zhCn from 'element-plus/dist/locale/zh-cn.mjs'
import en from 'element-plus/dist/locale/en.mjs'

const localeMap: Record<string, any> = {
  'zh-CN': zhCn,
  'en': en,
}

export function useLocale() {
  const { locale } = useI18n()

  const elementLocale = computed(() => localeMap[locale.value] || zhCn)

  function setLocale(lang: string) {
    locale.value = lang
    localStorage.setItem('locale', lang)
  }

  return {
    locale,
    elementLocale,
    setLocale,
  }
}
