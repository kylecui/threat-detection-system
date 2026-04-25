import { expect, test } from '@playwright/test';
import { setAuthState } from './helpers/auth';

test.describe('RBAC menu visibility and route access', () => {
  test('SUPER_ADMIN sees all key menu items', async ({ page }) => {
    await setAuthState(page, 'SUPER_ADMIN');
    await page.goto('/overview');

    await expect(page.getByText(/租户管理|Tenant Management/).first()).toBeVisible();
    await expect(page.getByText(/管道健康|Pipeline Health/).first()).toBeVisible();
    await expect(page.getByText(/集成配置|Integrations/).first()).toBeVisible();
  });

  test('TENANT_ADMIN does not see tenant management', async ({ page }) => {
    await setAuthState(page, 'TENANT_ADMIN');
    await page.goto('/overview');

    await expect(page.getByText(/客户与设备|Customers & Devices/).first()).toBeVisible();
    await expect(page.getByText(/用户管理|User Management/).first()).toBeVisible();
    await expect(page.getByText(/租户管理|Tenant Management/)).toHaveCount(0);
  });

  test('CUSTOMER_USER sees limited menu without admin/operate sections', async ({ page }) => {
    await setAuthState(page, 'CUSTOMER_USER');
    await page.goto('/overview');

    await expect(page.getByText(/威胁总览|Overview/).first()).toBeVisible();
    await expect(page.getByText(/运维|Operate/)).toHaveCount(0);
    await expect(page.getByText(/管理|Admin/)).toHaveCount(0);
  });

  test('direct access to restricted route is blocked for CUSTOMER_USER', async ({ page }) => {
    await setAuthState(page, 'CUSTOMER_USER');
    await page.goto('/admin/tenants');

    await expect(page).not.toHaveURL(/\/admin\/tenants$/);
  });
});
