import { expect, test } from '@playwright/test';
import { setAuthState } from './helpers/auth';

test.describe('Threat list page', () => {
  test.beforeEach(async ({ page }) => {
    await setAuthState(page, 'SUPER_ADMIN');
    await page.goto('/investigate/threats');
  });

  test('threat list loads with filter bar', async ({ page }) => {
    await expect(page.getByText(/Threat List|威胁列表|Title/).first()).toBeVisible();
    await expect(page.locator('.ant-table')).toBeVisible();
  });

  test('filter controls are present', async ({ page }) => {
    await expect(page.locator('.ant-select').first()).toBeVisible();
    await expect(page.locator('.ant-picker-range')).toBeVisible();
    await expect(page.locator('input[placeholder="Search Attack Mac"]')).toBeVisible();
  });

  test('table renders with expected columns', async ({ page }) => {
    await expect(page.getByRole('columnheader', { name: /ID/ })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: /Assessment Time/ })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: /Attack Mac/ })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: /Threat Level/ })).toBeVisible();
  });

  test('detail drawer opens on row click when rows exist', async ({ page }) => {
    const rows = page.locator('.ant-table-tbody tr');
    const rowCount = await rows.count();

    if (rowCount > 0) {
      await rows.first().click();
      await expect(page.locator('.ant-drawer-open')).toBeVisible();
    } else {
      await expect(page.locator('.ant-empty')).toBeVisible();
    }
  });
});
