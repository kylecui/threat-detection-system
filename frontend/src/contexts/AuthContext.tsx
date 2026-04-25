import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';

export type AppRole = 'SUPER_ADMIN' | 'TENANT_ADMIN' | 'CUSTOMER_USER';

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
    localStorage.removeItem('token');
    localStorage.removeItem('refreshToken');
    localStorage.removeItem('user');
    localStorage.removeItem('customer_id');
    localStorage.removeItem('tenant_id');
    setToken(null);
    setUser(null);
    window.location.href = '/login';
  }, []);

  const hasRole = useCallback((role: AppRole) => {
    return user?.roles.includes(role) ?? false;
  }, [user]);

  const isSuperAdmin = user?.roles.includes('SUPER_ADMIN') ?? false;
  const isTenantAdmin = user?.roles.includes('TENANT_ADMIN') ?? false;
  const isCustomerUser = user?.roles.includes('CUSTOMER_USER') ?? false;

  useEffect(() => {
    const handleStorageChange = (e: StorageEvent) => {
      if (e.key === 'token' && !e.newValue) {
        setToken(null);
        setUser(null);
      }
    };
    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, []);

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
        isCustomerUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
