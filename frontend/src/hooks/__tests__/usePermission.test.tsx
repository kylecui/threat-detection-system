import { type ReactNode } from 'react';
import { renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, test } from 'vitest';
import { AuthProvider } from '@/contexts/AuthContext';
import { usePermission } from '@/hooks/usePermission';

function authWrapper({ children }: { children: ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>;
}

describe('usePermission', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test('checks role membership and aggregated role checks', () => {
    localStorage.setItem('token', 'token-value');
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 1,
        username: 'tenant-admin',
        roles: ['TENANT_ADMIN', 'CUSTOMER_USER'],
      }),
    );

    const { result } = renderHook(() => usePermission(), { wrapper: authWrapper });

    expect(result.current.hasRole('TENANT_ADMIN')).toBe(true);
    expect(result.current.hasRole('SUPER_ADMIN')).toBe(false);
    expect(result.current.hasAnyRole(['SUPER_ADMIN', 'TENANT_ADMIN'])).toBe(true);
    expect(result.current.hasAnyRole(['SUPER_ADMIN'])).toBe(false);
    expect(result.current.hasAllRoles(['TENANT_ADMIN', 'CUSTOMER_USER'])).toBe(true);
    expect(result.current.hasAllRoles(['TENANT_ADMIN', 'SUPER_ADMIN'])).toBe(false);
    expect(result.current.canAccess(['TENANT_ADMIN'])).toBe(true);
    expect(result.current.canAccess(['SUPER_ADMIN'])).toBe(false);
    expect(result.current.canAccess([])).toBe(true);
  });

  test('computed flags reflect user role combinations', () => {
    localStorage.setItem('token', 'token-value');
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 2,
        username: 'customer',
        roles: ['CUSTOMER_USER'],
      }),
    );

    const { result } = renderHook(() => usePermission(), { wrapper: authWrapper });

    expect(result.current.isSuperAdmin).toBe(false);
    expect(result.current.isTenantAdmin).toBe(false);
    expect(result.current.isCustomerUser).toBe(true);
    expect(result.current.isAdminRole).toBe(false);
  });

  test('super admin can access any protected routes', () => {
    localStorage.setItem('token', 'token-value');
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 3,
        username: 'root',
        roles: ['SUPER_ADMIN'],
      }),
    );

    const { result } = renderHook(() => usePermission(), { wrapper: authWrapper });

    expect(result.current.isSuperAdmin).toBe(true);
    expect(result.current.isAdminRole).toBe(true);
    expect(result.current.canAccess(['TENANT_ADMIN'])).toBe(true);
    expect(result.current.canAccess(['CUSTOMER_USER'])).toBe(true);
  });
});
