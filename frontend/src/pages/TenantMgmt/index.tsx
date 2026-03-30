import { useState, useEffect, useCallback } from 'react';
import { Card, Table, Button, Modal, Form, Input, Select, Space, Tag, Popconfirm, message } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { Tenant, CreateTenantRequest, UpdateTenantRequest } from '@/types';
import { TenantStatus } from '@/types';
import { listTenants, createTenant, updateTenant, deleteTenant } from '@/services/tenant';

export default function TenantMgmt() {
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
      message.success('租户已删除');
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
        message.success('租户已更新');
      } else {
        const req: CreateTenantRequest = {
          tenantId: values.tenantId,
          name: values.name,
          description: values.description,
          contactEmail: values.contactEmail,
          maxCustomers: values.maxCustomers,
        };
        await createTenant(req);
        message.success('租户已创建');
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
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: '租户标识', dataIndex: 'tenantId', key: 'tenantId' },
    { title: '名称', dataIndex: 'name', key: 'name' },
    { title: '联系邮箱', dataIndex: 'contactEmail', key: 'contactEmail' },
    { title: '最大客户数', dataIndex: 'maxCustomers', key: 'maxCustomers', width: 100 },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (s: string) => <Tag color={statusColor[s] || 'default'}>{s}</Tag>,
    },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
    {
      title: '操作', key: 'actions', width: 140,
      render: (_: unknown, record: Tenant) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该租户?" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="租户管理"
      extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建租户</Button>}
    >
      <Table
        rowKey="id"
        columns={columns}
        dataSource={tenants}
        loading={loading}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />

      <Modal
        title={editingTenant ? '编辑租户' : '新建租户'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="tenantId"
            label="租户标识"
            rules={[{ required: true, message: '请输入租户标识' }]}
          >
            <Input placeholder="如: tenant-acme" disabled={!!editingTenant} />
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="租户名称" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="可选描述" />
          </Form.Item>
          <Form.Item name="contactEmail" label="联系邮箱">
            <Input placeholder="admin@example.com" />
          </Form.Item>
          <Form.Item name="maxCustomers" label="最大客户数" initialValue={10}>
            <Input type="number" />
          </Form.Item>
          {editingTenant && (
            <Form.Item name="status" label="状态">
              <Select>
                <Select.Option value={TenantStatus.ACTIVE}>启用</Select.Option>
                <Select.Option value={TenantStatus.SUSPENDED}>暂停</Select.Option>
                <Select.Option value={TenantStatus.DISABLED}>禁用</Select.Option>
              </Select>
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Card>
  );
}
