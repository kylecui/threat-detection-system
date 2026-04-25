import { expect, test } from '@playwright/test';
import { setAuthState } from './helpers/auth';

test.describe('Dark mode', () => {
  test.beforeEach(async ({ page }) => {
    await setAuthState(page, 'SUPER_ADMIN');
  });

  test('dark mode toggle exists in header', async ({ page }) => {
    await page.goto('/overview');
    await expect(page.getByRole('button', { name: /切换主题|Toggle theme/ })).toBeVisible();
  });

  test('clicking toggle changes html data-theme attribute', async ({ page }) => {
    await page.goto('/overview');

    const before = await page.locator('html').getAttribute('data-theme');
    await page.getByRole('button', { name: /切换主题|Toggle theme/ }).click();
    const after = await page.locator('html').getAttribute('data-theme');

    expect(before).not.toBe(after);
    expect(after === 'dark' || after === 'light').toBeTruthy();
  });

  test('dark mode persists across reload via localStorage', async ({ page }) => {
    await page.goto('/overview');
    await page.getByRole('button', { name: /切换主题|Toggle theme/ }).click();

    const storedTheme = await page.evaluate(() => localStorage.getItem('tds-theme'));
    await page.reload();
    const dataTheme = await page.locator('html').getAttribute('data-theme');

    expect(storedTheme).toBeTruthy();
    expect(dataTheme).toBe(storedTheme);
  });
});
