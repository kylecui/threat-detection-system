import { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  Input,
  List,
  Modal,
  Pagination,
  Row,
  Space,
  Spin,
  Tag,
  Tooltip,
  Typography,
} from 'antd';
import {
  ApartmentOutlined,
  CheckOutlined,
  DownOutlined,
  ReloadOutlined,
  SearchOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useAuth } from '@/contexts/AuthContext';
import { useScope } from '@/contexts/ScopeContext';
import type { Customer, Tenant } from '@/types';

const PAGE_SIZE = 20;

export default function ScopeSelector() {
  const { t } = useTranslation();
  const { isSuperAdmin, isTenantAdmin, user } = useAuth();
  const {
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
  } = useScope();

  const [open, setOpen] = useState(false);
  const [tenantKeyword, setTenantKeyword] = useState('');
  const [customerKeyword, setCustomerKeyword] = useState('');
  const [tenantPage, setTenantPage] = useState(1);
  const [customerPage, setCustomerPage] = useState(1);
  const [tenantResults, setTenantResults] = useState<Tenant[]>(tenants);
  const [customerResults, setCustomerResults] = useState<Customer[]>(customers);
  const [tenantTotal, setTenantTotal] = useState(tenants.length);
  const [customerTotal, setCustomerTotal] = useState(customers.length);
  const [searchingTenants, setSearchingTenants] = useState(false);
  const [searchingCustomers, setSearchingCustomers] = useState(false);
  const [tenantSearchFailed, setTenantSearchFailed] = useState(false);
  const [customerSearchFailed, setCustomerSearchFailed] = useState(false);

  const effectiveTenantId = isSuperAdmin ? tenantId : user?.tenantId;
  const allTenantOptions = useMemo(
    () => [...tenantResults, ...tenants].filter(
      (item, index, array) => array.findIndex((other) => other.id === item.id) === index,
    ),
    [tenantResults, tenants],
  );
  const allCustomerOptions = useMemo(
    () => [...customerResults, ...customers].filter(
      (item, index, array) => array.findIndex((other) => other.customerId === item.customerId) === index,
    ),
    [customerResults, customers],
  );
  const selectedTenant = allTenantOptions.find((item) => item.id === effectiveTenantId);
  const selectedCustomer = allCustomerOptions.find((item) => item.customerId === customerId);

  useEffect(() => {
    setTenantResults(tenants);
    setTenantTotal((current) => Math.max(current, tenants.length));
  }, [tenants]);

  useEffect(() => {
    setCustomerResults(customers);
    setCustomerTotal((current) => Math.max(current, customers.length));
  }, [customers]);

  useEffect(() => {
    if (!open || !isSuperAdmin) return;
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      setSearchingTenants(true);
      setTenantSearchFailed(false);
      try {
        const result = await searchTenants(tenantKeyword, tenantPage - 1, PAGE_SIZE);
        if (!cancelled) {
          setTenantResults(result.content || []);
          setTenantTotal(result.totalElements || 0);
        }
      } catch {
        if (!cancelled) setTenantSearchFailed(true);
      } finally {
        if (!cancelled) setSearchingTenants(false);
      }
    }, 300);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [isSuperAdmin, open, searchTenants, tenantKeyword, tenantPage]);

  useEffect(() => {
    if (!open || !effectiveTenantId) {
      if (!effectiveTenantId) {
        setCustomerResults([]);
        setCustomerTotal(0);
      }
      return;
    }
    let cancelled = false;
    const timer = window.setTimeout(async () => {
      setSearchingCustomers(true);
      setCustomerSearchFailed(false);
      try {
        const result = await searchCustomers(
          effectiveTenantId,
          customerKeyword,
          customerPage - 1,
          PAGE_SIZE,
        );
        if (!cancelled) {
          setCustomerResults(result.content || []);
          setCustomerTotal(result.totalElements || 0);
        }
      } catch {
        if (!cancelled) setCustomerSearchFailed(true);
      } finally {
        if (!cancelled) setSearchingCustomers(false);
      }
    }, 300);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [customerKeyword, customerPage, effectiveTenantId, open, searchCustomers]);

  if (!isSuperAdmin && !isTenantAdmin) return null;

  const reload = async () => {
    if (isSuperAdmin) await refreshTenants().catch(() => undefined);
    if (effectiveTenantId) await refreshCustomers(effectiveTenantId).catch(() => undefined);
    setTenantPage(1);
    setCustomerPage(1);
  };

  const scopeLabel = selectedCustomer
    ? `${selectedTenant?.name || t('scope.tenant')} / ${selectedCustomer.name}`
    : effectiveTenantId
      ? `${selectedTenant?.name || `${t('scope.tenant')} #${effectiveTenantId}`} / ${t('scope.allCustomers')}`
      : t('scope.selectScope');

  return (
    <Space size={4} style={{ marginLeft: 16 }}>
      <Button
        size="small"
        icon={<ApartmentOutlined />}
        onClick={() => setOpen(true)}
        style={{ maxWidth: 340 }}
      >
        <span
          style={{
            display: 'inline-block',
            maxWidth: 270,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            verticalAlign: 'bottom',
            whiteSpace: 'nowrap',
          }}
        >
          {scopeLabel}
        </span>
        <DownOutlined style={{ marginLeft: 6, fontSize: 10 }} />
      </Button>
      <Tooltip title={t('common.refresh')}>
        <Button
          size="small"
          type="text"
          icon={<ReloadOutlined />}
          loading={loadingTenants || loadingCustomers}
          onClick={() => void reload()}
          aria-label={t('common.refresh')}
        />
      </Tooltip>

      <Modal
        title={
          <Space>
            <ApartmentOutlined />
            <span>{t('scope.chooseDataScope')}</span>
          </Space>
        }
        open={open}
        onCancel={() => setOpen(false)}
        width={isSuperAdmin ? 820 : 520}
        footer={<Button type="primary" onClick={() => setOpen(false)}>{t('common.done')}</Button>}
        destroyOnHidden
      >
        <Alert
          type="info"
          showIcon
          message={t('scope.scopeHint')}
          style={{ marginBottom: 16 }}
        />
        <Row gutter={16}>
          {isSuperAdmin && (
            <Col span={11}>
              <Card size="small" title={<Space><ApartmentOutlined />{t('scope.tenant')}</Space>}>
                <Input
                  allowClear
                  prefix={<SearchOutlined />}
                  placeholder={t('scope.searchTenant')}
                  value={tenantKeyword}
                  onChange={(event) => {
                    setTenantKeyword(event.target.value);
                    setTenantPage(1);
                  }}
                  style={{ marginBottom: 10 }}
                />
                {tenantSearchFailed || tenantsError ? (
                  <Alert type="error" showIcon message={t('scope.tenantLoadFailed')} />
                ) : (
                  <Spin spinning={searchingTenants}>
                    <List
                      size="small"
                      style={{ height: 300, overflowY: 'auto' }}
                      dataSource={tenantResults}
                      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('common.noData')} /> }}
                      renderItem={(tenant) => {
                        const selected = tenant.id === tenantId;
                        const disabled = tenant.status !== 'ACTIVE';
                        return (
                          <List.Item
                            className={`tds-scope-option${selected ? ' tds-scope-option--selected' : ''}${disabled ? ' tds-scope-option--disabled' : ''}`}
                            role="button"
                            tabIndex={disabled ? -1 : 0}
                            aria-pressed={selected}
                            aria-disabled={disabled}
                            style={{
                              cursor: disabled ? 'not-allowed' : 'pointer',
                              paddingInline: 10,
                              borderRadius: 6,
                              opacity: disabled ? 0.55 : 1,
                            }}
                            onClick={() => {
                              if (disabled) return;
                              setTenantId(tenant.id);
                              setCustomerKeyword('');
                              setCustomerPage(1);
                              setCustomerResults([]);
                            }}
                            onKeyDown={(event) => {
                              if (disabled || (event.key !== 'Enter' && event.key !== ' ')) return;
                              event.preventDefault();
                              setTenantId(tenant.id);
                              setCustomerKeyword('');
                              setCustomerPage(1);
                              setCustomerResults([]);
                            }}
                          >
                            <List.Item.Meta
                              title={<Typography.Text strong={selected}>{tenant.name}</Typography.Text>}
                              description={tenant.tenantId}
                            />
                            {selected ? <CheckOutlined /> : null}
                          </List.Item>
                        );
                      }}
                    />
                  </Spin>
                )}
                <Pagination
                  simple
                  size="small"
                  current={tenantPage}
                  pageSize={PAGE_SIZE}
                  total={tenantTotal}
                  onChange={setTenantPage}
                  style={{ marginTop: 10, textAlign: 'center' }}
                />
              </Card>
            </Col>
          )}

          <Col span={isSuperAdmin ? 13 : 24}>
            <Card size="small" title={<Space><TeamOutlined />{t('scope.customer')}</Space>}>
              <Input
                allowClear
                prefix={<SearchOutlined />}
                placeholder={t('scope.searchCustomer')}
                value={customerKeyword}
                disabled={!effectiveTenantId}
                onChange={(event) => {
                  setCustomerKeyword(event.target.value);
                  setCustomerPage(1);
                }}
                style={{ marginBottom: 10 }}
              />
              {!effectiveTenantId ? (
                <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('scope.selectTenantFirst')} />
              ) : customerSearchFailed || customersError ? (
                <Alert type="error" showIcon message={t('scope.customerLoadFailed')} />
              ) : (
                <Spin spinning={searchingCustomers}>
                  <div style={{ height: 300, overflowY: 'auto' }}>
                    <List.Item
                      className={`tds-scope-option${!customerId ? ' tds-scope-option--selected' : ''}`}
                      role="button"
                      tabIndex={0}
                      aria-pressed={!customerId}
                      style={{
                        cursor: 'pointer',
                        paddingInline: 10,
                        borderRadius: 6,
                        marginBottom: 4,
                      }}
                      onClick={() => setCustomerId(undefined)}
                      onKeyDown={(event) => {
                        if (event.key !== 'Enter' && event.key !== ' ') return;
                        event.preventDefault();
                        setCustomerId(undefined);
                      }}
                    >
                      <List.Item.Meta
                        title={<Typography.Text strong={!customerId}>{t('scope.allCustomers')}</Typography.Text>}
                        description={t('scope.allCustomersHint')}
                      />
                      {!customerId ? <Tag color="green">{t('scope.current')}</Tag> : null}
                    </List.Item>
                    <List
                      size="small"
                      dataSource={customerResults}
                      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('common.noData')} /> }}
                      renderItem={(customer) => {
                        const selected = customer.customerId === customerId;
                        const disabled = customer.status !== 'ACTIVE';
                        return (
                          <List.Item
                            className={`tds-scope-option${selected ? ' tds-scope-option--selected' : ''}${disabled ? ' tds-scope-option--disabled' : ''}`}
                            role="button"
                            tabIndex={disabled ? -1 : 0}
                            aria-pressed={selected}
                            aria-disabled={disabled}
                            style={{
                              cursor: disabled ? 'not-allowed' : 'pointer',
                              paddingInline: 10,
                              borderRadius: 6,
                              opacity: disabled ? 0.55 : 1,
                            }}
                            onClick={() => {
                              if (!disabled) setCustomerId(customer.customerId);
                            }}
                            onKeyDown={(event) => {
                              if (disabled || (event.key !== 'Enter' && event.key !== ' ')) return;
                              event.preventDefault();
                              setCustomerId(customer.customerId);
                            }}
                          >
                            <List.Item.Meta
                              title={<Typography.Text strong={selected}>{customer.name}</Typography.Text>}
                              description={`${customer.customerId}${customer.email ? ` · ${customer.email}` : ''}`}
                            />
                            {selected ? <CheckOutlined /> : null}
                          </List.Item>
                        );
                      }}
                    />
                  </div>
                </Spin>
              )}
              <Pagination
                simple
                size="small"
                current={customerPage}
                pageSize={PAGE_SIZE}
                total={customerTotal}
                disabled={!effectiveTenantId}
                onChange={setCustomerPage}
                style={{ marginTop: 10, textAlign: 'center' }}
              />
            </Card>
          </Col>
        </Row>
      </Modal>
    </Space>
  );
}
