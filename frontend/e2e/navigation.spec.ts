import { expect, test } from '@playwright/test';
import { setAuthState } from './helpers/auth';

test.describe('Navigation and routing', () => {
  test.beforeEach(async ({ page }) => {
    await setAuthState(page, 'SUPER_ADMIN');
  });

  test('main routes are accessible for SUPER_ADMIN', async ({ page }) => {
    const routes = [
      '/overview',
      '/investigate/alerts',
      '/investigate/threats',
      '/investigate/intel',
      '/operate/pipeline',
      '/operate/ml',
      '/admin/customers',
      '/admin/users',
      '/admin/tenants',
      '/config/general',
      '/config/notifications',
      '/config/integrations',
      '/config/ai',
      '/config/plugins',
    ];

    for (const route of routes) {
      await page.goto(route);
      await expect(page).toHaveURL(new RegExp(`${route.replace('/', '\\/')}$`));
    }
  });

  test('grouped menu sections exist', async ({ page }) => {
    await page.goto('/overview');

    await expect(page.getByText(/调查|Investigate/).first()).toBeVisible();
    await expect(page.getByText(/运维|Operate/).first()).toBeVisible();
    await expect(page.getByText(/管理|Admin/).first()).toBeVisible();
    await expect(page.getByText(/配置|Configuration/).first()).toBeVisible();
  });

  test('legacy URLs redirect to new paths', async ({ page }) => {
    await page.goto('/threats');
    await expect(page).toHaveURL(/\/investigate\/threats$/);

    await page.goto('/pipeline');
    await expect(page).toHaveURL(/\/operate\/pipeline$/);
  });

  test('default route redirects to /overview', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/overview$/);
  });

  test('breadcrumbs appear on sub-pages', async ({ page }) => {
    await page.goto('/config/general');
    await expect(page.locator('.ant-breadcrumb')).toBeVisible();
  });
});
