import { useState, useEffect, useCallback } from 'react';
import { Card, Table, Button, Modal, Form, Input, Select, Space, Tag, Popconfirm, message } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { Tenant, CreateTenantRequest, UpdateTenantRequest } from '@/types';
import { TenantStatus } from '@/types';
import { listTenants, createTenant, updateTenant, deleteTenant } from '@/services/tenant';

export default function TenantMgmt() {
  const { t } = useTranslation();
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingTenant, setEditingTenant] = useState<Tenant | null>(null);
  const [form] = Form.useForm();

  const fetchTenants = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listTenants();
      setTenants(Array.isArray(data) ? data : []);
    } catch {
      // api interceptor shows error
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { fetchTenants(); }, [fetchTenants]);

  const handleCreate = () => {
    setEditingTenant(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (tenant: Tenant) => {
    setEditingTenant(tenant);
    form.setFieldsValue({
      tenantId: tenant.tenantId,
      name: tenant.name,
      description: tenant.description,
      contactEmail: tenant.contactEmail,
      maxCustomers: tenant.maxCustomers,
      status: tenant.status,
    });
    setModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteTenant(id);
      message.success(t('tenantMgmt.messageTenantDeleted'));
      fetchTenants();
    } catch {
      // handled by interceptor
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (editingTenant) {
        const req: UpdateTenantRequest = {
          name: values.name,
          description: values.description,
          contactEmail: values.contactEmail,
          maxCustomers: values.maxCustomers,
          status: values.status,
        };
        await updateTenant(editingTenant.id, req);
        message.success(t('tenantMgmt.messageTenantUpdated'));
      } else {
        const req: CreateTenantRequest = {
          tenantId: values.tenantId,
          name: values.name,
          description: values.description,
          contactEmail: values.contactEmail,
          maxCustomers: values.maxCustomers,
        };
        await createTenant(req);
        message.success(t('tenantMgmt.messageTenantCreated'));
      }
      setModalVisible(false);
      fetchTenants();
    } catch {
      // validation error or api error
    }
  };

  const statusColor: Record<string, string> = {
    ACTIVE: 'green',
    SUSPENDED: 'orange',
    DISABLED: 'red',
  };

  const columns = [
    { title: t('common.id'), dataIndex: 'id', key: 'id', width: 60 },
    { title: t('tenantMgmt.tenantIdentifier'), dataIndex: 'tenantId', key: 'tenantId' },
    { title: t('common.name'), dataIndex: 'name', key: 'name' },
    { title: t('tenantMgmt.contactEmail'), dataIndex: 'contactEmail', key: 'contactEmail' },
    { title: t('tenantMgmt.maxCustomers'), dataIndex: 'maxCustomers', key: 'maxCustomers', width: 100 },
    {
      title: t('common.status'), dataIndex: 'status', key: 'status', width: 100,
      render: (s: string) => <Tag color={statusColor[s] || 'default'}>{s}</Tag>,
    },
    { title: t('common.createdAt'), dataIndex: 'createdAt', key: 'createdAt', width: 180 },
    {
      title: t('common.actions'), key: 'actions', width: 140,
      render: (_: unknown, record: Tenant) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            {t('common.edit')}
          </Button>
          <Popconfirm title={t('tenantMgmt.confirmDeleteTenant')} onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>{t('common.delete')}</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title={t('tenantMgmt.title')}
      extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>{t('tenantMgmt.createTenant')}</Button>}
    >
      <Table
        rowKey="id"
        columns={columns}
        dataSource={tenants}
        loading={loading}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />

      <Modal
        title={editingTenant ? t('tenantMgmt.editTenant') : t('tenantMgmt.createTenant')}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="tenantId"
            label={t('tenantMgmt.tenantIdentifier')}
            rules={[{ required: true, message: t('tenantMgmt.validationTenantIdRequired') }]}
          >
            <Input placeholder={t('tenantMgmt.placeholderTenantId')} disabled={!!editingTenant} />
          </Form.Item>
          <Form.Item name="name" label={t('common.name')} rules={[{ required: true, message: t('tenantMgmt.validationNameRequired') }]}> 
            <Input placeholder={t('tenantMgmt.placeholderTenantName')} />
          </Form.Item>
          <Form.Item name="description" label={t('common.description')}>
            <Input.TextArea rows={2} placeholder={t('tenantMgmt.placeholderOptionalDescription')} />
          </Form.Item>
          <Form.Item name="contactEmail" label={t('tenantMgmt.contactEmail')}>
            <Input placeholder="admin@example.com" />
          </Form.Item>
          <Form.Item name="maxCustomers" label={t('tenantMgmt.maxCustomers')} initialValue={10}>
            <Input type="number" />
          </Form.Item>
          {editingTenant && (
            <Form.Item name="status" label={t('common.status')}>
              <Select>
                <Select.Option value={TenantStatus.ACTIVE}>{t('common.enabled')}</Select.Option>
                <Select.Option value={TenantStatus.SUSPENDED}>{t('tenantMgmt.suspended')}</Select.Option>
                <Select.Option value={TenantStatus.DISABLED}>{t('common.disabled')}</Select.Option>
              </Select>
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Card>
  );
}
