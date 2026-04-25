import { useEffect, useState } from 'react';
import { Select, Space } from 'antd';
import { ApartmentOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import Cookies from 'js-cookie';
import { useAuth } from '@/contexts/AuthContext';
import { useScope } from '@/contexts/ScopeContext';
import type { Customer, Tenant } from '@/types';
import apiClient from '@/services/api';

const CUSTOMER_COOKIE_KEY = 'tds_selected_customer';
const COOKIE_OPTIONS = { expires: 30, sameSite: 'lax' as const };

export default function ScopeSelector() {
  const { t } = useTranslation();
  const { isSuperAdmin, isTenantAdmin, user } = useAuth();
  const { tenantId, customerId, setTenantId, setCustomerId } = useScope();

  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [loadingTenants, setLoadingTenants] = useState(false);
  const [loadingCustomers, setLoadingCustomers] = useState(false);

  useEffect(() => {
    if (!isSuperAdmin) return;
    setLoadingTenants(true);
    apiClient
      .get<Tenant[]>('/api/v1/tenants')
      .then((res) => setTenants(Array.isArray(res.data) ? res.data : []))
      .catch(() => setTenants([]))
      .finally(() => setLoadingTenants(false));
  }, [isSuperAdmin]);

  useEffect(() => {
    if (!isSuperAdmin && !isTenantAdmin) return;
    const tid = isSuperAdmin ? tenantId : user?.tenantId;
    if (!tid) {
      setCustomers([]);
      return;
    }
    setLoadingCustomers(true);
    apiClient
      .get<Customer[]>(`/api/v1/customers/tenant/${tid}`)
      .then((res) => setCustomers(Array.isArray(res.data) ? res.data : []))
      .catch(() => setCustomers([]))
      .finally(() => setLoadingCustomers(false));
  }, [isSuperAdmin, isTenantAdmin, tenantId, user?.tenantId]);

  if (!isSuperAdmin && !isTenantAdmin) return null;

  return (
    <Space size="small" style={{ marginLeft: 16 }}>
      <ApartmentOutlined style={{ color: 'rgba(0,0,0,0.45)' }} />
      {isSuperAdmin && (
        <Select
          placeholder={t('scope.selectTenant')}
          style={{ width: 160 }}
          value={tenantId}
          onChange={(v) => {
            Cookies.remove(CUSTOMER_COOKIE_KEY);
            localStorage.removeItem('scope_customer_id');
            setTenantId(v);
            setCustomerId(undefined);
          }}
          loading={loadingTenants}
          allowClear
          options={tenants.map((t) => ({ label: t.name, value: t.id }))}
          size="small"
        />
      )}
      <Select
        placeholder={t('scope.selectCustomer')}
        style={{ width: 200 }}
        value={customerId}
        onChange={(value) => {
          if (value) {
            Cookies.set(CUSTOMER_COOKIE_KEY, value, COOKIE_OPTIONS);
          } else {
            Cookies.remove(CUSTOMER_COOKIE_KEY);
          }
          setCustomerId(value);
        }}
        loading={loadingCustomers}
        allowClear
        options={[
          { label: t('scope.allCustomers'), value: '__all__' },
          ...customers.map((c) => ({
            label: `${c.name} (${c.customerId})`,
            value: c.customerId,
          })),
        ]}
        size="small"
      />
    </Space>
  );
}
