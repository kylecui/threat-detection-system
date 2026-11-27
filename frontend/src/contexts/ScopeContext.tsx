import {
  createContext,
  useContext,
  useState,
  useCallback,
  useEffect,
  useMemo,
  type ReactNode,
} from 'react';
import Cookies from 'js-cookie';
import { useAuth } from './AuthContext';
import apiClient from '@/services/api';
import type { Customer, PaginatedResponse, Tenant } from '@/types';

export type ScopeMode = 'platform' | 'tenant' | 'customer';

interface ScopeState {
  tenantId: number | undefined;
  customerId: string | undefined;
}

interface ScopeContextValue extends ScopeState {
  tenants: Tenant[];
  customers: Customer[];
  loadingTenants: boolean;
  loadingCustomers: boolean;
  tenantsError: string | undefined;
  customersError: string | undefined;
  setTenantId: (id: number | undefined) => void;
  setCustomerId: (id: string | undefined) => void;
  refreshTenants: () => Promise<Tenant[]>;
  refreshCustomers: (tenantId?: number) => Promise<Customer[]>;
  searchTenants: (keyword: string, page?: number, size?: number) => Promise<PaginatedResponse<Tenant>>;
  searchCustomers: (tenantId: number, keyword: string, page?: number, size?: number) => Promise<PaginatedResponse<Customer>>;
  effectiveCustomerId: string | undefined;
  scopeMode: ScopeMode;
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
  return Number.isFinite(parsed) && parsed > 0 ? parsed : undefined;
}

function getCustomerCookieValue(): string | undefined {
  return Cookies.get(CUSTOMER_COOKIE_KEY) || undefined;
}

function isActiveTenant(tenant: Tenant): boolean {
  return tenant.status === 'ACTIVE';
}

