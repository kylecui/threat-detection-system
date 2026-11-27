import Cookies from 'js-cookie';

const SESSION_STORAGE_KEYS = [
  'token',
  'refreshToken',
  'user',
  'customer_id',
  'tenant_id',
  'scope_tenant_id',
  'scope_customer_id',
] as const;

const SCOPE_COOKIE_KEYS = [
  'tds_selected_tenant',
  'tds_selected_customer',
] as const;

export const SESSION_CLEARED_EVENT = 'tds:session-cleared';

let redirectInProgress = false;

export function clearStoredSession(): void {
  SESSION_STORAGE_KEYS.forEach((key) => localStorage.removeItem(key));
  SCOPE_COOKIE_KEYS.forEach((key) => {
    Cookies.remove(key);
    Cookies.remove(key, { path: '/' });
  });

  window.dispatchEvent(new CustomEvent(SESSION_CLEARED_EVENT));
}

export function redirectToLogin(
  reason?: 'session_expired',
  replace: (url: string) => void = (url) => window.location.replace(url),
): boolean {
  if (redirectInProgress || window.location.pathname === '/login') {
    return false;
  }

  redirectInProgress = true;
  const target = reason === 'session_expired'
    ? '/login?reason=session_expired'
    : '/login';
  replace(target);
  return true;
}

export function terminateSession(reason?: 'session_expired'): void {
  clearStoredSession();
  redirectToLogin(reason);
}

export function getJwtExpirationMs(token: string): number | undefined {
  try {
    const payload = token.split('.')[1];
    if (!payload) return undefined;

    const normalized = payload.replace(/-/g, '+').replace(/_/g, '/');
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=');
    const decoded = JSON.parse(window.atob(padded)) as { exp?: unknown };
    return typeof decoded.exp === 'number' && Number.isFinite(decoded.exp)
      ? decoded.exp * 1000
      : undefined;
  } catch {
    return undefined;
  }
}

export function isAuthenticationFailure(status: number | undefined, data: unknown): boolean {
  if (status === 401) return true;
  if (status !== 403 || !data || typeof data !== 'object') return false;

  const response = data as { code?: unknown; message?: unknown; error?: unknown };
  const code = typeof response.code === 'string' ? response.code.toUpperCase() : '';
  const authenticationCodes = new Set([
    'TOKEN_EXPIRED',
    'TOKEN_INVALID',
    'UNAUTHORIZED',
    'AUTHENTICATION_REQUIRED',
    'INVALID_TOKEN',
  ]);
  if (authenticationCodes.has(code)) return true;

  const details = [response.message, response.error]
    .filter((value): value is string => typeof value === 'string')
    .join(' ');
  return /(?:token|jwt|session|会话|登录|认证).*(?:expired|过期)/i.test(details)
    || /(?:expired|过期).*(?:token|jwt|session|会话|登录|认证)/i.test(details);
}

// Only used by isolated unit tests; production navigation remains one-shot.
export function resetSessionRedirectGuardForTests(): void {
  redirectInProgress = false;
}
