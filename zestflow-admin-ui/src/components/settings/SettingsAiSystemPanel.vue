<template>
  <div class="settings-ai-system">
    <el-alert type="info" :closable="false" show-icon :title="$t('settings.ai.system.hintTitle')">
      <template #default>
        <p>{{ $t('settings.ai.system.hintBody') }}</p>
      </template>
    </el-alert>

    <el-tabs v-model="subTab" class="system-sub-tabs">
      <el-tab-pane :label="$t('settings.ai.system.tabProviders')" name="providers">
        <ResponsiveTable
          :data="providers"
          :columns="providerColumns"
          :loading="loading"
          row-key="id"
          :show-actions="true"
          :actions-label="$t('common.actions')"
          :actions-width="100"
        >
          <template #status="{ row }">
            <el-tag :type="enableStatusTagType(row.status)" size="small">
              {{ enableStatusLabel(row.status) }}
            </el-tag>
          </template>
          <template #actions="{ row }">
            <el-button text type="primary" size="small" @click="openEdit(row)">{{ $t('common.edit') }}</el-button>
          </template>
        </ResponsiveTable>
      </el-tab-pane>

      <el-tab-pane :label="$t('settings.ai.system.tabModels')" name="models">
        <el-form inline size="default" class="responsive-filter-form" style="margin-bottom:12px">
          <el-form-item :label="$t('settings.ai.provider')">
            <el-select v-model="filterProvider" clearable filterable class="page-filter-control" @change="loadModels">
              <el-option
                v-for="p in providers"
                :key="p.value"
                :label="p.label"
                :value="p.value"
              />
            </el-select>
          </el-form-item>
        </el-form>
        <ResponsiveTable
          :data="models"
          :columns="modelColumns"
          :loading="modelLoading"
          row-key="id"
        >
          <template #defaultFlag="{ row }">
            <el-tag v-if="row.defaultFlag === 1" type="success" size="small">{{ $t('dict.yes') }}</el-tag>
            <span v-else>-</span>
          </template>
        </ResponsiveTable>
        <el-empty v-if="!filterProvider" :description="$t('settings.ai.system.selectProviderFirst')" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="editVisible" :title="$t('settings.ai.system.editProvider')" width="720px" destroy-on-close>
      <el-form :model="editForm" label-width="120px">
        <el-form-item :label="$t('dict.label')">
          <el-input v-model="editForm.label" />
        </el-form-item>
        <el-form-item :label="$t('dict.value')">
          <el-input v-model="editForm.value" disabled />
        </el-form-item>
        <AiProviderExtraForm
          ref="providerExtraFormRef"
          v-model="editForm.extra"
          @tier-change="onTierChange"
        />
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="saveProvider">{{ $t('common.save') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import ResponsiveTable from '@/components/ResponsiveTable.vue'
import AiProviderExtraForm from '@/components/settings/AiProviderExtraForm.vue'
import { useDictLabel } from '@/composables/useDictLabel'
import { tagTypeForAiProviderTier } from '@/utils/aiProviderExtra'
import { dictApi, type DictDataVO } from '@/api/dict'
import { useDictStore } from '@/stores/dict'

const AI_PROVIDER = 'ai_provider'
const AI_MODEL = 'ai_model'

const { t } = useI18n()
const dictStore = useDictStore()
const { labelOf: enableStatusLabel, tagTypeOf: enableStatusTagType } = useDictLabel('enable_status')

const subTab = ref('providers')
const loading = ref(false)
const modelLoading = ref(false)
const saving = ref(false)
const providers = ref<DictDataVO[]>([])
const models = ref<DictDataVO[]>([])
const filterProvider = ref('')
const editVisible = ref(false)
const editForm = ref({ id: 0, label: '', value: '', extra: '', tagType: '' })
const providerExtraFormRef = ref<{ syncToModel: () => string } | null>(null)

const emit = defineEmits<{ 'providers-changed': [] }>()

const providerColumns = computed(() => [
  { prop: 'value', label: t('dict.code'), width: 140, showOverflowTooltip: true },
  { prop: 'label', label: t('dict.label'), minWidth: 120, showOverflowTooltip: true },
  { prop: 'sort', label: t('dict.sort'), width: 70, align: 'center' as const },
  { prop: 'status', label: t('common.status'), width: 90, align: 'center' as const },
])

const modelColumns = computed(() => [
  { prop: 'value', label: t('settings.ai.model'), minWidth: 160, showOverflowTooltip: true },
  { prop: 'parentValue', label: t('settings.ai.provider'), width: 120, showOverflowTooltip: true },
  { prop: 'defaultFlag', label: t('dict.default'), width: 90, align: 'center' as const },
  { prop: 'sort', label: t('dict.sort'), width: 70, align: 'center' as const },
])

async function loadProviders() {
  loading.value = true
  try {
    providers.value = await dictApi.getDictData(AI_PROVIDER)
  } finally {
    loading.value = false
  }
}

async function loadModels() {
  if (!filterProvider.value) {
    models.value = []
    return
  }
  modelLoading.value = true
  try {
    models.value = await dictApi.getDictData(AI_MODEL, {
      parentTypeCode: AI_PROVIDER,
      parentValue: filterProvider.value,
    })
  } finally {
    modelLoading.value = false
  }
}

function onTierChange(_tier: string, tagType: string) {
  editForm.value.tagType = tagType
}

function openEdit(row: DictDataVO) {
  editForm.value = {
    id: row.id,
    label: row.label,
    value: row.value,
    extra: row.extra ?? '',
    tagType: row.tagType || tagTypeForAiProviderTier('B'),
  }
  editVisible.value = true
}

async function saveProvider() {
  saving.value = true
  try {
    providerExtraFormRef.value?.syncToModel()
    await dictApi.updateData(editForm.value.id, {
      label: editForm.value.label,
      extra: editForm.value.extra || undefined,
      tagType: editForm.value.tagType || undefined,
    })
    dictStore.invalidate(AI_PROVIDER)
    dictStore.invalidate(AI_MODEL)
    ElMessage.success(t('settings.ai.saveSuccess'))
    editVisible.value = false
    await loadProviders()
    emit('providers-changed')
  } catch {
    ElMessage.error(t('settings.ai.saveFailed'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadProviders()
})
</script>

<style scoped>
.settings-ai-system {
  max-width: 960px;
}

.system-sub-tabs {
  margin-top: 16px;
}

</style>
