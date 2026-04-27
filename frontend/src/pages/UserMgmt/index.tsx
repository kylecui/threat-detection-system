import { useState, useEffect, useCallback, useMemo } from 'react';
import { Card, Table, Button, Modal, Form, Input, Select, Space, Tag, Popconfirm, message } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import type { ManagedUser, CreateUserRequest, UpdateUserRequest, Tenant } from '@/types';
import { UserRole } from '@/types';
import { listUsers, createUser, updateUser, deleteUser } from '@/services/user';
import { listTenants } from '@/services/tenant';
import { usePermission } from '@/hooks/usePermission';
import PermissionGate from '@/components/PermissionGate';

export default function UserMgmt() {
  const { t } = useTranslation();
  const [users, setUsers] = useState<ManagedUser[]>([]);
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<ManagedUser | null>(null);
  const [form] = Form.useForm();

  const { isSuperAdmin } = usePermission();

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
      message.success(t('userMgmt.messageUserDeleted'));
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
        message.success(t('userMgmt.messageUserUpdated'));
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
        message.success(t('userMgmt.messageUserCreated'));
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
    { title: t('common.id'), dataIndex: 'id', key: 'id', width: 60 },
    { title: t('userMgmt.username'), dataIndex: 'username', key: 'username' },
    { title: t('userMgmt.displayName'), dataIndex: 'displayName', key: 'displayName' },
    { title: t('common.email'), dataIndex: 'email', key: 'email' },
    { title: t('common.customerId'), dataIndex: 'customerId', key: 'customerId' },
    {
      title: t('common.tenant'), dataIndex: 'tenantId', key: 'tenantId',
      render: (tid: number | null) => tid ? (tenantMap[tid] || `#${tid}`) : '-',
    },
    {
      title: t('common.role'), dataIndex: 'roles', key: 'roles',
      render: (roles: string[]) => roles?.map(r => (
        <Tag key={r} color={roleColor[r] || 'default'}>{r}</Tag>
      )),
    },
    {
      title: t('common.status'), dataIndex: 'enabled', key: 'enabled', width: 80,
      render: (v: boolean) => <Tag color={v ? 'green' : 'red'}>{v ? t('common.enabled') : t('common.disabled')}</Tag>,
    },
    {
      title: t('common.actions'), key: 'actions', width: 140,
      render: (_: unknown, record: ManagedUser) => (
        <Space>
          <PermissionGate requiredRoles={['SUPER_ADMIN', 'TENANT_ADMIN']}>
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
              {t('common.edit')}
            </Button>
          </PermissionGate>
          <PermissionGate requiredRoles={['SUPER_ADMIN']}>
            <Popconfirm title={t('userMgmt.confirmDeleteUser')} onConfirm={() => handleDelete(record.id)}>
              <Button type="link" size="small" danger icon={<DeleteOutlined />}>{t('common.delete')}</Button>
            </Popconfirm>
          </PermissionGate>
        </Space>
      ),
    },
  ];

  const availableRoles = isSuperAdmin
    ? [UserRole.SUPER_ADMIN, UserRole.TENANT_ADMIN, UserRole.CUSTOMER_USER]
    : [UserRole.TENANT_ADMIN, UserRole.CUSTOMER_USER];

  return (
    <Card
      title={t('userMgmt.title')}
      extra={(
        <PermissionGate requiredRoles={['SUPER_ADMIN', 'TENANT_ADMIN']}>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>{t('userMgmt.createUser')}</Button>
        </PermissionGate>
      )}
    >
      <Table
        rowKey="id"
        columns={columns}
        dataSource={users}
        loading={loading}
        pagination={{ pageSize: 10, showSizeChanger: true }}
      />

      <Modal
        title={editingUser ? t('userMgmt.editUser') : t('userMgmt.createUser')}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        destroyOnClose
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="username"
            label={t('userMgmt.username')}
            rules={[{ required: !editingUser, message: t('userMgmt.validationUsernameRequired') }]}
          >
            <Input placeholder={t('userMgmt.placeholderLoginUsername')} disabled={!!editingUser} />
          </Form.Item>
          <Form.Item
            name="password"
            label={editingUser ? t('userMgmt.newPasswordOptional') : t('common.password')}
            rules={editingUser ? [] : [{ required: true, message: t('userMgmt.validationPasswordRequired') }]}
          >
            <Input.Password placeholder={editingUser ? t('common.keepEmptyToKeepValue') : t('userMgmt.placeholderEnterPassword')} />
          </Form.Item>
          <Form.Item name="displayName" label={t('userMgmt.displayName')}>
            <Input placeholder={t('userMgmt.placeholderDisplayName')} />
          </Form.Item>
          <Form.Item name="email" label={t('common.email')}>
            <Input placeholder="user@example.com" />
          </Form.Item>
          <Form.Item name="customerId" label={t('common.customerId')}>
            <Input placeholder={t('userMgmt.placeholderCustomerId')} />
          </Form.Item>
          {isSuperAdmin && !editingUser && (
            <Form.Item name="tenantId" label={t('userMgmt.tenantBelonging')}>
              <Select allowClear placeholder={t('userMgmt.selectTenant')}>
                {tenants.map(t => (
                  <Select.Option key={t.id} value={t.id}>{t.name} ({t.tenantId})</Select.Option>
                ))}
              </Select>
            </Form.Item>
          )}
          <Form.Item
            name="role"
            label={t('common.role')}
            rules={[{ required: !editingUser, message: t('userMgmt.validationRoleRequired') }]}
            initialValue="CUSTOMER_USER"
          >
            <Select>
              {availableRoles.map(r => (
                <Select.Option key={r} value={r}>{r}</Select.Option>
              ))}
            </Select>
          </Form.Item>
          {editingUser && (
            <Form.Item name="enabled" label={t('userMgmt.enableStatus')} initialValue={true}>
              <Select>
                <Select.Option value={true}>{t('common.enabled')}</Select.Option>
                <Select.Option value={false}>{t('common.disabled')}</Select.Option>
              </Select>
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Card>
  );
}
