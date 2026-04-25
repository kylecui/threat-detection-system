import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import { useAuth } from './AuthContext';

interface ScopeState {
  tenantId: number | undefined;
  customerId: string | undefined;
}

interface ScopeContextValue extends ScopeState {
  setTenantId: (id: number | undefined) => void;
  setCustomerId: (id: string | undefined) => void;
  effectiveCustomerId: string | undefined;
}

const ScopeContext = createContext<ScopeContextValue | null>(null);

export function ScopeProvider({ children }: { children: ReactNode }) {
  const { user, isSuperAdmin, isTenantAdmin } = useAuth();

  const [tenantId, setTenantIdState] = useState<number | undefined>(() => {
    const stored = localStorage.getItem('scope_tenant_id');
    return stored ? Number(stored) : user?.tenantId;
  });

  const [customerId, setCustomerIdState] = useState<string | undefined>(() => {
    const stored = localStorage.getItem('scope_customer_id');
    return stored || user?.customerId;
  });

  const setTenantId = useCallback((id: number | undefined) => {
    setTenantIdState(id);
    if (id !== undefined) {
      localStorage.setItem('scope_tenant_id', String(id));
    } else {
      localStorage.removeItem('scope_tenant_id');
    }
  }, []);

  const setCustomerId = useCallback((id: string | undefined) => {
    setCustomerIdState(id);
    if (id) {
      localStorage.setItem('scope_customer_id', id);
    } else {
      localStorage.removeItem('scope_customer_id');
    }
  }, []);

  const effectiveCustomerId = (() => {
    if (isSuperAdmin || isTenantAdmin) return customerId;
    return user?.customerId;
  })();

  useEffect(() => {
    if (!isSuperAdmin && !isTenantAdmin) {
      setTenantIdState(user?.tenantId);
      setCustomerIdState(user?.customerId);
    }
  }, [user, isSuperAdmin, isTenantAdmin]);

  return (
    <ScopeContext.Provider
      value={{
        tenantId,
        customerId,
        setTenantId,
        setCustomerId,
        effectiveCustomerId,
      }}
    >
      {children}
    </ScopeContext.Provider>
  );
}

export function useScope(): ScopeContextValue {
  const ctx = useContext(ScopeContext);
  if (!ctx) throw new Error('useScope must be used within ScopeProvider');
  return ctx;
}
