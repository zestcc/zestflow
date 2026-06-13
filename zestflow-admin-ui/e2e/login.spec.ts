import { test, expect } from '@playwright/test'

test.describe('Login page', () => {
  test('shows login form and rejects empty submit', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('input[type="text"], input[autocomplete="username"]').first()).toBeVisible()
    await page.locator('.btn-login').click()
    await expect(page).toHaveURL(/\/login/)
  })

  test('invalid credentials stay on login', async ({ page }) => {
    await page.goto('/login')
    const user = page.locator('input[autocomplete="username"], input[type="text"]').first()
    const pass = page.locator('input[type="password"]').first()
    await user.fill('not-a-real-user')
    await pass.fill('wrong-password')
    await page.locator('.btn-login').click()
    await expect(page).toHaveURL(/\/login/, { timeout: 10_000 })
  })
})
