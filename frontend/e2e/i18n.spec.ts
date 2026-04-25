import { expect, test } from '@playwright/test';
import { setAuthState } from './helpers/auth';

test.describe('Language switching', () => {
  test.beforeEach(async ({ page }) => {
    await setAuthState(page, 'SUPER_ADMIN');
  });

  test('language switcher exists in header', async ({ page }) => {
    await page.goto('/overview');
    await expect(page.getByRole('button', { name: /切换语言|Switch language/ })).toBeVisible();
  });

  test('switching language changes visible nav text', async ({ page }) => {
    await page.goto('/overview');

    await expect(page.getByText('调查').first()).toBeVisible();
    await page.getByRole('button', { name: /切换语言|Switch language/ }).click();
    await expect(page.getByText('Investigate').first()).toBeVisible();
  });

  test('language preference persists across reload', async ({ page }) => {
    await page.goto('/overview');
    await page.getByRole('button', { name: /切换语言|Switch language/ }).click();

    const lang = await page.evaluate(() => localStorage.getItem('tds-lang'));
    await page.reload();

    if (lang === 'en-US') {
      await expect(page.getByText('Investigate').first()).toBeVisible();
    } else {
      await expect(page.getByText('调查').first()).toBeVisible();
    }
  });
});
