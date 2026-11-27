import { beforeEach, describe, expect, test, vi } from 'vitest';
import {
  clearStoredSession,
  getJwtExpirationMs,
  isAuthenticationFailure,
  redirectToLogin,
  resetSessionRedirectGuardForTests,
} from '@/services/session';

describe('session service', () => {
  beforeEach(() => {
    localStorage.clear();
    document.cookie = 'tds_selected_tenant=; Max-Age=0; path=/';
    document.cookie = 'tds_selected_customer=; Max-Age=0; path=/';
    resetSessionRedirectGuardForTests();
  });

  test('clears all auth and scope state including cookies', () => {
    for (const key of [
      'token', 'refreshToken', 'user', 'customer_id', 'tenant_id',
      'scope_tenant_id', 'scope_customer_id',
    ]) {
      localStorage.setItem(key, 'value');
    }
    document.cookie = 'tds_selected_tenant=7; path=/';
    document.cookie = 'tds_selected_customer=customer-a; path=/';

    clearStoredSession();

    expect(localStorage.length).toBe(0);
    expect(document.cookie).not.toContain('tds_selected_tenant');
    expect(document.cookie).not.toContain('tds_selected_customer');
  });

  test('redirects once with an explicit expiry reason', () => {
    const replace = vi.fn();

    expect(redirectToLogin('session_expired', replace)).toBe(true);
    expect(redirectToLogin('session_expired', replace)).toBe(false);
    expect(replace).toHaveBeenCalledOnce();
    expect(replace).toHaveBeenCalledWith('/login?reason=session_expired');
  });

  test('decodes base64url JWT expiration', () => {
    const payload = btoa(JSON.stringify({ exp: 1_800_000_000 }))
      .replace(/=/g, '')
      .replace(/\+/g, '-')
      .replace(/\//g, '_');

    expect(getJwtExpirationMs(`header.${payload}.signature`)).toBe(1_800_000_000_000);
    expect(getJwtExpirationMs('malformed')).toBeUndefined();
  });

  test('distinguishes authentication expiry from ordinary authorization denial', () => {
    expect(isAuthenticationFailure(401, {})).toBe(true);
    expect(isAuthenticationFailure(403, { code: 'TOKEN_EXPIRED' })).toBe(true);
    expect(isAuthenticationFailure(403, { message: 'JWT session expired' })).toBe(true);
    expect(isAuthenticationFailure(403, { code: 'ACCESS_DENIED' })).toBe(false);
  });
});
