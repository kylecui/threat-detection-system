import { createContext, useContext, useState, useCallback, useEffect, type ReactNode } from 'react';
import Cookies from 'js-cookie';
import { useAuth } from './AuthContext';
import apiClient from '@/services/api';
import type { Customer, Tenant } from '@/types';

interface ScopeState {
  tenantId: number | undefined;
  customerId: string | undefined;
}

interface ScopeContextValue extends ScopeState {
  setTenantId: (id: number | undefined) => void;
  setCustomerId: (id: string | undefined) => void;
  effectiveCustomerId: string | undefined;
  initialized: boolean;
}

const ScopeContext = createContext<ScopeContextValue | null>(null);

const TENANT_COOKIE_KEY = 'tds_selected_tenant';
const CUSTOMER_COOKIE_KEY = 'tds_selected_customer';
const TENANT_STORAGE_KEY = 'scope_tenant_id';
const CUSTOMER_STORAGE_KEY = 'scope_customer_id';
const COOKIE_OPTIONS = { expires: 30, sameSite: 'lax' as const };

function parseTenantCookieValue(): number | undefined {
  const raw = Cookies.get(TENANT_COOKIE_KEY);
  if (!raw) return undefined;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function getCustomerCookieValue(): string | undefined {
  return Cookies.get(CUSTOMER_COOKIE_KEY) || undefined;
}

export function ScopeProvider({ children }: { children: ReactNode }) {
  const { user, isSuperAdmin, isTenantAdmin } = useAuth();

  const [tenantId, setTenantIdState] = useState<number | undefined>(undefined);
  const [customerId, setCustomerIdState] = useState<string | undefined>(undefined);
  const [initialized, setInitialized] = useState(false);

  const setTenantId = useCallback((id: number | undefined) => {
    setTenantIdState(id);
    if (id !== undefined) {
      localStorage.setItem(TENANT_STORAGE_KEY, String(id));
      if (isSuperAdmin) {
        Cookies.set(TENANT_COOKIE_KEY, String(id), COOKIE_OPTIONS);
      } else {
        Cookies.remove(TENANT_COOKIE_KEY);
      }
    } else {
      localStorage.removeItem(TENANT_STORAGE_KEY);
      Cookies.remove(TENANT_COOKIE_KEY);
    }
  }, [isSuperAdmin]);

  const setCustomerId = useCallback((id: string | undefined) => {
    setCustomerIdState(id);
    if (id) {
      localStorage.setItem(CUSTOMER_STORAGE_KEY, id);
      if (isSuperAdmin || isTenantAdmin) {
        Cookies.set(CUSTOMER_COOKIE_KEY, id, COOKIE_OPTIONS);
      } else {
        Cookies.remove(CUSTOMER_COOKIE_KEY);
      }
    } else {
      localStorage.removeItem(CUSTOMER_STORAGE_KEY);
      Cookies.remove(CUSTOMER_COOKIE_KEY);
    }
  }, [isSuperAdmin, isTenantAdmin]);

  const effectiveCustomerId = (() => {
    if (isSuperAdmin || isTenantAdmin) return customerId;
    return user?.customerId;
  })();

  useEffect(() => {
    let cancelled = false;

    const initializeScope = async () => {
      setInitialized(false);

      try {
        if (!user) {
          setTenantIdState(undefined);
          setCustomerIdState(undefined);
          localStorage.removeItem(TENANT_STORAGE_KEY);
          localStorage.removeItem(CUSTOMER_STORAGE_KEY);
          Cookies.remove(TENANT_COOKIE_KEY);
          Cookies.remove(CUSTOMER_COOKIE_KEY);
          return;
        }

        if (isSuperAdmin) {
          const tenantCookieId = parseTenantCookieValue();
          const customerCookieId = getCustomerCookieValue();

          const tenantsRes = await apiClient.get<Tenant[]>('/api/v1/tenants');
          const tenants = Array.isArray(tenantsRes.data) ? tenantsRes.data : [];

          const validatedTenantId =
            tenantCookieId !== undefined && tenants.some((t) => t.id === tenantCookieId)
              ? tenantCookieId
              : tenants[0]?.id;

          let validatedCustomerId: string | undefined;
          if (validatedTenantId !== undefined) {
            const customersRes = await apiClient.get<Customer[]>(`/api/v1/customers/tenant/${validatedTenantId}`);
            const customers = Array.isArray(customersRes.data) ? customersRes.data : [];

            validatedCustomerId =
              customerCookieId && customers.some((c) => c.customerId === customerCookieId)
                ? customerCookieId
                : customers[0]?.customerId;
          }

          if (cancelled) return;

          setTenantIdState(validatedTenantId);
          if (validatedTenantId !== undefined) {
            localStorage.setItem(TENANT_STORAGE_KEY, String(validatedTenantId));
            Cookies.set(TENANT_COOKIE_KEY, String(validatedTenantId), COOKIE_OPTIONS);
          } else {
            localStorage.removeItem(TENANT_STORAGE_KEY);
            Cookies.remove(TENANT_COOKIE_KEY);
          }

          setCustomerIdState(validatedCustomerId);
          if (validatedCustomerId) {
            localStorage.setItem(CUSTOMER_STORAGE_KEY, validatedCustomerId);
            Cookies.set(CUSTOMER_COOKIE_KEY, validatedCustomerId, COOKIE_OPTIONS);
          } else {
            localStorage.removeItem(CUSTOMER_STORAGE_KEY);
            Cookies.remove(CUSTOMER_COOKIE_KEY);
          }

          return;
        }

        if (isTenantAdmin) {
          const fixedTenantId = user.tenantId;
          const customerCookieId = getCustomerCookieValue();
          let validatedCustomerId: string | undefined;

          if (fixedTenantId !== undefined) {
            const customersRes = await apiClient.get<Customer[]>(`/api/v1/customers/tenant/${fixedTenantId}`);
            const customers = Array.isArray(customersRes.data) ? customersRes.data : [];

            validatedCustomerId =
              customerCookieId && customers.some((c) => c.customerId === customerCookieId)
                ? customerCookieId
                : customers[0]?.customerId;
          }

          if (cancelled) return;

          setTenantIdState(fixedTenantId);
          if (fixedTenantId !== undefined) {
            localStorage.setItem(TENANT_STORAGE_KEY, String(fixedTenantId));
          } else {
            localStorage.removeItem(TENANT_STORAGE_KEY);
          }
          Cookies.remove(TENANT_COOKIE_KEY);

          setCustomerIdState(validatedCustomerId);
          if (validatedCustomerId) {
            localStorage.setItem(CUSTOMER_STORAGE_KEY, validatedCustomerId);
            Cookies.set(CUSTOMER_COOKIE_KEY, validatedCustomerId, COOKIE_OPTIONS);
          } else {
            localStorage.removeItem(CUSTOMER_STORAGE_KEY);
            Cookies.remove(CUSTOMER_COOKIE_KEY);
          }

          return;
        }

        if (cancelled) return;

        setTenantIdState(user.tenantId);
        if (user.tenantId !== undefined) {
          localStorage.setItem(TENANT_STORAGE_KEY, String(user.tenantId));
        } else {
          localStorage.removeItem(TENANT_STORAGE_KEY);
        }

        setCustomerIdState(user.customerId);
        if (user.customerId) {
          localStorage.setItem(CUSTOMER_STORAGE_KEY, user.customerId);
        } else {
          localStorage.removeItem(CUSTOMER_STORAGE_KEY);
        }

        Cookies.remove(TENANT_COOKIE_KEY);
        Cookies.remove(CUSTOMER_COOKIE_KEY);
      } catch {
        if (cancelled) return;

        if (!isSuperAdmin && !isTenantAdmin) {
          setTenantIdState(user?.tenantId);
          setCustomerIdState(user?.customerId);

          if (user?.tenantId !== undefined) {
            localStorage.setItem(TENANT_STORAGE_KEY, String(user.tenantId));
          } else {
            localStorage.removeItem(TENANT_STORAGE_KEY);
          }

          if (user?.customerId) {
            localStorage.setItem(CUSTOMER_STORAGE_KEY, user.customerId);
          } else {
            localStorage.removeItem(CUSTOMER_STORAGE_KEY);
          }
        }
      } finally {
        if (!cancelled) {
          setInitialized(true);
        }
      }
    };

    void initializeScope();

    return () => {
      cancelled = true;
    };
  }, [user, isSuperAdmin, isTenantAdmin]);

  return (
    <ScopeContext.Provider
      value={{
        tenantId,
        customerId,
        setTenantId,
        setCustomerId,
        effectiveCustomerId,
        initialized,
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
