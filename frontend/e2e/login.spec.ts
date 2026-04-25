import { expect, test } from '@playwright/test';

test.describe('Login page', () => {
  test('login page renders title, fields, and submit button', async ({ page }) => {
    await page.goto('/login');

    await expect(page.getByText(/威胁检测系统|Threat Detection System/)).toBeVisible();
    await expect(page.locator('input[placeholder="用户名"], input[placeholder="Username"]').first()).toBeVisible();
    await expect(page.locator('input[placeholder="密码"], input[placeholder="Password"]').first()).toBeVisible();
    await expect(page.getByRole('button', { name: /登录|Login/ })).toBeVisible();
  });

  test('login form validation for empty username/password', async ({ page }) => {
    await page.goto('/login');

    await page.getByRole('button', { name: /登录|Login/ }).click();

    await expect(page.getByText(/请输入用户名|Please enter username/)).toBeVisible();
    await expect(page.getByText(/请输入密码|Please enter password/)).toBeVisible();
  });

  test('successful login stores auth and navigates into app', async ({ page }) => {
    await page.route('**/api/v1/auth/login', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          token: 'mock-token',
          refreshToken: 'mock-refresh-token',
          user: {
            id: 1,
            username: 'admin',
            displayName: 'Admin',
            roles: ['SUPER_ADMIN'],
            customerId: 'demo-customer',
            tenantId: 1,
          },
        }),
      });
    });

    await page.goto('/login');
    await page.locator('input[placeholder="用户名"], input[placeholder="Username"]').first().fill('admin');
    await page.locator('input[placeholder="密码"], input[placeholder="Password"]').first().fill('admin123');
    await page.getByRole('button', { name: /登录|Login/ }).click();

    await expect(page).toHaveURL(/\/(dashboard|overview)$/);

    const token = await page.evaluate(() => localStorage.getItem('token'));
    expect(token).toBe('mock-token');
  });

  test('login page is accessible at /login', async ({ page }) => {
    await page.goto('/login');
    await expect(page).toHaveURL(/\/login$/);
  });
});
