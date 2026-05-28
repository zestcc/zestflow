<template>
  <div class="chains-page">
    <div class="page-header">
      <h2>{{ $t('chains.title') }}</h2>
      <el-button type="primary" @click="$router.push('/chains/create')">{{ $t('chains.createChain') }}</el-button>
    </div>
    <el-table :data="[]" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" :label="$t('chains.chainName')" />
      <el-table-column prop="status" :label="$t('common.status')" width="100" />
      <el-table-column prop="updatedAt" :label="$t('common.updatedAt')" width="180" />
      <el-table-column :label="$t('common.actions')" width="200">
        <template #default>
          <el-button text type="primary">{{ $t('common.edit') }}</el-button>
          <el-button text type="danger">{{ $t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.chains-page {
  background: #fff;
  padding: 20px;
  border-radius: 4px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0;
  font-size: 18px;
  color: #303133;
}
</style>
