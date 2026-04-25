import { expect, test } from '@playwright/test';
import { setAuthState } from './helpers/auth';

test.describe('Overview page', () => {
  test.beforeEach(async ({ page }) => {
    await setAuthState(page, 'SUPER_ADMIN');
    await page.goto('/overview');
  });

  test('overview page loads with stat cards and header', async ({ page }) => {
    await expect(page.getByText(/总览|Overview|Title/).first()).toBeVisible();
    await expect(page.getByText(/Total Threats|总威胁数|Total Threats/).first()).toBeVisible();
  });

  test('chart containers are rendered', async ({ page }) => {
    await expect(page.getByRole('img', { name: /Trend Chart Aria|趋势/ })).toBeVisible();
    await expect(page.getByRole('img', { name: /Level Chart Aria|等级/ })).toBeVisible();
    await expect(page.getByRole('img', { name: /Port Chart Aria|端口/ })).toBeVisible();
  });

  test('refresh keyboard shortcut R keeps page active', async ({ page }) => {
    await page.keyboard.press('r');
    await expect(page).toHaveURL(/\/overview$/);
  });
});
