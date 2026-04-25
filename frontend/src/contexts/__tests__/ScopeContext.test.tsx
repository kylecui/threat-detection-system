import { type ReactNode } from 'react';
import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, test } from 'vitest';
import { AuthProvider } from '@/contexts/AuthContext';
import { ScopeProvider, useScope } from '@/contexts/ScopeContext';

function wrapper({ children }: { children: ReactNode }) {
  return (
    <AuthProvider>
      <ScopeProvider>{children}</ScopeProvider>
    </AuthProvider>
  );
}

describe('ScopeContext', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  test('initial state reads from localStorage for admin users', () => {
    localStorage.setItem('token', 'token');
    localStorage.setItem('user', JSON.stringify({ id: 1, username: 'sa', roles: ['SUPER_ADMIN'] }));
    localStorage.setItem('scope_tenant_id', '456');
    localStorage.setItem('scope_customer_id', 'cust-456');

    const { result } = renderHook(() => useScope(), { wrapper });

    expect(result.current.tenantId).toBe(456);
    expect(result.current.customerId).toBe('cust-456');
    expect(result.current.effectiveCustomerId).toBe('cust-456');
  });

  test('setTenantId and setCustomerId update state and persistence', () => {
    localStorage.setItem('token', 'token');
    localStorage.setItem('user', JSON.stringify({ id: 2, username: 'ta', roles: ['TENANT_ADMIN'] }));

    const { result } = renderHook(() => useScope(), { wrapper });

    act(() => {
      result.current.setTenantId(999);
      result.current.setCustomerId('cust-999');
    });

    expect(result.current.tenantId).toBe(999);
    expect(result.current.customerId).toBe('cust-999');
    expect(localStorage.getItem('scope_tenant_id')).toBe('999');
    expect(localStorage.getItem('scope_customer_id')).toBe('cust-999');

    act(() => {
      result.current.setTenantId(undefined);
      result.current.setCustomerId(undefined);
    });

    expect(localStorage.getItem('scope_tenant_id')).toBeNull();
    expect(localStorage.getItem('scope_customer_id')).toBeNull();
  });

  test('throws when useScope used outside provider', () => {
    expect(() => renderHook(() => useScope())).toThrow('useScope must be used within ScopeProvider');
  });
});
