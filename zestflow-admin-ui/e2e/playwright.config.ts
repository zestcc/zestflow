import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: '.',
  timeout: 60_000,
  retries: 0,
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:8080',
    trace: 'on-first-retry',
  },
  projects: [{
    name: 'chromium',
    use: {
      ...devices['Desktop Chrome'],
      // 优先用本机 Chrome，避免 cdn.playwright.dev 在国内下载失败/极慢
      channel: process.env.PLAYWRIGHT_CHANNEL || 'chrome',
    },
  }],
})
