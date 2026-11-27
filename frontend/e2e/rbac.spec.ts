import { expect, test, type Page } from '@playwright/test';
import { setAuthState } from './helpers/auth';
import { gotoReady } from './helpers/ready';

async function switchWorkspace(page: Page, name: string) {
  await page.getByRole('button', { name: 'Switch workspace' }).click();
  await page.getByRole('menuitem').filter({ hasText: name }).click();
}

test.describe('RBAC menu visibility and route access', () => {
  test('SUPER_ADMIN sees all key menu items', async ({ page }) => {
    await setAuthState(page, 'SUPER_ADMIN');
    await gotoReady(page, '/overview');

    await switchWorkspace(page, 'Organization');
    await expect(page.getByText('Tenant Management').first()).toBeVisible();
    await switchWorkspace(page, 'Platform health');
    await expect(page.getByText('Pipeline Health').first()).toBeVisible();
    await switchWorkspace(page, 'Configuration');
    await expect(page.getByText('Integrations').first()).toBeVisible();
  });

  test('TENANT_ADMIN does not see tenant management', async ({ page }) => {
    await setAuthState(page, 'TENANT_ADMIN');
    await gotoReady(page, '/overview');

    await switchWorkspace(page, 'Organization');
    await expect(page.getByText('Customers & Devices').first()).toBeVisible();
    await expect(page.getByText('User Management').first()).toBeVisible();
    await expect(page.getByText('Tenant Management')).toHaveCount(0);
  });

  test('CUSTOMER_USER only sees permitted workspaces', async ({ page }) => {
    await setAuthState(page, 'CUSTOMER_USER');
    await gotoReady(page, '/overview');

    await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();
    await page.getByRole('button', { name: 'Switch workspace' }).click();
    await expect(page.getByRole('menuitem').filter({ hasText: 'Platform health' })).toHaveCount(0);
    await expect(page.getByRole('menuitem').filter({ hasText: 'Organization' })).toHaveCount(0);
    await expect(page.getByRole('menuitem').filter({ hasText: 'Configuration' })).toBeVisible();
  });

  test('direct access to restricted route is blocked for CUSTOMER_USER', async ({ page }) => {
    await setAuthState(page, 'CUSTOMER_USER');
    await gotoReady(page, '/admin/tenants');

    await expect(page).not.toHaveURL(/\/admin\/tenants$/);
  });
});
