import type { Page } from '@playwright/test';

export type E2ERole = 'SUPER_ADMIN' | 'TENANT_ADMIN' | 'CUSTOMER_USER';

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
    username: 'super-admin',
    displayName: 'Super Admin',
    email: 'super-admin@example.com',
    roles: ['SUPER_ADMIN'],
    customerId: 'demo-customer',
    tenantId: 1,
  },
  TENANT_ADMIN: {
    id: 2,
    username: 'tenant-admin',
    displayName: 'Tenant Admin',
    email: 'tenant-admin@example.com',
    roles: ['TENANT_ADMIN'],
    customerId: 'tenant-customer-001',
    tenantId: 1001,
  },
  CUSTOMER_USER: {
    id: 3,
    username: 'customer-user',
    displayName: 'Customer User',
    email: 'customer-user@example.com',
    roles: ['CUSTOMER_USER'],
    customerId: 'customer-001',
  },
};

function buildUser(role: E2ERole, overrides?: Partial<E2EAuthUser>): E2EAuthUser {
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
): Promise<E2EAuthUser> {
  const user = buildUser(role, overrides);
  const token = `mock-token-${role.toLowerCase()}`;
  const refreshToken = `mock-refresh-${role.toLowerCase()}`;

  await page.addInitScript(
    ({ authToken, authRefreshToken, authUser }) => {
      localStorage.setItem('token', authToken);
      localStorage.setItem('refreshToken', authRefreshToken);
      localStorage.setItem('user', JSON.stringify(authUser));

      if (authUser.customerId) {
        localStorage.setItem('customer_id', authUser.customerId);
      } else {
        localStorage.removeItem('customer_id');
      }

      if (authUser.tenantId) {
        localStorage.setItem('tenant_id', String(authUser.tenantId));
      } else {
        localStorage.removeItem('tenant_id');
      }
    },
    { authToken: token, authRefreshToken: refreshToken, authUser: user },
  );

  return user;
}

export async function clearAuthState(page: Page): Promise<void> {
  await page.addInitScript(() => {
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    localStorage.removeItem('customer_id');
    localStorage.removeItem('tenant_id');
  });
}
