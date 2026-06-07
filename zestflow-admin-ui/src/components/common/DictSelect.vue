<template>
  <el-select
    v-if="mode === 'strict'"
    :model-value="modelValue"
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    :class="selectClass"
    filterable
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-option
      v-for="item in dictOptions"
      :key="item.value"
      :label="item.label"
      :value="item.bindValue"
    />
  </el-select>
  <ComboboxInput
    v-else
    v-model="innerValue"
    :suggestions="suggestionStrings"
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    :class="selectClass"
  />
</template>

<script setup lang="ts">
import { computed, type PropType } from 'vue'
import ComboboxInput from '@/components/common/ComboboxInput.vue'
import { useDictCascade } from '@/composables/useDictCascade'
import { dictOptionValue } from '@/composables/useDictLabel'

export type DictSelectMode = 'strict' | 'allowCustom'

const props = defineProps({
  modelValue: { type: [String, Number] as PropType<string | number | undefined>, default: undefined },
  typeCode: { type: String, required: true },
  parentTypeCode: { type: String, default: undefined },
  parentValue: { type: String, default: undefined },
  mode: { type: String as PropType<DictSelectMode>, default: 'strict' },
  placeholder: { type: String, default: '' },
  clearable: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false },
  selectClass: { type: String, default: '' },
})

const emit = defineEmits<{
  'update:modelValue': [value: string | number | undefined]
}>()

const { options } = useDictCascade(
  () => props.typeCode,
  () => props.parentTypeCode,
  () => props.parentValue,
)

const dictOptions = computed(() =>
  options.value.map(o => ({
    ...o,
    bindValue: dictOptionValue(o.value),
  })),
)

const suggestionStrings = computed(() => dictOptions.value.map(o => o.label))

const innerValue = computed({
  get: () => (props.modelValue === undefined || props.modelValue === null ? '' : String(props.modelValue)),
  set: (v: string) => {
    if (!v) {
      emit('update:modelValue', undefined)
      return
    }
    const matched = dictOptions.value.find(o => o.value === v || o.label === v)
    if (matched) {
      emit('update:modelValue', matched.bindValue)
      return
    }
    if (props.mode === 'allowCustom') {
      emit('update:modelValue', v)
    }
  },
})
</script>
