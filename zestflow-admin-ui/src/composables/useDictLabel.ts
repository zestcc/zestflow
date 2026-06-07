import { computed } from 'vue'
import { useDict } from '@/composables/useDict'
import type { DictDataVO } from '@/api/dict'

/** 字典 value 转 el-option / v-model 绑定值（数字型字典项转为 number） */
export function dictOptionValue(value: string): string | number {
  if (/^-?\d+$/.test(value)) {
    return Number(value)
  }
  return value
}

/** 加载字典并提供 label / tagType 解析（表格展示、筛选下拉等） */
export function useDictLabel(typeCode: string) {
  const { options, refresh } = useDict(typeCode)

  const labelOf = (value: string | number | undefined | null): string => {
    if (value === undefined || value === null || value === '') {
      return '-'
    }
    const sv = String(value)
    const found = options.value.find(o => o.value === sv)
    return found?.label ?? sv
  }

  const tagTypeOf = (value: string | number | undefined | null): string => {
    if (value === undefined || value === null || value === '') {
      return 'info'
    }
    const sv = String(value)
    return options.value.find(o => o.value === sv)?.tagType ?? 'info'
  }

  const dictOptions = computed(() =>
    options.value.map((o: DictDataVO) => ({
      ...o,
      bindValue: dictOptionValue(o.value),
    })),
  )

  return { options, dictOptions, labelOf, tagTypeOf, refresh }
}
