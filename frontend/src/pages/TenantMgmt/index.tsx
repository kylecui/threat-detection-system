import { useState, useEffect, useCallback } from "react";
import {
  Card,
  Table,
  Button,
  Modal,
  Form,
  Input,
  InputNumber,
  Select,
  Popconfirm,
  message,
} from "antd";
import { PlusOutlined, EditOutlined, DeleteOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";
import { formatDateTime } from "@/utils/formatDateTime";
import type { Tenant, CreateTenantRequest, UpdateTenantRequest } from "@/types";
import { TenantStatus } from "@/types";
import {
  listTenants,
  createTenant,
  updateTenant,
  deleteTenant,
} from "@/services/tenant";
import PermissionGate from "@/components/PermissionGate";
import { useScope } from "@/contexts/ScopeContext";
import useSubmitGuard from "@/hooks/useSubmitGuard";
import SemanticStatusTag, {
  type SemanticStatusTone,
} from "@/components/SemanticStatusTag";
import TableActionButton from "@/components/TableActionButton";
import TableActions from "@/components/TableActions";
import TableCellText from "@/components/TableCellText";
import "../governance.css";

export default function TenantMgmt() {
  const { t } = useTranslation();
  const {
    tenantId: selectedTenantId,
    setTenantId,
    setCustomerId,
    refreshTenants,
    refreshCustomers,
  } = useScope();
  const [tenants, setTenants] = useState<Tenant[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingTenant, setEditingTenant] = useState<Tenant | null>(null);
  const [form] = Form.useForm();
  const mutation = useSubmitGuard();

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

  useEffect(() => {
    fetchTenants();
  }, [fetchTenants]);

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
    if (!mutation.begin()) return;
    try {
      await deleteTenant(id);
      await refreshTenants().catch(() => undefined);
      if (selectedTenantId === id) {
        setTenantId(undefined);
        setCustomerId(undefined);
      }
      message.success(t("tenantMgmt.messageTenantDeleted"));
      fetchTenants();
    } catch {
      // handled by interceptor
    } finally {
      mutation.end();
    }
  };

  const handleSubmit = async () => {
    if (!mutation.begin()) return;
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
        const updated = await updateTenant(editingTenant.id, req);
        await refreshTenants().catch(() => undefined);
        if (selectedTenantId === editingTenant.id) {
          if (updated.status === TenantStatus.ACTIVE) {
            await refreshCustomers(editingTenant.id).catch(() => undefined);
          } else {
            setTenantId(undefined);
            setCustomerId(undefined);
          }
        }
        message.success(t("tenantMgmt.messageTenantUpdated"));
      } else {
        const req: CreateTenantRequest = {
          tenantId: values.tenantId,
          name: values.name,
          description: values.description,
          contactEmail: values.contactEmail,
          maxCustomers: values.maxCustomers,
        };
        const created = await createTenant(req);
        await refreshTenants().catch(() => undefined);
        setTenantId(created.id);
        setCustomerId(undefined);
        await refreshCustomers(created.id).catch(() => undefined);
        message.success(t("tenantMgmt.messageTenantCreated"));
      }
      setModalVisible(false);
      fetchTenants();
    } catch {
      // validation error or api error
    } finally {
      mutation.end();
    }
  };

  const statusTone: Record<string, SemanticStatusTone> = {
    ACTIVE: "success",
    SUSPENDED: "warning",
    DISABLED: "danger",
  };
  const activeTenantCount = tenants.filter(
    (tenant) => tenant.status === TenantStatus.ACTIVE,
  ).length;
  const suspendedTenantCount = tenants.filter(
    (tenant) => tenant.status === TenantStatus.SUSPENDED,
  ).length;
  const customerCapacity = tenants.reduce(
    (total, tenant) => total + (tenant.maxCustomers || 0),
    0,
  );

  const columns = [
    { title: t("common.id"), dataIndex: "id", key: "id", width: 72 },
    {
      title: t("tenantMgmt.tenantIdentifier"),
      dataIndex: "tenantId",
      key: "tenantId",
      width: 180,
    },
    {
      title: t("common.name"),
      dataIndex: "name",
      key: "name",
      width: 180,
      render: (value?: string) => <TableCellText value={value} />,
    },
    {
      title: t("tenantMgmt.contactEmail"),
      dataIndex: "contactEmail",
      key: "contactEmail",
      width: 220,
      render: (value?: string) => <TableCellText value={value} />,
    },
    {
      title: t("tenantMgmt.maxCustomers"),
      dataIndex: "maxCustomers",
      key: "maxCustomers",
      width: 120,
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
      dataIndex: "status",
      key: "status",
      width: 100,
      fixed: "right" as const,
      render: (s: string) => (
        <SemanticStatusTag tone={statusTone[s] || "neutral"}>
          {s === "ACTIVE"
            ? t("common.enabled")
            : s === "SUSPENDED"
              ? t("tenantMgmt.suspended")
              : t("common.disabled")}
        </SemanticStatusTag>
      ),
    },
    {
      title: t("common.actions"),
      key: "actions",
      width: 108,
      fixed: "right" as const,
      render: (_: unknown, record: Tenant) => (
        <TableActions label={t("common.actions")}>
          <PermissionGate requiredRoles={["SUPER_ADMIN"]}>
            <TableActionButton
              label={t("common.edit")}
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            />
          </PermissionGate>
          <PermissionGate requiredRoles={["SUPER_ADMIN"]}>
            <Popconfirm
              title={t("tenantMgmt.confirmDeleteTenant")}
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
      ),
    },
  ];

  return (
    <div className="tds-governance-page tds-tenant-management-page">
      <section className="tds-governance-metrics" aria-label={t("tenantMgmt.title")}>
        <div className="tds-governance-metric">
          <span>{t("tenantMgmt.title")}</span>
          <strong>{tenants.length}</strong>
        </div>
        <div className="tds-governance-metric tds-governance-metric--positive">
          <span>{t("common.enabled")}</span>
          <strong>{activeTenantCount}</strong>
        </div>
        <div className="tds-governance-metric tds-governance-metric--warning">
          <span>{t("tenantMgmt.suspended")}</span>
          <strong>{suspendedTenantCount}</strong>
        </div>
        <div className="tds-governance-metric">
          <span>{t("tenantMgmt.maxCustomers")}</span>
          <strong>{customerCapacity}</strong>
        </div>
      </section>

      <Card
        className="tds-governance-panel tds-governance-list-panel"
        title={t("tenantMgmt.title")}
        extra={
          <PermissionGate requiredRoles={["SUPER_ADMIN"]}>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
              {t("tenantMgmt.createTenant")}
            </Button>
          </PermissionGate>
        }
      >
        <Table
          className="tds-governance-table"
          rowKey="id"
          columns={columns}
          dataSource={tenants}
          loading={loading}
          pagination={{ pageSize: 10, showSizeChanger: true }}
          tableLayout="fixed"
          scroll={{ x: 1160 }}
        />

        <Modal
        title={
          editingTenant
            ? t("tenantMgmt.editTenant")
            : t("tenantMgmt.createTenant")
        }
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => !mutation.submitting && setModalVisible(false)}
        confirmLoading={mutation.submitting}
        cancelButtonProps={{ disabled: mutation.submitting }}
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="tenantId"
            label={t("tenantMgmt.tenantIdentifier")}
            rules={[
              {
                required: true,
                message: t("tenantMgmt.validationTenantIdRequired"),
              },
              { max: 50 },
              {
                pattern: /^[A-Za-z0-9][A-Za-z0-9_-]*$/,
                message: t("tenantMgmt.validationTenantIdFormat"),
              },
            ]}
          >
            <Input
              placeholder={t("tenantMgmt.placeholderTenantId")}
              disabled={!!editingTenant}
            />
          </Form.Item>
          <Form.Item
            name="name"
            label={t("common.name")}
            rules={[
              {
                required: true,
                message: t("tenantMgmt.validationNameRequired"),
              },
            ]}
          >
            <Input placeholder={t("tenantMgmt.placeholderTenantName")} />
          </Form.Item>
          <Form.Item name="description" label={t("common.description")}>
            <Input.TextArea
              rows={2}
              placeholder={t("tenantMgmt.placeholderOptionalDescription")}
            />
          </Form.Item>
          <Form.Item
            name="contactEmail"
            label={t("tenantMgmt.contactEmail")}
            rules={[{ type: "email" }]}
          >
            <Input
              type="email"
              inputMode="email"
              autoComplete="off"
              spellCheck={false}
              placeholder="security-admin@corp.internal"
            />
          </Form.Item>
          <Form.Item
            name="maxCustomers"
            label={t("tenantMgmt.maxCustomers")}
            initialValue={10}
            rules={[{ required: true }]}
          >
            <InputNumber
              min={1}
              max={100000}
              precision={0}
              style={{ width: "100%" }}
            />
          </Form.Item>
          {editingTenant && (
            <Form.Item name="status" label={t("common.status")}>
              <Select>
                <Select.Option value={TenantStatus.ACTIVE}>
                  {t("common.enabled")}
                </Select.Option>
                <Select.Option value={TenantStatus.SUSPENDED}>
                  {t("tenantMgmt.suspended")}
                </Select.Option>
                <Select.Option value={TenantStatus.DISABLED}>
                  {t("common.disabled")}
                </Select.Option>
              </Select>
            </Form.Item>
          )}
        </Form>
        </Modal>
      </Card>
    </div>
  );
}
