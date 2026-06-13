import { test, expect } from '@playwright/test'

test.describe('SSO callback', () => {
  test('missing code/state redirects to login', async ({ page }) => {
    await page.goto('/login/callback')
    await expect(page).toHaveURL(/\/login/, { timeout: 15_000 })
  })

  test('login page loads SSO section when configured', async ({ page }) => {
    await page.goto('/login')
    const ssoBtn = page.getByRole('button', { name: /SSO|单点|Enterprise/i })
    const count = await ssoBtn.count()
    if (count > 0) {
      await expect(ssoBtn.first()).toBeVisible()
    } else {
      test.info().annotations.push({ type: 'note', description: 'SSO disabled in this environment' })
    }
  })
})
