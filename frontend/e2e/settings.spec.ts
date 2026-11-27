import { expect, test } from '@playwright/test';
import { setAuthState } from './helpers/auth';
import { gotoReady } from './helpers/ready';

test.describe('Configuration pages', () => {
  test.beforeEach(async ({ page }) => {
    await setAuthState(page, 'SUPER_ADMIN');
  });

  test('config sub-routes load', async ({ page }) => {
    const routes = [
      { path: '/config/general', title: 'General', content: 'Auto Refresh' },
      { path: '/config/notifications', title: 'Notifications', content: 'SMTP Server' },
      {
        path: '/config/integrations',
        title: 'Integrations',
        content: 'Integration Configuration',
      },
      { path: '/config/ai', title: 'AI', content: 'Global AI Configuration' },
      { path: '/config/plugins', title: 'Plugins', content: 'Plugin Configuration' },
    ];

    for (const route of routes) {
      await gotoReady(page, route.path);
      await expect(page).toHaveURL(
        new RegExp(`${route.path.replace('/', '\\/')}$`),
      );
      await expect(
        page.getByRole('heading', { level: 1, name: route.title }),
      ).toBeVisible();
      await expect(page.getByText(route.content).first()).toBeVisible();
    }
  });

  test('can navigate between configuration pages via menu links', async ({ page }) => {
    await gotoReady(page, '/config/general');
    await page.getByText(/通知配置|Notifications/).first().click();
    await expect(page).toHaveURL(/\/config\/notifications$/);

    await page.getByText(/AI配置|AI/).first().click();
    await expect(page).toHaveURL(/\/config\/ai$/);
  });
});
