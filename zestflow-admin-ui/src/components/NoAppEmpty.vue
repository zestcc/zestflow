<template>
  <div class="no-app-empty">
    <el-empty :description="$t('common.noAppEmpty')">
      <template #image>
        <svg viewBox="0 0 200 160" width="200" height="160" xmlns="http://www.w3.org/2000/svg">
          <rect x="30" y="40" width="140" height="90" rx="10" fill="#e8edf2" stroke="#c0c8d4" stroke-width="2"/>
          <rect x="50" y="60" width="40" height="12" rx="4" fill="#c0c8d4"/>
          <rect x="50" y="80" width="60" height="12" rx="4" fill="#c0c8d4"/>
          <rect x="50" y="100" width="30" height="12" rx="4" fill="#c0c8d4"/>
          <circle cx="160" cy="120" r="30" fill="#e8edf2" stroke="#c0c8d4" stroke-width="2"/>
          <line x1="180" y1="140" x2="195" y2="155" stroke="#c0c8d4" stroke-width="3" stroke-linecap="round"/>
        </svg>
      </template>
    </el-empty>
  </div>
</template>

<style scoped>
.no-app-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400px;
}
</style>
