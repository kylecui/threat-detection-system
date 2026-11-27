import { expect, test } from '@playwright/test';
import { setAuthState } from './helpers/auth';
import { gotoReady, waitForAppReady } from './helpers/ready';

test.describe('Language switching', () => {
  test.beforeEach(async ({ page }) => {
    await setAuthState(page, 'SUPER_ADMIN', undefined, 'zh-CN');
  });

  test('language switcher exists in header', async ({ page }) => {
    await gotoReady(page, '/overview');
    await expect(page.getByRole('button', { name: '切换语言' })).toBeVisible();
  });

  test('switching language changes visible workspace text', async ({ page }) => {
    await gotoReady(page, '/overview');

    await expect(page.getByRole('button', { name: '切换工作区' })).toContainText('威胁运营');
    await page.getByRole('button', { name: '切换语言' }).click();
    await expect(page.getByRole('button', { name: 'Switch workspace' })).toContainText('Threat operations');
  });

  test('language preference persists across reload', async ({ page }) => {
    await gotoReady(page, '/overview');
    await page.getByRole('button', { name: '切换语言' }).click();

    const lang = await page.evaluate(() => localStorage.getItem('tds-lang'));
    await page.reload();
    await waitForAppReady(page);

    expect(lang).toBe('en-US');
    await expect(page.getByRole('button', { name: 'Switch workspace' })).toContainText('Threat operations');
  });
});