export function ScopeProvider({ children }: { children: ReactNode }) {
  const { user, isSuperAdmin, isTenantAdmin } = useAuth();

  const [tenantId, setTenantIdState] = useState<number | undefined>(undefined);
  const [customerId, setCustomerIdState] = useState<string | undefined>(undefined);
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loadingTenants, setLoadingTenants] = useState(false);
  const [loadingCustomers, setLoadingCustomers] = useState(false);
  const [tenantsError, setTenantsError] = useState<string | undefined>(undefined);
  const [customersError, setCustomersError] = useState<string | undefined>(undefined);
  const [initialized, setInitialized] = useState(false);

  const persistTenant = useCallback((id: number | undefined) => {
    setTenantIdState(id);
    if (id !== undefined) {
      localStorage.setItem(TENANT_STORAGE_KEY, String(id));
      if (isSuperAdmin) Cookies.set(TENANT_COOKIE_KEY, String(id), COOKIE_OPTIONS);
    } else {
      localStorage.removeItem(TENANT_STORAGE_KEY);
      Cookies.remove(TENANT_COOKIE_KEY);
    }
  }, [isSuperAdmin]);

  const persistCustomer = useCallback((id: string | undefined) => {
    setCustomerIdState(id);
    if (id) {
      localStorage.setItem(CUSTOMER_STORAGE_KEY, id);
      if (isSuperAdmin || isTenantAdmin) {
        Cookies.set(CUSTOMER_COOKIE_KEY, id, COOKIE_OPTIONS);
      }
    } else {
      localStorage.removeItem(CUSTOMER_STORAGE_KEY);
      Cookies.remove(CUSTOMER_COOKIE_KEY);
    }

    // Administrators use only the explicit scope key. Keeping a legacy
    // customer_id would make "all customers" silently fall back to an old
    // customer and leak stale scope into later requests.
    if (isSuperAdmin || isTenantAdmin) {
      localStorage.removeItem('customer_id');
    }
  }, [isSuperAdmin, isTenantAdmin]);

  const setTenantId = useCallback((id: number | undefined) => {
    persistTenant(id);
    persistCustomer(undefined);
    setCustomers([]);
    setCustomersError(undefined);
  }, [persistCustomer, persistTenant]);

  const setCustomerId = useCallback((id: string | undefined) => {
    persistCustomer(id);
  }, [persistCustomer]);

  const searchTenants = useCallback(async (
    keyword: string,
    page = 0,
    size = 20,
  ): Promise<PaginatedResponse<Tenant>> => {
    const response = await apiClient.get<PaginatedResponse<Tenant>>('/api/v1/tenants/search', {
      params: { keyword, page, size },
      skipScope: true,
      maxRetries: 1,
      suppressGlobalError: true,
    });
    return response.data;
  }, []);

  const searchCustomers = useCallback(async (
    targetTenantId: number,
    keyword: string,
    page = 0,
    size = 20,
  ): Promise<PaginatedResponse<Customer>> => {
    const path = keyword.trim() ? '/api/v1/customers/search' : '/api/v1/customers';
    const response = await apiClient.get<PaginatedResponse<Customer>>(path, {
      params: { tenantId: targetTenantId, keyword: keyword.trim() || undefined, page, size },
      skipScope: true,
      maxRetries: 1,
      suppressGlobalError: true,
    });
    return response.data;
  }, []);

  const refreshTenants = useCallback(async (): Promise<Tenant[]> => {
    if (!isSuperAdmin) {
      setTenants([]);
      return [];
    }
    setLoadingTenants(true);
    setTenantsError(undefined);
    try {
      const page = await searchTenants('', 0, 50);
      const next = Array.isArray(page.content) ? page.content : [];
      setTenants(next);
      if (tenantId && !next.some((tenant) => tenant.id === tenantId && tenant.status === 'ACTIVE')) {
        persistTenant(undefined);
        persistCustomer(undefined);
        setCustomers([]);
      }
      return next;
    } catch (error) {
      setTenantsError('load_failed');
      throw error;
    } finally {
      setLoadingTenants(false);
    }
  }, [isSuperAdmin, persistCustomer, persistTenant, searchTenants, tenantId]);

  const refreshCustomers = useCallback(async (requestedTenantId?: number): Promise<Customer[]> => {
    const targetTenantId = requestedTenantId ?? tenantId ?? user?.tenantId;
    if (!targetTenantId || (!isSuperAdmin && !isTenantAdmin)) {
      setCustomers([]);
      setCustomersError(undefined);
      return [];
    }
    setLoadingCustomers(true);
    setCustomersError(undefined);
    try {
      const page = await searchCustomers(targetTenantId, '', 0, 50);
      const next = Array.isArray(page.content) ? page.content : [];
      setCustomers(next);
      if (customerId && !next.some((customer) => customer.customerId === customerId && customer.status === 'ACTIVE')) {
        persistCustomer(undefined);
      }
      return next;
    } catch (error) {
      setCustomersError('load_failed');
      throw error;
    } finally {
      setLoadingCustomers(false);
    }
  }, [customerId, isSuperAdmin, isTenantAdmin, persistCustomer, searchCustomers, tenantId, user?.tenantId]);

  useEffect(() => {
    let cancelled = false;

    const initializeScope = async () => {
      setInitialized(false);
      setTenantsError(undefined);
      setCustomersError(undefined);

      if (!user) {
        setTenants([]);
        setCustomers([]);
        persistTenant(undefined);
        persistCustomer(undefined);
        setInitialized(true);
        return;
      }

      try {
        if (isSuperAdmin) {
          localStorage.removeItem('customer_id');
          const tenantCookieId = parseTenantCookieValue();
          const customerCookieId = getCustomerCookieValue();
          const tenantPage = await searchTenants('', 0, 50);
          let nextTenants = Array.isArray(tenantPage.content) ? tenantPage.content : [];
          if (tenantCookieId !== undefined && !nextTenants.some((tenant) => tenant.id === tenantCookieId)) {
            const selectedResponse = await apiClient.get<Tenant>(`/api/v1/tenants/${tenantCookieId}`, {
              skipScope: true,
              suppressGlobalError: true,
              maxRetries: 1,
            }).catch(() => undefined);
            if (selectedResponse?.data) nextTenants = [selectedResponse.data, ...nextTenants];
          }
          const activeTenants = nextTenants.filter(isActiveTenant);
          const selectedTenantId = tenantCookieId !== undefined
            && activeTenants.some((tenant) => tenant.id === tenantCookieId)
            ? tenantCookieId
            : activeTenants[0]?.id;

          let nextCustomers: Customer[] = [];
          let selectedCustomerId: string | undefined;
          if (selectedTenantId !== undefined) {
            const customerPage = await searchCustomers(selectedTenantId, '', 0, 50);
            nextCustomers = Array.isArray(customerPage.content) ? customerPage.content : [];
            if (customerCookieId && !nextCustomers.some((customer) => customer.customerId === customerCookieId)) {
              const selectedResponse = await apiClient.get<Customer>(`/api/v1/customers/${customerCookieId}`, {
                skipScope: true,
                suppressGlobalError: true,
                maxRetries: 1,
              }).catch(() => undefined);
              if (selectedResponse?.data?.tenantId === selectedTenantId) {
                nextCustomers = [selectedResponse.data, ...nextCustomers];
              }
            }
            selectedCustomerId = customerCookieId
              && nextCustomers.some((customer) => customer.customerId === customerCookieId && customer.status === 'ACTIVE')
              ? customerCookieId
              : undefined;
          }

          if (cancelled) return;
          setTenants(nextTenants);
          setCustomers(nextCustomers);
          persistTenant(selectedTenantId);
          persistCustomer(selectedCustomerId);
        } else if (isTenantAdmin) {
          localStorage.removeItem('customer_id');
          const fixedTenantId = user.tenantId;
          const customerCookieId = getCustomerCookieValue();
          let nextCustomers: Customer[] = [];
          if (fixedTenantId !== undefined) {
            const customerPage = await searchCustomers(fixedTenantId, '', 0, 50);
            nextCustomers = Array.isArray(customerPage.content) ? customerPage.content : [];
            if (customerCookieId && !nextCustomers.some((customer) => customer.customerId === customerCookieId)) {
              const selectedResponse = await apiClient.get<Customer>(`/api/v1/customers/${customerCookieId}`, {
                skipScope: true,
                suppressGlobalError: true,
                maxRetries: 1,
              }).catch(() => undefined);
              if (selectedResponse?.data?.tenantId === fixedTenantId) {
                nextCustomers = [selectedResponse.data, ...nextCustomers];
              }
            }
          }
          const selectedCustomerId = customerCookieId
            && nextCustomers.some((customer) => customer.customerId === customerCookieId && customer.status === 'ACTIVE')
            ? customerCookieId
            : undefined;
          if (cancelled) return;
          setTenants([]);
          setCustomers(nextCustomers);
          persistTenant(fixedTenantId);
          persistCustomer(selectedCustomerId);
          Cookies.remove(TENANT_COOKIE_KEY);
        } else {
          if (cancelled) return;
          setTenants([]);
          setCustomers([]);
          persistTenant(user.tenantId);
          persistCustomer(user.customerId);
          Cookies.remove(TENANT_COOKIE_KEY);
          Cookies.remove(CUSTOMER_COOKIE_KEY);
        }
      } catch {
        if (cancelled) return;
        if (isSuperAdmin) setTenantsError('load_failed');
        if (isSuperAdmin || isTenantAdmin) setCustomersError('load_failed');
        if (!isSuperAdmin && !isTenantAdmin) {
          persistTenant(user.tenantId);
          persistCustomer(user.customerId);
        }
      } finally {
        if (!cancelled) setInitialized(true);
      }
    };

    void initializeScope();
    return () => { cancelled = true; };
  }, [user, isSuperAdmin, isTenantAdmin, persistCustomer, persistTenant, searchCustomers, searchTenants]);

  const effectiveCustomerId = isSuperAdmin || isTenantAdmin ? customerId : user?.customerId;
  const scopeMode = useMemo<ScopeMode>(() => {
    if (effectiveCustomerId) return 'customer';
    if (tenantId) return 'tenant';
    return 'platform';
  }, [effectiveCustomerId, tenantId]);

  return (
    <ScopeContext.Provider value={{
      tenantId,
      customerId,
      tenants,
      customers,
      loadingTenants,
      loadingCustomers,
      tenantsError,
      customersError,
      setTenantId,
      setCustomerId,
      refreshTenants,
      refreshCustomers,
      searchTenants,
      searchCustomers,
      effectiveCustomerId,
      scopeMode,
      initialized,
    }}>
      {children}
    </ScopeContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useScope(): ScopeContextValue {
  const ctx = useContext(ScopeContext);
  if (!ctx) throw new Error('useScope must be used within ScopeProvider');
  return ctx;
}
