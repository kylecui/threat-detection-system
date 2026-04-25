import { type ReactNode } from 'react';
import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { AuthProvider, useAuth, type AuthUser } from '@/contexts/AuthContext';

function wrapper({ children }: { children: ReactNode }) {
  return <AuthProvider>{children}</AuthProvider>;
}

describe('AuthContext', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });

  test('login stores token and user in localStorage', () => {
    const { result } = renderHook(() => useAuth(), { wrapper });
    const user: AuthUser = {
      id: 10,
      username: 'admin',
      roles: ['SUPER_ADMIN'],
      customerId: 'cust-1',
      tenantId: 100,
    };

    act(() => {
      result.current.login('token-1', 'refresh-1', user);
    });

    expect(localStorage.getItem('token')).toBe('token-1');
    expect(localStorage.getItem('refreshToken')).toBe('refresh-1');
    expect(localStorage.getItem('user')).toBe(JSON.stringify(user));
    expect(localStorage.getItem('customer_id')).toBe('cust-1');
    expect(localStorage.getItem('tenant_id')).toBe('100');
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user?.username).toBe('admin');
  });

  test('logout clears localStorage auth values', () => {
    localStorage.setItem('token', 'existing-token');
    localStorage.setItem('refreshToken', 'existing-refresh');
    localStorage.setItem('user', JSON.stringify({ id: 1, username: 'u', roles: ['CUSTOMER_USER'] }));
    localStorage.setItem('customer_id', 'cust-2');
    localStorage.setItem('tenant_id', '22');

    const { result } = renderHook(() => useAuth(), { wrapper });

    act(() => {
      result.current.logout();
    });

    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('refreshToken')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
    expect(localStorage.getItem('customer_id')).toBeNull();
    expect(localStorage.getItem('tenant_id')).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
  });

  test('throws when useAuth called outside provider', () => {
    expect(() => renderHook(() => useAuth())).toThrow('useAuth must be used within AuthProvider');
  });

  test('initial state is read from localStorage', () => {
    localStorage.setItem('token', 'bootstrap-token');
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 99,
        username: 'bootstrap-user',
        roles: ['TENANT_ADMIN'],
      }),
    );

    const { result } = renderHook(() => useAuth(), { wrapper });

    expect(result.current.token).toBe('bootstrap-token');
    expect(result.current.user?.username).toBe('bootstrap-user');
    expect(result.current.isAuthenticated).toBe(true);
  });

  test('role-based properties are computed correctly', () => {
    localStorage.setItem('token', 'token-role');
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 50,
        username: 'tenant',
        roles: ['TENANT_ADMIN', 'CUSTOMER_USER'],
      }),
    );

    const { result } = renderHook(() => useAuth(), { wrapper });

    expect(result.current.isSuperAdmin).toBe(false);
    expect(result.current.isTenantAdmin).toBe(true);
    expect(result.current.isCustomerUser).toBe(true);
    expect(result.current.hasRole('TENANT_ADMIN')).toBe(true);
    expect(result.current.hasRole('SUPER_ADMIN')).toBe(false);
  });
});
