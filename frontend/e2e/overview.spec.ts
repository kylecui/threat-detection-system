import { expect, test } from '@playwright/test';
import { setAuthState } from './helpers/auth';
import { gotoReady, waitForAppReady, waitForOverviewReady } from './helpers/ready';

test.describe('Overview page', () => {
  test.beforeEach(async ({ page }) => {
    await setAuthState(page, 'SUPER_ADMIN');
    await gotoReady(page, '/overview');
    await waitForOverviewReady(page);
  });

  test('overview page loads with stat cards and header', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Total Threats').first()).toBeVisible();
  });

  test('chart containers are rendered', async ({ page }) => {
    await expect(page.getByRole('img', { name: 'Threat trend line chart' })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('img', { name: 'Threat level distribution pie chart' })).toBeVisible();
    await expect(page.getByRole('img', { name: 'Horizontal bar chart of attack counts by port' })).toBeVisible();
  });

  test('refresh keyboard shortcut R keeps page active', async ({ page }) => {
    await page.keyboard.press('r');
    await expect(page).toHaveURL(/\/overview$/);
  });

  test('time range selection updates the overview request window', async ({ page }) => {
    const requestPromise = page.waitForRequest((request) => {
      const url = new URL(request.url());
      return (
        url.pathname.endsWith('/api/v1/assessment/overview-snapshot') &&
        url.searchParams.get('hours') === '168'
      );
    });

    await page.getByText('7d', { exact: true }).click();
    await requestPromise;
    await expect(page.getByTitle('7d')).toHaveAttribute('aria-selected', 'true');
  });

  test('view all keeps the investigation route reachable', async ({ page }) => {
    await page.getByRole('button', { name: 'View all threats' }).click();
    await expect(page).toHaveURL(/\/investigate\/threats$/);
  });

  test('mobile overview keeps threat fields in readable rows', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.reload();
    await waitForAppReady(page);
    // The skeleton branch has no mobile lists; assert only once data is shown.
    await waitForOverviewReady(page);

    await expect(page.locator('.tds-overview__mobile-list').first()).toBeVisible();
    await expect(page.locator('.tds-overview__desktop-table').first()).toBeHidden();
    const recentThreatRows = page.locator('.tds-overview__mobile-list').first();
    await expect(recentThreatRows.getByText('AA:BB:CC:DD:EE:FF')).toBeVisible();
    await expect(recentThreatRows.getByText('Threat Score')).toBeVisible();
    await expect(recentThreatRows.getByText('Detection Window')).toBeVisible();
  });
});
