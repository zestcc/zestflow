import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import path from 'path'

const adminProxyTarget =
  process.env.VITE_ADMIN_PROXY_TARGET ?? 'http://localhost:8082'

export default defineConfig({
  plugins: [vue()],
  test: {
    environment: 'node',
    include: ['src/**/*.spec.ts'],
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api/zestflow': {
        target: adminProxyTarget,
        changeOrigin: true,
        ws: true,
      },
      '/uploads': {
        target: adminProxyTarget,
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: path.resolve(__dirname, '../zestflow-admin/src/main/resources/static'),
    emptyOutDir: true,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/@antv/x6')) return 'x6'
          if (id.includes('node_modules/element-plus')) return 'element-plus'
          if (id.includes('node_modules/vue-i18n')) return 'i18n'
        },
      },
    },
  },
})
