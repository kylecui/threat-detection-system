import type { Page } from "@playwright/test";
import { installApiMocks, type ApiMockReport } from "./mockApi";

export { getApiMockReport } from "./mockApi";

export type E2ERole = "SUPER_ADMIN" | "TENANT_ADMIN" | "CUSTOMER_USER";

export interface E2EAuthUser {
  id: number;
  username: string;
  displayName?: string;
  email?: string;
  roles: E2ERole[];
  customerId?: string;
  tenantId?: number;
}

const DEFAULT_USERS: Record<E2ERole, E2EAuthUser> = {
  SUPER_ADMIN: {
    id: 1,
    username: "super-admin",
    displayName: "Super Admin",
    email: "super-admin@example.com",
    roles: ["SUPER_ADMIN"],
    customerId: "demo-customer",
    tenantId: 1,
  },
  TENANT_ADMIN: {
    id: 2,
    username: "tenant-admin",
    displayName: "Tenant Admin",
    email: "tenant-admin@example.com",
    roles: ["TENANT_ADMIN"],
    customerId: "tenant-customer-001",
    tenantId: 1001,
  },
  CUSTOMER_USER: {
    id: 3,
    username: "customer-user",
    displayName: "Customer User",
    email: "customer-user@example.com",
    roles: ["CUSTOMER_USER"],
    customerId: "customer-001",
  },
};

/**
 * Strict, typed API mocks for every GET (see ./mockApi.ts, INTERACTION-003).
 * Unregistered GETs are answered with 501 and recorded; use
 * `getApiMockReport(page)` to assert that none occurred.
 */
export async function stubApiAvailability(page: Page): Promise<ApiMockReport> {
  return installApiMocks(page);
}

function buildUser(
  role: E2ERole,
  overrides?: Partial<E2EAuthUser>,
): E2EAuthUser {
  return {
    ...DEFAULT_USERS[role],
    ...overrides,
    roles: overrides?.roles ?? DEFAULT_USERS[role].roles,
  };
}

export async function setAuthState(
  page: Page,
  role: E2ERole,
  overrides?: Partial<E2EAuthUser>,
  language: "zh-CN" | "en-US" = "en-US",
): Promise<E2EAuthUser> {
  await stubApiAvailability(page);
  const user = buildUser(role, overrides);
  const token = `mock-token-${role.toLowerCase()}`;
  const refreshToken = `mock-refresh-${role.toLowerCase()}`;

  await page.addInitScript(
    ({ authToken, authRefreshToken, authUser, preferredLanguage }) => {
      localStorage.setItem("token", authToken);
      localStorage.setItem("refreshToken", authRefreshToken);
      localStorage.setItem("user", JSON.stringify(authUser));
      if (!localStorage.getItem("tds-lang")) {
        localStorage.setItem("tds-lang", preferredLanguage);
      }
      document.cookie = "tds_selected_tenant=1; path=/";
      document.cookie = "tds_selected_customer=e2e-customer; path=/";

      if (authUser.customerId) {
        localStorage.setItem("customer_id", authUser.customerId);
      } else {
        localStorage.removeItem("customer_id");
      }

      if (authUser.tenantId) {
        localStorage.setItem("tenant_id", String(authUser.tenantId));
      } else {
        localStorage.removeItem("tenant_id");
      }
    },
    {
      authToken: token,
      authRefreshToken: refreshToken,
      authUser: user,
      preferredLanguage: language,
    },
  );

  return user;
}

export type E2ETheme = "light" | "dark";

/** Persist the theme before any page script runs (ThemeContext key `tds-theme`). */
export async function setTheme(page: Page, theme: E2ETheme): Promise<void> {
  await page.addInitScript((value) => {
    localStorage.setItem("tds-theme", value);
  }, theme);
}

export async function clearAuthState(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.removeItem("token");
    localStorage.removeItem("refreshToken");
    localStorage.removeItem("user");
    localStorage.removeItem("customer_id");
    localStorage.removeItem("tenant_id");
  });
}
