import { useState, useEffect, useCallback, useMemo } from 'react';
import { Card, Table, Button, Modal, Form, Input, Select, Space, Tag, Popconfirm, message } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import type { ManagedUser, CreateUserRequest, UpdateUserRequest, Tenant } from '@/types';
import { UserRole } from '@/types';
import { listUsers, createUser, updateUser, deleteUser } from '@/services/user';
import { listTenants } from '@/services/tenant';

function getCurrentUserRoles(): string[] {
  try {
    const raw = localStorage.getItem('user');
    if (!raw) return [];
    const u = JSON.parse(raw);
    return u.roles || [];
  } catch {
    return [];
  }
}

export default function UserMgmt() {
  const [users, setUsers] = useState<ManagedUser[]>([]);
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<ManagedUser | null>(null);
  const [form] = Form.useForm();

  const isSuperAdmin = useMemo(() => getCurrentUserRoles().includes('SUPER_ADMIN'), []);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const [userData, tenantData] = await Promise.all([
        listUsers(),
        isSuperAdmin ? listTenants() : Promise.resolve([]),
      ]);
      setUsers(Array.isArray(userData) ? userData : []);
      setTenants(Array.isArray(tenantData) ? tenantData : []);
    } catch {
      // api interceptor
    } finally {
      setLoading(false);
    }
  }, [isSuperAdmin]);

  useEffect(() => { fetchData(); }, [fetchData]);

  const handleCreate = () => {
    setEditingUser(null);
    form.resetFields();
    setModalVisible(true);
  };

  const handleEdit = (user: ManagedUser) => {
    setEditingUser(user);
    form.setFieldsValue({
      username: user.username,
      displayName: user.displayName,
      email: user.email,
      customerId: user.customerId,
      tenantId: user.tenantId,
      role: user.roles?.[0] || 'CUSTOMER_USER',
      enabled: user.enabled,
    });
    setModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    try {
      await deleteUser(id);
      message.success('用户已删除');
      fetchData();
    } catch {
      // handled
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      if (editingUser) {
        const req: UpdateUserRequest = {
          displayName: values.displayName,
          email: values.email,
          customerId: values.customerId,
          role: values.role,
          enabled: values.enabled,
        };
        if (values.password) req.password = values.password;
        await updateUser(editingUser.id, req);
        message.success('用户已更新');
      } else {
        const req: CreateUserRequest = {
          username: values.username,
          password: values.password,
          displayName: values.displayName,
          email: values.email,
          customerId: values.customerId,
          tenantId: values.tenantId,
          role: values.role,
        };
        await createUser(req);
        message.success('用户已创建');
      }
      setModalVisible(false);
      fetchData();
    } catch {
      // validation or api error
    }
  };

  const roleColor: Record<string, string> = {
    SUPER_ADMIN: 'red',
    TENANT_ADMIN: 'blue',
    CUSTOMER_USER: 'green',
  };

  const tenantMap = useMemo(() => {
    const m: Record<number, string> = {};
    tenants.forEach(t => { m[t.id] = t.name; });
    return m;
  }, [tenants]);

  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: '用户名', dataIndex: 'username', key: 'username' },
    { title: '显示名', dataIndex: 'displayName', key: 'displayName' },
    { title: '邮箱', dataIndex: 'email', key: 'email' },
    { title: '客户ID', dataIndex: 'customerId', key: 'customerId' },
    {
      title: '租户', dataIndex: 'tenantId', key: 'tenantId',
      render: (tid: number | null) => tid ? (tenantMap[tid] || `#${tid}`) : '-',
    },
    {
      title: '角色', dataIndex: 'roles', key: 'roles',
      render: (roles: string[]) => roles?.map(r => (
        <Tag key={r} color={roleColor[r] || 'default'}>{r}</Tag>
      )),
    },
    {
      title: '状态', dataIndex: 'enabled', key: 'enabled', width: 80,
      render: (v: boolean) => <Tag color={v ? 'green' : 'red'}>{v ? '启用' : '禁用'}</Tag>,
    },
    {
      title: '操作', key: 'actions', width: 140,
      render: (_: unknown, record: ManagedUser) => (
        <Space>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
            编辑
          </Button>
          <Popconfirm title="确定删除该用户?" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const availableRoles = isSuperAdmin
    ? [UserRole.SUPER_ADMIN, UserRole.TENANT_ADMIN, UserRole.CUSTOMER_USER]
    : [UserRole.TENANT_ADMIN, UserRole.CUSTOMER_USER];

  return (
    <Card
      title="用户管理"
      extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建用户</Button>}
    >
      <Table
        rowKey="id"
        columns={columns}
        dataSource={users}
        loading={loading}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />

      <Modal
        title={editingUser ? '编辑用户' : '新建用户'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        destroyOnClose
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="username"
            label="用户名"
            rules={[{ required: !editingUser, message: '请输入用户名' }]}
          >
            <Input placeholder="登录用户名" disabled={!!editingUser} />
          </Form.Item>
          <Form.Item
            name="password"
            label={editingUser ? '新密码 (留空不修改)' : '密码'}
            rules={editingUser ? [] : [{ required: true, message: '请输入密码' }]}
          >
            <Input.Password placeholder={editingUser ? '留空保持不变' : '输入密码'} />
          </Form.Item>
          <Form.Item name="displayName" label="显示名">
            <Input placeholder="显示名称" />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input placeholder="user@example.com" />
          </Form.Item>
          <Form.Item name="customerId" label="客户ID">
            <Input placeholder="关联的客户标识 (如: demo-customer)" />
          </Form.Item>
          {isSuperAdmin && !editingUser && (
            <Form.Item name="tenantId" label="所属租户">
              <Select allowClear placeholder="选择租户">
                {tenants.map(t => (
                  <Select.Option key={t.id} value={t.id}>{t.name} ({t.tenantId})</Select.Option>
                ))}
              </Select>
            </Form.Item>
          )}
          <Form.Item
            name="role"
            label="角色"
            rules={[{ required: !editingUser, message: '请选择角色' }]}
            initialValue="CUSTOMER_USER"
          >
            <Select>
              {availableRoles.map(r => (
                <Select.Option key={r} value={r}>{r}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          {editingUser && (
            <Form.Item name="enabled" label="启用状态" initialValue={true}>
              <Select>
                <Select.Option value={true}>启用</Select.Option>
                <Select.Option value={false}>禁用</Select.Option>
              </Select>
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Card>
  );
}
