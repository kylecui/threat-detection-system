import { expect, test } from '@playwright/test';
import { readFile } from 'node:fs/promises';
import { setAuthState } from './helpers/auth';
import { gotoReady } from './helpers/ready';

test.describe('Threat list page', () => {
  test.beforeEach(async ({ page }) => {
    await setAuthState(page, 'SUPER_ADMIN');
    await gotoReady(page, '/investigate/threats');
    await page.locator('.ant-segmented-item').filter({ hasText: 'Detail View' }).click();
  });

  test('threat list loads with filter bar', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Threat List' })).toBeVisible();
    await expect(page.locator('.ant-table')).toBeVisible();
  });

  test('filter controls are present', async ({ page }) => {
    await expect(page.locator('.ant-select').first()).toBeVisible();
    await expect(page.locator('.ant-picker-range')).toBeVisible();
    await expect(page.getByPlaceholder('Search suspicious source MAC')).toBeVisible();
  });

  test('table renders with expected columns', async ({ page }) => {
    await expect(page.getByRole('columnheader', { name: 'Assessment Time' })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: 'Suspicious Source MAC' })).toBeVisible();
    await expect(page.getByRole('columnheader', { name: 'Threat Level' })).toBeVisible();
  });

  test('detail drawer opens from a result row', async ({ page }) => {
    await page.getByRole('button', { name: 'Threat detail #42' }).click();
    await expect(page.locator('.ant-drawer-open')).toBeVisible();
    await expect(page.getByRole('dialog').getByText('203.0.113.10')).toBeVisible();
  });

  test('CSV and JSON exports produce current-page downloads with real data', async ({
    page,
  }) => {
    const csvDownloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Export CSV' }).click();
    const csvDownload = await csvDownloadPromise;
    expect(csvDownload.suggestedFilename()).toMatch(
      /^threat-list-\d{8}-\d{6}\.csv$/,
    );
    const csvPath = await csvDownload.path();
    expect(csvPath).not.toBeNull();
    const csv = await readFile(csvPath!, 'utf8');
    expect(csv).toContain('AA:BB:CC:DD:EE:FF');
    expect(csv).toContain('HIGH');
    await expect(page.getByText('CSV exported successfully')).toBeVisible();

    const jsonDownloadPromise = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Export JSON' }).click();
    const jsonDownload = await jsonDownloadPromise;
    expect(jsonDownload.suggestedFilename()).toMatch(
      /^threat-list-\d{8}-\d{6}\.json$/,
    );
    const jsonPath = await jsonDownload.path();
    expect(jsonPath).not.toBeNull();
    const json = JSON.parse(await readFile(jsonPath!, 'utf8')) as Array<{
      id: number;
      attackMac: string;
    }>;
    expect(json).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ id: 42, attackMac: 'AA:BB:CC:DD:EE:FF' }),
      ]),
    );
    await expect(page.getByText('JSON exported successfully')).toBeVisible();
  });

  test('delete action submits once and refreshes the list', async ({ page }) => {
    let deleteRequests = 0;
    let completeDelete!: () => void;
    const deleteGate = new Promise<void>((resolve) => {
      completeDelete = resolve;
    });
    await page.route('**/api/v1/assessment/42**', async (route) => {
      if (route.request().method() === 'DELETE') {
        deleteRequests += 1;
        await deleteGate;
        await route.fulfill({ status: 204 });
        return;
      }
      await route.fallback();
    });

    await page.getByRole('button', { name: 'Delete threat record #42' }).click();
    const confirm = page.locator('.ant-modal-confirm-btns .ant-btn-dangerous');
    await confirm.click();
    await expect(confirm).toHaveClass(/ant-btn-loading/);
    expect(deleteRequests).toBe(1);
    completeDelete();
    await expect(page.getByText('Deleted successfully')).toBeVisible();
    expect(deleteRequests).toBe(1);
  });
});
