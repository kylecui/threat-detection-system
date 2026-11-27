import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import {
  getJwtExpirationMs,
  SESSION_CLEARED_EVENT,
  terminateSession,
} from '@/services/session';

export type AppRole = 'SUPER_ADMIN' | 'TENANT_ADMIN' | 'CUSTOMER_ADMIN' | 'CUSTOMER_USER';

export interface AuthUser {
  id: number;
  username: string;
  displayName?: string;
  email?: string;
  roles: AppRole[];
  customerId?: string;
  tenantId?: number;
}

interface AuthState {
  user: AuthUser | null;
  token: string | null;
  isAuthenticated: boolean;
}

interface AuthContextValue extends AuthState {
  login: (token: string, refreshToken: string, user: AuthUser) => void;
  logout: () => void;
  hasRole: (role: AppRole) => boolean;
  isSuperAdmin: boolean;
  isTenantAdmin: boolean;
  isCustomerAdmin: boolean;
  isCustomerUser: boolean;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function parseStoredUser(): AuthUser | null {
  try {
    const raw = localStorage.getItem('user');
    if (!raw) return null;
    const parsed = JSON.parse(raw);
    return {
      id: parsed.id ?? 0,
      username: parsed.username ?? '',
      displayName: parsed.displayName,
      email: parsed.email,
      roles: (parsed.roles ?? []) as AppRole[],
      customerId: parsed.customerId || undefined,
      tenantId: parsed.tenantId || undefined,
    };
  } catch {
    return null;
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(() => parseStoredUser());
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('token'));

  const isAuthenticated = !!token && !!user;

  const login = useCallback((newToken: string, refreshToken: string, newUser: AuthUser) => {
    localStorage.setItem('token', newToken);
    localStorage.setItem('refreshToken', refreshToken);
    localStorage.setItem('user', JSON.stringify(newUser));
    if (newUser.customerId) {
      localStorage.setItem('customer_id', newUser.customerId);
    }
    if (newUser.tenantId) {
      localStorage.setItem('tenant_id', String(newUser.tenantId));
    }
    setToken(newToken);
    setUser(newUser);
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
    terminateSession();
  }, []);

  const hasRole = useCallback((role: AppRole) => {
    return user?.roles.includes(role) ?? false;
  }, [user]);

  const isSuperAdmin = user?.roles.includes('SUPER_ADMIN') ?? false;
  const isTenantAdmin = user?.roles.includes('TENANT_ADMIN') ?? false;
  const isCustomerAdmin = user?.roles.includes('CUSTOMER_ADMIN') ?? false;
  const isCustomerUser = user?.roles.includes('CUSTOMER_USER') ?? false;

  useEffect(() => {
    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === 'token' && !e.newValue) {
        setToken(null);
        setUser(null);
      }
    };
    const handleSessionCleared = () => {
      setToken(null);
      setUser(null);
    };
    window.addEventListener('storage', handleStorageChange);
    window.addEventListener(SESSION_CLEARED_EVENT, handleSessionCleared);
    return () => {
      window.removeEventListener('storage', handleStorageChange);
      window.removeEventListener(SESSION_CLEARED_EVENT, handleSessionCleared);
    };
  }, []);

  useEffect(() => {
    if (!token) return undefined;

    const expiresAt = getJwtExpirationMs(token);
    if (expiresAt === undefined) return undefined;

    let timer: number | undefined;
    const expireIfNeeded = () => {
      if (Date.now() >= expiresAt) {
        setToken(null);
        setUser(null);
        terminateSession('session_expired');
        return true;
      }
      return false;
    };
    const scheduleExpiry = () => {
      if (timer !== undefined) window.clearTimeout(timer);
      const remaining = expiresAt - Date.now();
      if (remaining <= 0) {
        expireIfNeeded();
        return;
      }
      // Browsers clamp longer timers; reschedule when that boundary is reached.
      timer = window.setTimeout(scheduleExpiry, Math.min(remaining, 2_147_000_000));
    };
    const handleFocus = () => {
      if (!expireIfNeeded()) scheduleExpiry();
    };
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') handleFocus();
    };

    scheduleExpiry();
    window.addEventListener('focus', handleFocus);
    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => {
      if (timer !== undefined) window.clearTimeout(timer);
      window.removeEventListener('focus', handleFocus);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [token]);

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated,
        login,
        logout,
        hasRole,
        isSuperAdmin,
        isTenantAdmin,
        isCustomerAdmin,
        isCustomerUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
