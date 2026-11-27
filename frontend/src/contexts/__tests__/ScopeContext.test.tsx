import { type ReactNode } from 'react';
import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, test, vi } from 'vitest';
import { AuthProvider } from '@/contexts/AuthContext';
import { ScopeProvider, useScope } from '@/contexts/ScopeContext';

const apiGet = vi.hoisted(() => vi.fn());

vi.mock('@/services/api', () => ({
  default: { get: apiGet },
}));

function wrapper({ children }: { children: ReactNode }) {
  return <AuthProvider><ScopeProvider>{children}</ScopeProvider></AuthProvider>;
}

describe('ScopeContext', () => {
  beforeEach(() => {
    localStorage.clear();
    document.cookie = 'tds_selected_tenant=; Max-Age=0; path=/';
    document.cookie = 'tds_selected_customer=; Max-Age=0; path=/';
    apiGet.mockReset();
  });

  test('restores only a valid active tenant and customer for super administrators', async () => {
    localStorage.setItem('token', 'token');
    localStorage.setItem('user', JSON.stringify({ id: 1, username: 'sa', roles: ['SUPER_ADMIN'] }));
    document.cookie = 'tds_selected_tenant=456; path=/';
    document.cookie = 'tds_selected_customer=cust-456; path=/';
    apiGet
      .mockResolvedValueOnce({
        data: { content: [{ id: 456, tenantId: 't-456', name: 'Tenant', status: 'ACTIVE' }] },
      })
      .mockResolvedValueOnce({
        data: { content: [{ id: 1, customerId: 'cust-456', name: 'Customer', status: 'ACTIVE' }] },
      });

    const { result } = renderHook(() => useScope(), { wrapper });
    await waitFor(() => expect(result.current.initialized).toBe(true));

    expect(result.current.tenantId).toBe(456);
    expect(result.current.customerId).toBe('cust-456');
    expect(result.current.effectiveCustomerId).toBe('cust-456');
  });

  test('tenant administrators use their token tenant and clear stale legacy customer scope', async () => {
    localStorage.setItem('token', 'token');
    localStorage.setItem('customer_id', 'stale-customer');
    localStorage.setItem('user', JSON.stringify({
      id: 2, username: 'ta', roles: ['TENANT_ADMIN'], tenantId: 999,
    }));
    apiGet.mockResolvedValueOnce({ data: { content: [] } });

    const { result } = renderHook(() => useScope(), { wrapper });
    await waitFor(() => expect(result.current.initialized).toBe(true));

    expect(result.current.tenantId).toBe(999);
    expect(result.current.customerId).toBeUndefined();
    expect(localStorage.getItem('customer_id')).toBeNull();

    act(() => result.current.setCustomerId('cust-999'));
    expect(result.current.effectiveCustomerId).toBe('cust-999');
    expect(localStorage.getItem('scope_customer_id')).toBe('cust-999');

    act(() => result.current.setTenantId(undefined));
    expect(localStorage.getItem('scope_tenant_id')).toBeNull();
    expect(localStorage.getItem('scope_customer_id')).toBeNull();
  });

  test('customer administrators are fixed to the customer in their authenticated identity', async () => {
    localStorage.setItem('token', 'token');
    localStorage.setItem('user', JSON.stringify({
      id: 3,
      username: 'cust-admin',
      roles: ['CUSTOMER_ADMIN'],
      tenantId: 7,
      customerId: 'cust-7',
    }));

    const { result } = renderHook(() => useScope(), { wrapper });
    await waitFor(() => expect(result.current.initialized).toBe(true));
    expect(result.current.tenantId).toBe(7);
    expect(result.current.effectiveCustomerId).toBe('cust-7');
    expect(result.current.scopeMode).toBe('customer');
    expect(apiGet).not.toHaveBeenCalled();
  });

  test('throws when useScope used outside provider', () => {
    expect(() => renderHook(() => useScope())).toThrow('useScope must be used within ScopeProvider');
  });
});
