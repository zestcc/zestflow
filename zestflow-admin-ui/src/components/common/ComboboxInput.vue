<template>
  <el-autocomplete
    :model-value="modelValue"
    :fetch-suggestions="fetchSuggestions"
    :placeholder="placeholder"
    :clearable="clearable"
    :disabled="disabled"
    class="combobox-input"
    value-key="value"
    @update:model-value="emit('update:modelValue', $event)"
    @select="onSelect"
  >
    <template v-if="$slots.prepend" #prepend>
      <slot name="prepend" />
    </template>
    <template v-if="$slots.append" #append>
      <slot name="append" />
    </template>
  </el-autocomplete>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    modelValue?: string
    suggestions?: string[]
    placeholder?: string
    clearable?: boolean
    disabled?: boolean
  }>(),
  {
    modelValue: '',
    suggestions: () => [],
    placeholder: '',
    clearable: true,
    disabled: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

function fetchSuggestions(query: string, cb: (items: { value: string }[]) => void) {
  const q = query.trim().toLowerCase()
  const list = props.suggestions ?? []
  const filtered = q
    ? list.filter(s => s.toLowerCase().includes(q))
    : list
  const values = new Set<string>()
  if (props.modelValue?.trim()) {
    values.add(props.modelValue.trim())
  }
  filtered.forEach(s => values.add(s))
  cb([...values].map(value => ({ value })))
}

function onSelect(item: { value: string }) {
  emit('update:modelValue', item.value)
}
</script>

<style scoped>
.combobox-input {
  width: 100%;
}
</style>
