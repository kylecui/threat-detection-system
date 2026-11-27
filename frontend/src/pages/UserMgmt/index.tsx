import { useState, useEffect, useCallback, useMemo } from "react";
import {
  Card,
  Table,
  Button,
  Modal,
  Form,
  Input,
  Select,
  Tag,
  Tooltip,
  Popconfirm,
  message,
  Alert,
} from "antd";
import { PlusOutlined, EditOutlined, DeleteOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";
import { formatDateTime } from "@/utils/formatDateTime";
import type {
  ManagedUser,
  CreateUserRequest,
  UpdateUserRequest,
  Customer,
} from "@/types";
import { UserRole } from "@/types";
import { listUsers, createUser, updateUser, deleteUser } from "@/services/user";
import customerService from "@/services/customer";
import { usePermission } from "@/hooks/usePermission";
import { useAuth } from "@/contexts/AuthContext";
import { useScope } from "@/contexts/ScopeContext";
import PermissionGate from "@/components/PermissionGate";
import useSubmitGuard from "@/hooks/useSubmitGuard";
import SemanticStatusTag from "@/components/SemanticStatusTag";
import TableActionButton from "@/components/TableActionButton";
import TableActions from "@/components/TableActions";
import TableCellText from "@/components/TableCellText";
import "../governance.css";

const CUSTOMER_ROLES = [UserRole.CUSTOMER_ADMIN, UserRole.CUSTOMER_USER];

export default function UserMgmt() {
  const { t } = useTranslation();
  const { user: caller } = useAuth();
  const { isSuperAdmin, isTenantAdmin, isCustomerAdmin } = usePermission();
  const { tenants, customers: scopeCustomers } = useScope();
  const [users, setUsers] = useState<ManagedUser[]>([]);
  const [formCustomers, setFormCustomers] = useState<Customer[]>([]);
  const [loading, setLoading] = useState(false);
  const [customerLoading, setCustomerLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingUser, setEditingUser] = useState<ManagedUser | null>(null);
  const [form] = Form.useForm();
  const mutation = useSubmitGuard();
  const selectedRole = Form.useWatch("role", form) as UserRole | undefined;
  const selectedTenantId = Form.useWatch("tenantId", form) as
    | number
    | undefined;
  const needsCustomer = !!selectedRole && CUSTOMER_ROLES.includes(selectedRole);

  const fetchData = useCallback(async () => {
    setLoading(true);
    try {
      const data = await listUsers();
      setUsers(Array.isArray(data) ? data : []);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchData();
  }, [fetchData]);

  useEffect(() => {
    if (!modalVisible || !needsCustomer) {
      setFormCustomers([]);
      return;
    }
    const tenantId = isSuperAdmin ? selectedTenantId : caller?.tenantId;
    if (!tenantId) {
      setFormCustomers([]);
      return;
    }
    if (
      !isSuperAdmin &&
      tenantId === caller?.tenantId &&
      scopeCustomers.length > 0
    ) {
      setFormCustomers(scopeCustomers);
      return;
    }
    setCustomerLoading(true);
    void customerService
      .getCustomersByTenant(tenantId)
      .then(setFormCustomers)
      .catch(() => setFormCustomers([]))
      .finally(() => setCustomerLoading(false));
  }, [
    caller?.tenantId,
    isSuperAdmin,
    modalVisible,
    needsCustomer,
    scopeCustomers,
    selectedTenantId,
  ]);

  const defaultRole = UserRole.CUSTOMER_USER;

  const handleCreate = () => {
    setEditingUser(null);
    form.resetFields();
    form.setFieldsValue({
      role: defaultRole,
      tenantId: isSuperAdmin ? undefined : caller?.tenantId,
      customerId: isCustomerAdmin ? caller?.customerId : undefined,
    });
    setModalVisible(true);
  };

  const handleEdit = (managedUser: ManagedUser) => {
    setEditingUser(managedUser);
    form.setFieldsValue({
      username: managedUser.username,
      displayName: managedUser.displayName,
      email: managedUser.email,
      customerId: managedUser.customerId,
      tenantId: managedUser.tenantId,
      role: managedUser.roles?.[0] || UserRole.CUSTOMER_USER,
      enabled: managedUser.enabled,
    });
    setModalVisible(true);
  };

  const handleDelete = async (id: number) => {
    if (!mutation.begin()) return;
    try {
      await deleteUser(id);
      message.success(t("userMgmt.messageUserDeleted"));
      await fetchData();
    } catch {
      // global interceptor shows the server reason
    } finally {
      mutation.end();
    }
  };

  const handleSubmit = async () => {
    if (!mutation.begin()) return;
    try {
      const values = await form.validateFields();
      const role = values.role as UserRole;
      const customerId = CUSTOMER_ROLES.includes(role)
        ? values.customerId
        : undefined;
      const tenantId =
        role === UserRole.SUPER_ADMIN
          ? undefined
          : isSuperAdmin
            ? values.tenantId
            : caller?.tenantId;

      if (editingUser) {
        const req: UpdateUserRequest = {
          displayName: values.displayName,
          email: values.email,
          customerId,
          role,
          enabled: values.enabled,
        };
        if (values.password) req.password = values.password;
        await updateUser(editingUser.id, req);
        message.success(t("userMgmt.messageUserUpdated"));
      } else {
        const req: CreateUserRequest = {
          username: values.username,
          password: values.password,
          displayName: values.displayName,
          email: values.email,
          customerId,
          tenantId,
          role,
        };
        await createUser(req);
        message.success(t("userMgmt.messageUserCreated"));
      }
      setModalVisible(false);
      await fetchData();
    } catch {
      // validation or API interceptor
    } finally {
      mutation.end();
    }
  };

  const roleColor: Record<string, string> = {
    SUPER_ADMIN: "red",
    TENANT_ADMIN: "blue",
    CUSTOMER_ADMIN: "purple",
    CUSTOMER_USER: "green",
  };

  const tenantMap = useMemo(
    () => Object.fromEntries(tenants.map((tenant) => [tenant.id, tenant.name])),
    [tenants],
  );
  const availableRoles = isSuperAdmin
    ? [
        UserRole.SUPER_ADMIN,
        UserRole.TENANT_ADMIN,
        UserRole.CUSTOMER_ADMIN,
        UserRole.CUSTOMER_USER,
      ]
    : isTenantAdmin
      ? [UserRole.TENANT_ADMIN, UserRole.CUSTOMER_ADMIN, UserRole.CUSTOMER_USER]
      : [UserRole.CUSTOMER_USER];
  const enabledUserCount = users.filter((managedUser) => managedUser.enabled).length;
  const disabledUserCount = users.length - enabledUserCount;
  const visibleRoleCount = new Set(users.flatMap((managedUser) => managedUser.roles || []))
    .size;

  const columns = [
    { title: t("common.id"), dataIndex: "id", key: "id", width: 72 },
    {
      title: t("userMgmt.username"),
      dataIndex: "username",
      key: "username",
      width: 150,
      render: (value?: string) => <TableCellText value={value} />,
    },
    {
      title: t("userMgmt.displayName"),
      dataIndex: "displayName",
      key: "displayName",
      width: 150,
      render: (value?: string) => <TableCellText value={value} />,
    },
    {
      title: t("common.email"),
      dataIndex: "email",
      key: "email",
      width: 210,
      render: (value?: string) => <TableCellText value={value} />,
    },
    {
      title: t("common.customerId"),
      dataIndex: "customerId",
      key: "customerId",
      width: 150,
      render: (value?: string) => value || "-",
    },
    {
      title: t("common.tenant"),
      dataIndex: "tenantId",
      key: "tenantId",
      width: 140,
      render: (value?: number) =>
        value ? tenantMap[value] || `#${value}` : "-",
    },
    {
      title: t("common.role"),
      dataIndex: "roles",
      key: "roles",
      width: 200,
      // Single-line role cell: first role + "+N"; the tooltip lists every role.
      render: (roles: string[]) => {
        if (!roles?.length) return "-";
        const [firstRole, ...otherRoles] = roles;
        const tags = (
          <span className="tds-table-tags">
            <Tag color={roleColor[firstRole] || "default"}>{firstRole}</Tag>
            {otherRoles.length > 0 && <Tag>+{otherRoles.length}</Tag>}
          </span>
        );
        return otherRoles.length > 0 ? (
          <Tooltip title={roles.join(", ")}>{tags}</Tooltip>
        ) : (
          tags
        );
      },
    },
    {
      title: t("common.createdAt"),
      dataIndex: "createdAt",
      key: "createdAt",
      width: 180,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: t("common.status"),
      dataIndex: "enabled",
      key: "enabled",
      width: 92,
      fixed: "right" as const,
      render: (value: boolean) => (
        <SemanticStatusTag tone={value ? "success" : "danger"}>
          {value ? t("common.enabled") : t("common.disabled")}
        </SemanticStatusTag>
      ),
    },
    {
      title: t("common.actions"),
      key: "actions",
      width: 108,
      fixed: "right" as const,
      render: (_: unknown, record: ManagedUser) => {
        const canManageRecord =
          !isCustomerAdmin ||
          (record.roles?.length === 1 &&
            record.roles[0] === UserRole.CUSTOMER_USER);
        if (!canManageRecord) return <Tag>{t("common.readOnly")}</Tag>;
        return (
          <TableActions label={t("common.actions")}>
            <PermissionGate
              requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN", "CUSTOMER_ADMIN"]}
            >
              <TableActionButton
                label={t("common.edit")}
                icon={<EditOutlined />}
                onClick={() => handleEdit(record)}
              />
            </PermissionGate>
            <PermissionGate
              requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN", "CUSTOMER_ADMIN"]}
            >
              <Popconfirm
                title={t("userMgmt.confirmDeleteUser")}
                onConfirm={() => handleDelete(record.id)}
              >
                <TableActionButton
                  label={t("common.delete")}
                  danger
                  icon={<DeleteOutlined />}
                />
              </Popconfirm>
            </PermissionGate>
          </TableActions>
        );
      },
    },
  ];

  return (
    <div className="tds-governance-page tds-user-management-page">
      <section className="tds-governance-metrics" aria-label={t("userMgmt.title")}>
        <div className="tds-governance-metric">
          <span>{t("userMgmt.title")}</span>
          <strong>{users.length}</strong>
        </div>
        <div className="tds-governance-metric tds-governance-metric--positive">
          <span>{t("common.enabled")}</span>
          <strong>{enabledUserCount}</strong>
        </div>
        <div className="tds-governance-metric">
          <span>{t("common.disabled")}</span>
          <strong>{disabledUserCount}</strong>
        </div>
        <div className="tds-governance-metric">
          <span>{t("common.role")}</span>
          <strong>{visibleRoleCount}</strong>
        </div>
      </section>

      <Card
        className="tds-governance-panel tds-governance-list-panel"
        title={t("userMgmt.title")}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            {t("userMgmt.createUser")}
          </Button>
        }
      >
        <Table
          className="tds-governance-table"
          rowKey="id"
          columns={columns}
          dataSource={users}
          loading={loading}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          tableLayout="fixed"
          scroll={{ x: 1460 }}
        />

        <Modal
        title={editingUser ? t("userMgmt.editUser") : t("userMgmt.createUser")}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => !mutation.submitting && setModalVisible(false)}
        confirmLoading={mutation.submitting}
        cancelButtonProps={{ disabled: mutation.submitting }}
        destroyOnHidden
        width={620}
      >
        <Form form={form} layout="vertical">
          {isCustomerAdmin && (
            <Alert
              type="info"
              showIcon
              message={`仅可管理客户 ${caller?.customerId || "-"} 下的普通用户`}
              style={{ marginBottom: 16 }}
            />
          )}
          <Form.Item
            name="username"
            label={t("userMgmt.username")}
            rules={[
              {
                required: !editingUser,
                message: t("userMgmt.validationUsernameRequired"),
              },
            ]}
          >
            <Input
              placeholder={t("userMgmt.placeholderLoginUsername")}
              disabled={!!editingUser}
            />
          </Form.Item>
          <Form.Item
            name="password"
            label={
              editingUser
                ? t("userMgmt.newPasswordOptional")
                : t("common.password")
            }
            rules={[
              ...(editingUser
                ? []
                : [
                    {
                      required: true,
                      message: t("userMgmt.validationPasswordRequired"),
                    },
                  ]),
              {
                validator: async (_, value?: string) => {
                  if (!value && editingUser) return;
                  if (!value || value.length < 8 || value.length > 16) {
                    throw new Error(t("userMgmt.validationPasswordPolicy"));
                  }
                },
              },
            ]}
          >
            <Input.Password
              placeholder={
                editingUser
                  ? t("common.keepEmptyToKeepValue")
                  : t("userMgmt.placeholderEnterPassword")
              }
            />
          </Form.Item>
          <Form.Item name="displayName" label={t("userMgmt.displayName")}>
            <Input />
          </Form.Item>
          <Form.Item
            name="email"
            label={t("common.email")}
            rules={[{ type: "email" }]}
          >
            <Input
              type="email"
              inputMode="email"
              autoComplete="off"
              spellCheck={false}
              placeholder="analyst@corp.internal"
            />
          </Form.Item>
          <Form.Item
            name="role"
            label={t("common.role")}
            rules={[{ required: true }]}
          >
            <Select
              options={availableRoles.map((role) => ({
                label: role,
                value: role,
              }))}
            />
          </Form.Item>
          {selectedRole !== UserRole.SUPER_ADMIN && (
            <Form.Item
              name="tenantId"
              label={t("userMgmt.tenantBelonging")}
              rules={[{ required: true }]}
            >
              <Select
                disabled={!isSuperAdmin || !!editingUser}
                placeholder={t("userMgmt.selectTenant")}
                options={
                  isSuperAdmin
                    ? tenants
                        .filter((tenant) => tenant.status === "ACTIVE")
                        .map((tenant) => ({
                          label: `${tenant.name} (${tenant.tenantId})`,
                          value: tenant.id,
                        }))
                    : caller?.tenantId
                      ? [
                          {
                            label: `#${caller.tenantId}`,
                            value: caller.tenantId,
                          },
                        ]
                      : []
                }
                onChange={() => form.setFieldValue("customerId", undefined)}
              />
            </Form.Item>
          )}
          {needsCustomer && (
            <Form.Item
              name="customerId"
              label={t("common.customerId")}
              rules={[{ required: true }]}
            >
              <Select
                showSearch
                disabled={isCustomerAdmin}
                loading={customerLoading}
                optionFilterProp="label"
                options={(isCustomerAdmin && caller?.customerId
                  ? [
                      {
                        customerId: caller.customerId,
                        name: caller.customerId,
                      } as Customer,
                    ]
                  : formCustomers
                ).map((customer) => ({
                  label: `${customer.name} (${customer.customerId})`,
                  value: customer.customerId,
                }))}
              />
            </Form.Item>
          )}
          {editingUser && (
            <Form.Item name="enabled" label={t("userMgmt.enableStatus")}>
              <Select
                options={[
                  { label: t("common.enabled"), value: true },
                  { label: t("common.disabled"), value: false },
                ]}
              />
            </Form.Item>
          )}
        </Form>
        </Modal>
      </Card>
    </div>
  );
}
