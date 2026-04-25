import { expect, test } from '@playwright/test';
import { setAuthState } from './helpers/auth';

test.describe('Configuration pages', () => {
  test.beforeEach(async ({ page }) => {
    await setAuthState(page, 'SUPER_ADMIN');
  });

  test('config sub-routes load', async ({ page }) => {
    const routes = [
      '/config/general',
      '/config/notifications',
      '/config/integrations',
      '/config/ai',
      '/config/plugins',
    ];

    for (const route of routes) {
      await page.goto(route);
      await expect(page).toHaveURL(new RegExp(`${route.replace('/', '\\/')}$`));
      await expect(page.locator('.ant-card').first()).toBeVisible();
    }
  });

  test('can navigate between configuration pages via menu links', async ({ page }) => {
    await page.goto('/config/general');
    await page.getByText(/通知配置|Notifications/).first().click();
    await expect(page).toHaveURL(/\/config\/notifications$/);

    await page.getByText(/AI配置|AI/).first().click();
    await expect(page).toHaveURL(/\/config\/ai$/);
  });
});
