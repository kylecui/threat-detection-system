import { useEffect, useState, useRef, useCallback } from "react";
import {
  Tag,
  Button,
  Space,
  Modal,
  Form,
  Input,
  Select,
  Drawer,
  Table,
  Descriptions,
  Popconfirm,
  message,
  Card,
  Typography,
  Tabs,
  Row,
  Col,
  Checkbox,
  Alert,
  Tooltip,
  Steps,
} from "antd";
import type { TableColumnsType } from "antd";
import {
  PlusOutlined,
  DeleteOutlined,
  EditOutlined,
  MobileOutlined,
  ReloadOutlined,
  SyncOutlined,
  EyeOutlined,
} from "@ant-design/icons";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { useTranslation } from "react-i18next";
import type { Customer, Device, DeviceAttackEvent, DeviceQuota } from "@/types";
import { CustomerStatus, SubscriptionTier } from "@/types";
import customerService from "@/services/customer";
import { useScope } from "@/contexts/ScopeContext";
import PermissionGate from "@/components/PermissionGate";
import DeviceControlPanel from "@/components/DeviceControlPanel";
import dayjs from "dayjs";
import { formatDateTime, formatDateTimeMinute } from "@/utils/formatDateTime";
import useSubmitGuard from "@/hooks/useSubmitGuard";
import "../governance.css";
import NetworkIdentifier from "@/components/NetworkIdentifier";
import SemanticStatusTag, {
  type SemanticStatusTone,
} from "@/components/SemanticStatusTag";
import TableActionButton from "@/components/TableActionButton";
import TableActions from "@/components/TableActions";
import TableCellText from "@/components/TableCellText";
import { useResponsiveScrollX } from "@/components/tableScroll";
import { PRO_TABLE_SEARCH } from "@/components/proTableConfig";
import { useProTableToolbar } from "@/components/useProTableToolbar";

const { Text } = Typography;

/**
 * INTERACTION-006: the device data-flow summary follows the viewport instead
 * of always using three desktop columns (antd breakpoints: md >= 768,
 * lg >= 992). Phones get one column, tablets two, desktops keep three.
 */
const DEVICE_FLOW_COLUMNS = { xs: 1, sm: 1, md: 2, lg: 3, xl: 3, xxl: 3 };
/** The description spans two cells whenever at least two columns exist. */
const DEVICE_FLOW_DESCRIPTION_SPAN = { xs: 1, sm: 1, md: 2, lg: 2, xl: 2, xxl: 2 };

type CustomerCreateValues = Partial<Customer> & {
  createAdmin?: boolean;
  adminUsername?: string;
  adminPassword?: string;
  adminPasswordConfirm?: string;
};

const ADMIN_RANDOM_ALPHABET =
  "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

function generateCustomerAdminPassword(): string {
  const bytes = new Uint8Array(16);
  window.crypto.getRandomValues(bytes);
  return Array.from(
    bytes,
    (value) => ADMIN_RANDOM_ALPHABET[value % ADMIN_RANDOM_ALPHABET.length],
  ).join("");
}

function credentialsForCustomer(customerId?: string) {
  const normalized = customerId?.trim() || "";
  if (!normalized) {
    return { adminUsername: "", adminPassword: "", adminPasswordConfirm: "" };
  }
  const password = generateCustomerAdminPassword();
  return {
    adminUsername: `${normalized}-admin`,
    adminPassword: password,
    adminPasswordConfirm: password,
  };
}

function newestTimestamp(
  values: Array<string | undefined>,
): string | undefined {
  return values.reduce<string | undefined>((latest, value) => {
    if (!value || !dayjs(value).isValid()) return latest;
    if (!latest || dayjs(value).isAfter(dayjs(latest))) return value;
    return latest;
  }, undefined);
}

/**
 * Keep the status summary consistent with raw events returned by the same dialog.
 * The device-detail endpoint remains authoritative; this lower-bound merge also
 * closes the short race where the event query sees a newly committed row before
 * a previously loaded device-list snapshot is refreshed.
 */
function reconcileDeviceTelemetry(
  device: Device,
  events: DeviceAttackEvent[],
): Device {
  if (events.length === 0) return device;

  const eventAttackAt = newestTimestamp(
    events.map((event) => event.eventTimestamp || event.receivedAt),
  );
  const eventIngestedAt = newestTimestamp(
    events.map((event) => event.receivedAt || event.eventTimestamp),
  );
  const lastAttackAt = newestTimestamp([device.lastAttackAt, eventAttackAt]);
  const lastIngestedAt = newestTimestamp([
    device.lastIngestedAt,
    eventIngestedAt,
  ]);
  const dayAgo = dayjs().subtract(24, "hour");
  const recentEventsOnPage = events.filter((event) => {
    const timestamp = event.receivedAt || event.eventTimestamp;
    return Boolean(
      timestamp &&
        dayjs(timestamp).isValid() &&
        dayjs(timestamp).isAfter(dayAgo),
    );
  }).length;

  let pipelineState = device.pipelineState;
  if (device.isActive !== false && lastAttackAt) {
    if (
      device.lastThreatAt &&
      !dayjs(device.lastThreatAt).isBefore(dayjs(lastAttackAt))
    ) {
      pipelineState = "PROCESSED";
    } else if (dayjs(lastAttackAt).isBefore(dayjs().subtract(10, "minute"))) {
      pipelineState = "PROCESSING_DELAY";
    } else {
      pipelineState = "RECEIVING";
    }
  }
  const dataActiveWindowSeconds = device.heartbeatDelayedAfterSeconds || 2100;
  const hasRecentData = Boolean(
    lastIngestedAt &&
      dayjs().diff(dayjs(lastIngestedAt), "second") <= dataActiveWindowSeconds,
  );

  return {
    ...device,
    lastAttackAt,
    lastIngestedAt,
    attackEvents24h: Math.max(device.attackEvents24h ?? 0, recentEventsOnPage),
    dataActivityState:
      device.isActive === false
        ? "DISABLED"
        : hasRecentData
          ? "ACTIVE"
          : device.dataActivityState,
    pipelineState,
  };
}

const customerStatusToneMap: Record<string, SemanticStatusTone> = {
  [CustomerStatus.ACTIVE]: "success",
  [CustomerStatus.INACTIVE]: "neutral",
  [CustomerStatus.SUSPENDED]: "danger",
};

const DEVICE_MODAL_Z_INDEX = 1300;

const tierColorMap: Record<string, string> = {
  [SubscriptionTier.FREE]: "default",
  [SubscriptionTier.BASIC]: "default",
  [SubscriptionTier.PROFESSIONAL]: "blue",
  [SubscriptionTier.ENTERPRISE]: "purple",
};

const CustomersTab = () => {
  const { t } = useTranslation();
  const customerTableToolbar = useProTableToolbar();
  const {
    tenantId,
    customerId: selectedCustomerId,
    setCustomerId,
    refreshCustomers,
  } = useScope();
  const customerActionRef = useRef<ActionType>();
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [deviceDrawerOpen, setDeviceDrawerOpen] = useState(false);
  const [currentCustomer, setCurrentCustomer] = useState<Customer | null>(null);
  const [devices, setDevices] = useState<Device[]>([]);
  const [deviceQuota, setDeviceQuota] = useState<DeviceQuota | null>(null);
  const [devicesLoading, setDevicesLoading] = useState(false);
  const [devicesSyncing, setDevicesSyncing] = useState(false);
  const [createForm] = Form.useForm();
  const [editForm] = Form.useForm();
  const [bindDeviceModalOpen, setBindDeviceModalOpen] = useState(false);
  const [editDeviceModalOpen, setEditDeviceModalOpen] = useState(false);
  const [currentDevice, setCurrentDevice] = useState<Device | null>(null);
  const [deviceFlowOpen, setDeviceFlowOpen] = useState(false);
  const [deviceEvents, setDeviceEvents] = useState<DeviceAttackEvent[]>([]);
  const [deviceEventsLoading, setDeviceEventsLoading] = useState(false);
  const [deviceEventsTotal, setDeviceEventsTotal] = useState(0);
  const [deviceEventsPage, setDeviceEventsPage] = useState(1);
  const [bindDeviceForm] = Form.useForm();
  const [editDeviceForm] = Form.useForm();
  const deviceMutation = useSubmitGuard();
  const createMutation = useSubmitGuard();
  const createAdmin = Form.useWatch("createAdmin", createForm);
  const deviceFlowRequestId = useRef(0);
  const deviceListRequestId = useRef(0);

  const loadDeviceFlow = useCallback(
    async (device: Device, page = 1, silent = false) => {
      if (!currentCustomer) return;
      const customerId = currentCustomer.customerId;
      const requestId = ++deviceFlowRequestId.current;
      if (!silent) setDeviceEventsLoading(true);
      const [detailResult, eventsResult] = await Promise.allSettled([
        customerService.getDevice(customerId, device.devSerial),
        customerService.getDeviceEvents(
          customerId,
          device.devSerial,
          page - 1,
          20,
        ),
      ]);

      if (requestId !== deviceFlowRequestId.current) return;

      let refreshedDevice =
        detailResult.status === "fulfilled" ? detailResult.value : device;
      if (eventsResult.status === "fulfilled") {
        const events = eventsResult.value.content || [];
        refreshedDevice = reconcileDeviceTelemetry(refreshedDevice, events);
        setDeviceEvents(events);
        setDeviceEventsTotal(eventsResult.value.totalElements || 0);
        setDeviceEventsPage(page);
      } else if (!silent) {
        message.error(t("customersDevices.loadDeviceEventsFailed"));
      }

      setCurrentDevice(refreshedDevice);
      setDevices((previous) =>
        previous.map((item) =>
          item.devSerial.toUpperCase() ===
          refreshedDevice.devSerial.toUpperCase()
            ? refreshedDevice
            : item,
        ),
      );
      setDeviceEventsLoading(false);
    },
    [currentCustomer, t],
  );

  const showDeviceFlow = (device: Device) => {
    setCurrentDevice(device);
    setDeviceEvents([]);
    setDeviceEventsTotal(0);
    setDeviceEventsPage(1);
    setDeviceFlowOpen(true);
    void loadDeviceFlow(device, 1);
  };

  const closeDeviceFlow = () => {
    deviceFlowRequestId.current += 1;
    setDeviceEventsLoading(false);
    setDeviceFlowOpen(false);
  };

  useEffect(() => {
    if (
      !deviceFlowOpen ||
      !currentDevice ||
      !currentCustomer ||
      deviceEventsPage !== 1
    )
      return undefined;
    const timer = window.setInterval(() => {
      void loadDeviceFlow(currentDevice, 1, true);
    }, 15_000);
    return () => window.clearInterval(timer);
  }, [
    currentCustomer,
    currentDevice,
    deviceEventsPage,
    deviceFlowOpen,
    loadDeviceFlow,
  ]);

  const stateTag = (
    state?: string,
    kind: "heartbeat" | "dataActivity" | "pipeline" = "heartbeat",
  ) => {
    if (!state) {
      return (
        <SemanticStatusTag tone="neutral">
          {t("common.unknown")}
        </SemanticStatusTag>
      );
    }
    const tone: SemanticStatusTone =
      state === "ONLINE" || state === "ACTIVE" || state === "PROCESSED"
        ? "success"
        : state === "RECEIVING"
          ? "info"
          : state === "DELAYED" ||
              state === "IDLE" ||
              state === "PROCESSING_DELAY"
            ? "warning"
            : state === "OFFLINE"
              ? "danger"
              : state === "DISABLED"
                ? "neutral"
                : "warning";
    return (
      <SemanticStatusTag tone={tone}>
        {t(`customersDevices.${kind}State.${state}`)}
      </SemanticStatusTag>
    );
  };

  const loadDevices = useCallback(
    async (customerId: string, silent = false) => {
      const requestId = ++deviceListRequestId.current;
      if (!silent) setDevicesLoading(true);
      try {
        const [devList, quota] = await Promise.all([
          customerService.getDevices(customerId),
          customerService.getDeviceQuota(customerId),
        ]);
        if (requestId !== deviceListRequestId.current) return;
        setDevices(devList);
        setDeviceQuota(quota);
      } catch {
        if (!silent) message.error(t("customersDevices.loadDevicesFailed"));
      } finally {
        if (requestId === deviceListRequestId.current) setDevicesLoading(false);
      }
    },
    [t],
  );

  const closeDeviceDrawer = () => {
    deviceListRequestId.current += 1;
    deviceFlowRequestId.current += 1;
    setDevicesLoading(false);
    setDeviceEventsLoading(false);
    setDeviceFlowOpen(false);
    setBindDeviceModalOpen(false);
    setEditDeviceModalOpen(false);
    bindDeviceForm.resetFields();
    editDeviceForm.resetFields();
    setDeviceDrawerOpen(false);
  };

  useEffect(() => {
    if (!deviceDrawerOpen || !currentCustomer || deviceFlowOpen)
      return undefined;
    const timer = window.setInterval(() => {
      void loadDevices(currentCustomer.customerId, true);
    }, 15_000);
    return () => window.clearInterval(timer);
  }, [currentCustomer, deviceDrawerOpen, deviceFlowOpen, loadDevices]);

  const handleCreate = async (values: CustomerCreateValues) => {
    if (!createMutation.begin()) return;
    if (!tenantId) {
      message.error(t("customersDevices.selectTenantBeforeCreate"));
      createMutation.end();
      return;
    }
    try {
      const payload = { ...values };
      delete payload.adminPasswordConfirm;
      const created = await customerService.createCustomer({
        ...payload,
        tenantId,
      });
      await refreshCustomers(tenantId).catch(() => undefined);
      setCustomerId(created.customerId);
      setCreateModalOpen(false);
      createForm.resetFields();
      customerActionRef.current?.reload();

      if (values.createAdmin) {
        Modal.success({
          title: t("customersDevices.customerAndAdminCreated"),
          width: 560,
          content: (
            <Space direction="vertical" style={{ width: "100%" }}>
              <Alert
                type="warning"
                showIcon
                message={t("customersDevices.saveInitialCredentials")}
              />
              <Text>{t("customersDevices.adminUsername")}</Text>
              <Text code copyable>
                {values.adminUsername}
              </Text>
              <Text>{t("customersDevices.adminPassword")}</Text>
              <Text code copyable>
                {values.adminPassword}
              </Text>
            </Space>
          ),
        });
      } else {
        message.success(t("customersDevices.customerCreated"));
      }
    } catch (error) {
      const detail = (error as { response?: { data?: { message?: string } } })
        .response?.data?.message;
      message.error(detail || t("common.createFailed"));
    } finally {
      createMutation.end();
    }
  };

  const handleCreateValuesChange = (
    changed: Partial<CustomerCreateValues>,
    allValues: CustomerCreateValues,
  ) => {
    if (
      Object.prototype.hasOwnProperty.call(changed, "customerId") &&
      allValues.createAdmin
    ) {
      createForm.setFieldsValue(credentialsForCustomer(changed.customerId));
    }
    if (changed.createAdmin === true) {
      createForm.setFieldsValue(credentialsForCustomer(allValues.customerId));
    }
  };

  const handleUpdate = async (values: Partial<Customer>) => {
    if (!currentCustomer) return;
    if (!deviceMutation.begin()) return;
    try {
      const payload = { ...values };
      delete payload.customerId;
      delete payload.tenantId;
      const updated = await customerService.updateCustomer(
        currentCustomer.customerId,
        payload,
      );
      await refreshCustomers(tenantId).catch(() => undefined);
      if (
        selectedCustomerId === currentCustomer.customerId &&
        updated.status !== CustomerStatus.ACTIVE
      ) {
        setCustomerId(undefined);
      }
      message.success(t("customersDevices.customerUpdated"));
      setEditModalOpen(false);
      customerActionRef.current?.reload();
    } catch {
      message.error(t("common.updateFailed"));
    } finally {
      deviceMutation.end();
    }
  };

  const handleDelete = async (customerId: string) => {
    if (!deviceMutation.begin()) return;
    try {
      await customerService.deleteCustomer(customerId);
      await refreshCustomers(tenantId).catch(() => undefined);
      if (selectedCustomerId === customerId) setCustomerId(undefined);
      message.success(t("common.deleteSuccess"));
      customerActionRef.current?.reload();
    } catch {
      message.error(t("common.deleteFailed"));
    } finally {
      deviceMutation.end();
    }
  };

  const handleDeleteDevice = async (devSerial: string) => {
    if (!currentCustomer) return;
    if (!deviceMutation.begin()) return;
    try {
      await customerService.deleteDevice(currentCustomer.customerId, devSerial);
      message.success(t("customersDevices.deviceDeleted"));
      loadDevices(currentCustomer.customerId);
    } catch {
      message.error(t("common.deleteFailed"));
    } finally {
      deviceMutation.end();
    }
  };

  const handleBindDevice = async () => {
    if (!currentCustomer) return;
    if (!deviceMutation.begin()) return;
    try {
      const values = await bindDeviceForm.validateFields();
      const result = await customerService.addDevices(
        currentCustomer.customerId,
        [{ devSerial: values.devSerial, description: values.description }],
      );
      if (result.succeeded !== 1 || result.failed !== 0) {
        throw new Error(
          result.failures[0]?.reason || t("customersDevices.bindDeviceFailed"),
        );
      }
      message.success(t("customersDevices.deviceBound"));
      setBindDeviceModalOpen(false);
      bindDeviceForm.resetFields();
      loadDevices(currentCustomer.customerId);
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : t("customersDevices.bindDeviceFailed"),
      );
    } finally {
      deviceMutation.end();
    }
  };

  const handleEditDevice = async () => {
    if (!currentCustomer || !currentDevice) return;
    if (!deviceMutation.begin()) return;
    try {
      const values = await editDeviceForm.validateFields();
      await customerService.updateDevice(
        currentCustomer.customerId,
        currentDevice.devSerial,
        { description: values.description, isActive: values.isActive },
      );
      message.success(t("customersDevices.deviceUpdated"));
      setEditDeviceModalOpen(false);
      loadDevices(currentCustomer.customerId);
    } catch {
      message.error(t("common.updateFailed"));
    } finally {
      deviceMutation.end();
    }
  };

  const handleSyncDevices = async () => {
    if (!currentCustomer) return;
    if (!deviceMutation.begin()) return;
    setDevicesSyncing(true);
    try {
      await customerService.syncDevices(currentCustomer.customerId);
      message.success(t("customersDevices.deviceSynced"));
      loadDevices(currentCustomer.customerId);
    } catch {
      message.error(t("customersDevices.syncFailed"));
    } finally {
      setDevicesSyncing(false);
      deviceMutation.end();
    }
  };

  const columns: ProColumns<Customer>[] = [
    {
      title: t("common.customerId"),
      dataIndex: "customerId",
      copyable: true,
      width: 160,
    },
    {
      title: t("common.name"),
      dataIndex: "name",
      width: 160,
      render: (_, record) => <TableCellText value={record.name} />,
    },
    {
      title: t("common.email"),
      dataIndex: "email",
      width: 220,
      search: false,
      render: (_, record) => <TableCellText value={record.email} />,
    },
    {
      title: t("common.status"),
      dataIndex: "status",
      width: 100,
      valueType: "select",
      valueEnum: {
        ACTIVE: { text: t("customersDevices.active"), status: "Success" },
        INACTIVE: { text: t("customersDevices.inactive"), status: "Default" },
        SUSPENDED: { text: t("customersDevices.suspended"), status: "Error" },
      },
      render: (_, record) => (
        <SemanticStatusTag
          tone={customerStatusToneMap[record.status] || "neutral"}
        >
          {record.status === CustomerStatus.ACTIVE
            ? t("customersDevices.active")
            : record.status === CustomerStatus.SUSPENDED
              ? t("customersDevices.suspended")
              : t("customersDevices.inactive")}
        </SemanticStatusTag>
      ),
    },
    {
      title: t("customersDevices.subscriptionTier"),
      dataIndex: "subscriptionTier",
      width: 140,
      search: false,
      render: (_, record) => (
        <Tag color={tierColorMap[record.subscriptionTier]}>
          {record.subscriptionTier}
        </Tag>
      ),
    },
    {
      title: t("customersDevices.protectedHosts"),
      width: 128,
      search: false,
      render: (_, record) => (
        <span>
          {record.currentDevices}/{record.maxDevices}
        </span>
      ),
    },
    {
      title: t("customersDevices.alerts"),
      dataIndex: "alertEnabled",
      width: 104,
      search: false,
      render: (_, record) =>
        record.alertEnabled ? (
          <SemanticStatusTag tone="success">
            {t("common.enabled")}
          </SemanticStatusTag>
        ) : (
          <SemanticStatusTag tone="neutral">
            {t("common.disabled")}
          </SemanticStatusTag>
        ),
    },
    {
      title: t("common.createdAt"),
      dataIndex: "createdAt",
      width: 170,
      search: false,
      render: (_, record) =>
        record.createdAt
          ? formatDateTimeMinute(
              typeof record.createdAt === "number"
                ? record.createdAt * 1000
                : record.createdAt,
            )
          : "-",
    },
    {
      title: t("common.actions"),
      width: 152,
      key: "actions",
      fixed: "right",
      search: false,
      render: (_, record) => (
        <TableActions label={t("common.actions")}>
          <PermissionGate requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}>
            <TableActionButton
              label={t("common.edit")}
              icon={<EditOutlined />}
              onClick={() => {
                setCurrentCustomer(record);
                editForm.setFieldsValue(record);
                setEditModalOpen(true);
              }}
            />
          </PermissionGate>
          <TableActionButton
            label={t("customersDevices.devices")}
            icon={<MobileOutlined />}
            onClick={() => {
              setCurrentCustomer(record);
              setDeviceDrawerOpen(true);
              loadDevices(record.customerId);
            }}
          />
          {record.status !== CustomerStatus.INACTIVE && (
            <PermissionGate requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}>
              <Popconfirm
                title={t("customersDevices.confirmDeleteCustomer")}
                onConfirm={() => handleDelete(record.customerId)}
              >
                <TableActionButton
                  label={t("customersDevices.deleteCustomerAria", {
                    customerId: record.customerId,
                  })}
                  danger
                  icon={<DeleteOutlined />}
                />
              </Popconfirm>
            </PermissionGate>
          )}
        </TableActions>
      ),
    },
  ];

  const deviceColumns: TableColumnsType<Device> = [
    {
      title: t("customersDevices.serialNumber"),
      dataIndex: "devSerial",
      key: "devSerial",
      width: 156,
      render: (value?: string) => (
        <NetworkIdentifier value={value} kind="serial" />
      ),
    },
    {
      title: t("common.description"),
      dataIndex: "description",
      key: "description",
      width: 124,
      responsive: ["xl"],
      render: (value?: string) =>
        value ? (
          <Tooltip title={value}>
            <span className="tds-device-description">{value}</span>
          </Tooltip>
        ) : (
          "-"
        ),
    },
    {
      title: t("customersDevices.bindingStatus"),
      dataIndex: "isActive",
      key: "isActive",
      width: 120,
      render: (val: boolean) => (
        <SemanticStatusTag tone={val ? "success" : "neutral"}>
          {val ? t("customersDevices.active") : t("customersDevices.inactive")}
        </SemanticStatusTag>
      ),
    },
    {
      title: t("customersDevices.heartbeatStatus"),
      dataIndex: "heartbeatState",
      key: "heartbeatState",
      width: 112,
      render: (_value: string, record: Device) =>
        stateTag(record.heartbeatState || record.connectionState, "heartbeat"),
    },
    {
      title: t("customersDevices.dataActivityStatus"),
      dataIndex: "dataActivityState",
      key: "dataActivityState",
      width: 112,
      render: (value: string) => stateTag(value, "dataActivity"),
    },
    {
      title: t("customersDevices.pipelineStatus"),
      dataIndex: "pipelineState",
      key: "pipelineState",
      width: 132,
      responsive: ["xl"],
      render: (value: string) => stateTag(value, "pipeline"),
    },
    {
      title: t("customersDevices.lastDataAt"),
      dataIndex: "lastIngestedAt",
      key: "lastIngestedAt",
      width: 180,
      responsive: ["xl"],
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: t("common.actions"),
      key: "actions",
      width: 152,
      fixed: "right",
      align: "center",
      render: (_: unknown, record: Device) => (
        <TableActions label={t("common.actions")}>
          <TableActionButton
            label={t("customersDevices.dataFlow")}
            icon={<EyeOutlined />}
            onClick={() => showDeviceFlow(record)}
          />
          <PermissionGate requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}>
            <TableActionButton
              label={t("customersDevices.editDevice")}
              icon={<EditOutlined />}
              onClick={() => {
                setCurrentDevice(record);
                editDeviceForm.setFieldsValue({
                  description: record.description,
                  isActive: record.isActive !== false,
                });
                setEditDeviceModalOpen(true);
              }}
            />
          </PermissionGate>
          <PermissionGate requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}>
            <Popconfirm
              title={t("customersDevices.confirmUnbind")}
              onConfirm={() => handleDeleteDevice(record.devSerial)}
            >
              <TableActionButton
                label={t("customersDevices.unbind")}
                danger
                icon={<DeleteOutlined />}
              />
            </Popconfirm>
          </PermissionGate>
        </TableActions>
      ),
    },
  ];

  const deviceEventColumns: TableColumnsType<DeviceAttackEvent> = [
    {
      title: t("common.time"),
      dataIndex: "eventTimestamp",
      width: 180,
      ellipsis: true,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: "MAC",
      dataIndex: "attackMac",
      width: 192,
      render: (value?: string) => (
        <NetworkIdentifier value={value} kind="mac" />
      ),
    },
    {
      title: t("customersDevices.sourceIp"),
      dataIndex: "attackIp",
      width: 140,
      ellipsis: true,
      render: (value?: string) => (
        <NetworkIdentifier value={value} kind="ip" />
      ),
    },
    {
      title: t("customersDevices.targetIp"),
      dataIndex: "responseIp",
      width: 140,
      ellipsis: true,
      render: (value?: string) => (
        <NetworkIdentifier value={value} kind="ip" />
      ),
    },
    {
      title: t("customersDevices.targetPort"),
      dataIndex: "responsePort",
      width: 96,
      align: "center",
      render: (value?: number) => (
        <NetworkIdentifier value={value} kind="port" />
      ),
    },
    {
      title: t("customersDevices.receivedAt"),
      dataIndex: "receivedAt",
      width: 180,
      ellipsis: true,
      responsive: ["lg"],
      render: (value?: string) => formatDateTime(value),
    },
  ];

  // Scroll budgets of the columns visible at the current breakpoint
  // (both tables hide columns with `responsive`).
  const deviceScrollX = useResponsiveScrollX(deviceColumns);
  const deviceEventScrollX = useResponsiveScrollX(deviceEventColumns);

  const renderCustomerFormFields = (isCreate: boolean) => (
    <Row gutter={[20, 0]}>
      <Col xs={24} md={12}>
        <Form.Item
          name="customerId"
          label={t("common.customerId")}
          extra={isCreate ? t("customersDevices.customerIdFormat") : undefined}
          rules={[
            { required: true, max: 50 },
            {
              pattern: /^[A-Za-z0-9][A-Za-z0-9_-]*$/,
              message: t("customersDevices.customerIdFormatError"),
            },
          ]}
        >
          <Input
            allowClear={isCreate}
            disabled={!isCreate}
            maxLength={50}
            placeholder={t("common.customerId")}
          />
        </Form.Item>
      </Col>
      <Col xs={24} md={12}>
        <Form.Item
          name="name"
          label={t("common.name")}
          rules={[{ required: true }]}
        >
          <Input allowClear placeholder={t("common.name")} />
        </Form.Item>
      </Col>
      <Col xs={24} md={12}>
        <Form.Item
          name="email"
          label={t("common.email")}
          rules={[{ required: true, type: "email" }]}
        >
          <Input
            allowClear
            type="email"
            inputMode="email"
            autoComplete="off"
            spellCheck={false}
            placeholder={t("common.email")}
          />
        </Form.Item>
      </Col>
      <Col xs={24} md={12}>
        <Form.Item name="phone" label={t("common.phone")}>
          <Input
            allowClear
            type="tel"
            inputMode="tel"
            autoComplete="off"
            placeholder={t("common.phone")}
          />
        </Form.Item>
      </Col>
      <Col xs={24} md={12}>
        <Form.Item name="address" label={t("common.address")}>
          <Input allowClear placeholder={t("common.address")} />
        </Form.Item>
      </Col>
      {!isCreate && (
        <Col xs={24} md={12}>
          <Form.Item name="status" label={t("common.status")}>
            <Select
              options={[
                { label: t("customersDevices.active"), value: "ACTIVE" },
                { label: t("customersDevices.inactive"), value: "INACTIVE" },
                { label: t("customersDevices.suspended"), value: "SUSPENDED" },
              ]}
            />
          </Form.Item>
        </Col>
      )}
      <Col xs={24} md={12}>
        <Form.Item
          name="subscriptionTier"
          label={t("customersDevices.subscriptionTier")}
          initialValue="BASIC"
        >
          <Select
            options={[
              { label: "Free", value: "FREE" },
              { label: "Basic", value: "BASIC" },
              { label: "Professional", value: "PROFESSIONAL" },
              { label: "Enterprise", value: "ENTERPRISE" },
            ]}
          />
        </Form.Item>
      </Col>
      <Col xs={24} md={12}>
        <Form.Item
          name="maxDevices"
          label={t("customersDevices.maxProtectedDevices")}
          initialValue={10}
        >
          <Input type="number" />
        </Form.Item>
      </Col>
      <Col span={24}>
        <Form.Item name="description" label={t("common.description")}>
          <Input.TextArea rows={2} autoSize={{ minRows: 2, maxRows: 4 }} />
        </Form.Item>
      </Col>
      {isCreate && (
        <Col span={24}>
          <Form.Item name="createAdmin" valuePropName="checked" initialValue>
            <Checkbox>{t("customersDevices.autoCreateAdmin")}</Checkbox>
          </Form.Item>
        </Col>
      )}
      {isCreate && createAdmin && (
        <>
          <Col xs={24} md={12}>
            <Form.Item
              name="adminUsername"
              label={t("customersDevices.adminUsername")}
              extra={t("customersDevices.adminUsernameRelated")}
              rules={[{ required: true }]}
            >
              <Input readOnly />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              name="adminPassword"
              label={t("customersDevices.adminPassword")}
              extra={t("customersDevices.adminPasswordRelated")}
              rules={[
                {
                  required: true,
                  min: 8,
                  max: 16,
                  message: t("customersDevices.adminPasswordRelated"),
                },
              ]}
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
          </Col>
          <Col xs={24} md={12}>
            <Form.Item
              name="adminPasswordConfirm"
              label={t("customersDevices.confirmAdminPassword")}
              dependencies={["adminPassword"]}
              rules={[
                { required: true },
                ({ getFieldValue }) => ({
                  validator: (_, value) =>
                    value === getFieldValue("adminPassword")
                      ? Promise.resolve()
                      : Promise.reject(
                          new Error(
                            t("customersDevices.adminPasswordMismatch"),
                          ),
                        ),
                }),
              ]}
            >
              <Input.Password autoComplete="new-password" />
            </Form.Item>
          </Col>
          <Col
            xs={24}
            md={12}
            style={{ display: "flex", alignItems: "center" }}
          >
            <Button
              onClick={() =>
                createForm.setFieldsValue(
                  credentialsForCustomer(
                    createForm.getFieldValue("customerId"),
                  ),
                )
              }
            >
              {t("customersDevices.regenerateAdminPassword")}
            </Button>
          </Col>
        </>
      )}
    </Row>
  );

  return (
    <>
      <ProTable<Customer>
        {...customerTableToolbar}
        className="tds-governance-table tds-customer-management-table"
        headerTitle={t("customersDevices.customerManagement")}
        actionRef={customerActionRef}
        rowKey="id"
        columns={columns}
        params={{ tenantId }}
        request={async (params) => {
          try {
            const result = await customerService.getCustomers({
              page: (params.current || 1) - 1,
              size: params.pageSize || 20,
              tenantId,
            });
            return {
              data: result.content,
              total: result.totalElements,
              success: true,
            };
          } catch {
            return { data: [], total: 0, success: false };
          }
        }}
        pagination={{ defaultPageSize: 20 }}
        scroll={{ x: 1340 }}
        search={PRO_TABLE_SEARCH}
        toolBarRender={() => [
          <PermissionGate
            key="create"
            requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}
          >
            <Tooltip
              title={
                !tenantId
                  ? t("customersDevices.selectTenantBeforeCreate")
                  : undefined
              }
            >
              <span>
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  disabled={!tenantId}
                  onClick={() => {
                    createForm.resetFields();
                    createForm.setFieldsValue({ createAdmin: true });
                    setCreateModalOpen(true);
                  }}
                >
                  {t("customersDevices.createCustomer")}
                </Button>
              </span>
            </Tooltip>
          </PermissionGate>,
          <Button
            key="refresh"
            icon={<ReloadOutlined />}
            onClick={() => customerActionRef.current?.reload()}
          >
            {t("common.refresh")}
          </Button>,
        ]}
      />

      <Modal
        rootClassName="tds-form-modal"
        title={t("customersDevices.createCustomer")}
        open={createModalOpen}
        onOk={() => createForm.submit()}
        onCancel={() => !createMutation.submitting && setCreateModalOpen(false)}
        confirmLoading={createMutation.submitting}
        cancelButtonProps={{ disabled: createMutation.submitting }}
        okButtonProps={{ disabled: !tenantId }}
        width={920}
        styles={{
          body: { maxHeight: "calc(100vh - 180px)", overflowY: "auto" },
        }}
      >
        <Form
          className="tds-customer-form"
          form={createForm}
          layout="vertical"
          onFinish={handleCreate}
          onValuesChange={handleCreateValuesChange}
        >
          {!tenantId && (
            <Alert
              type="warning"
              showIcon
              message={t("customersDevices.selectTenantBeforeCreate")}
              style={{ marginBottom: 16 }}
            />
          )}
          {renderCustomerFormFields(true)}
        </Form>
      </Modal>

      <Modal
        rootClassName="tds-form-modal"
        title={t("customersDevices.editCustomer")}
        open={editModalOpen}
        onOk={() => editForm.submit()}
        onCancel={() => !deviceMutation.submitting && setEditModalOpen(false)}
        confirmLoading={deviceMutation.submitting}
        cancelButtonProps={{ disabled: deviceMutation.submitting }}
        width={920}
        styles={{
          body: { maxHeight: "calc(100vh - 180px)", overflowY: "auto" },
        }}
      >
        <Form
          className="tds-customer-form"
          form={editForm}
          layout="vertical"
          onFinish={handleUpdate}
        >
          {renderCustomerFormFields(false)}
        </Form>
      </Modal>

      <Drawer
        title={t("customersDevices.deviceManagementTitle", {
          name: currentCustomer?.name || "",
        })}
        open={deviceDrawerOpen}
        onClose={closeDeviceDrawer}
        width="min(1480px, 96vw)"
        extra={
          <Space>
            <Button
              size="small"
              icon={<ReloadOutlined />}
              loading={devicesLoading}
              onClick={() =>
                currentCustomer && void loadDevices(currentCustomer.customerId)
              }
            >
              {t("common.refresh")}
            </Button>
            <PermissionGate requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}>
              <Button
                size="small"
                icon={<SyncOutlined />}
                loading={devicesSyncing}
                disabled={deviceMutation.submitting && !devicesSyncing}
                onClick={handleSyncDevices}
              >
                {t("customersDevices.sync")}
              </Button>
            </PermissionGate>
            <PermissionGate requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}>
              <Button
                type="primary"
                size="small"
                icon={<PlusOutlined />}
                onClick={() => {
                  bindDeviceForm.resetFields();
                  setBindDeviceModalOpen(true);
                }}
              >
                {t("customersDevices.bindDevice")}
              </Button>
            </PermissionGate>
          </Space>
        }
      >
        {deviceQuota && (
          <Descriptions
            className="tds-detail-descriptions"
            bordered
            size="small"
            style={{ marginBottom: 16 }}
          >
            <Descriptions.Item label={t("common.customerId")}>
              {deviceQuota.customerId}
            </Descriptions.Item>
            <Descriptions.Item
              label={t("customersDevices.maxProtectedDevicesShort")}
            >
              {deviceQuota.maxDevices}
            </Descriptions.Item>
            <Descriptions.Item
              label={t("customersDevices.currentProtectedHostCount")}
            >
              {deviceQuota.protectedHostCount ?? 0}
            </Descriptions.Item>
            <Descriptions.Item label={t("customersDevices.deviceCount")}>
              {deviceQuota.currentDevices}
            </Descriptions.Item>
            <Descriptions.Item label={t("customersDevices.remainingQuota")}>
              {deviceQuota.remainingQuota}
            </Descriptions.Item>
          </Descriptions>
        )}
        <div className="tds-governance-table">
          <Table
            className="tds-device-management-table"
            columns={deviceColumns}
            dataSource={devices}
            rowKey="devSerial"
            loading={devicesLoading}
            size="small"
            pagination={false}
            tableLayout="fixed"
            scroll={{ x: deviceScrollX }}
          />
        </div>
      </Drawer>

      <Modal
        title={t("customersDevices.dataFlowTitle", {
          serial: currentDevice?.devSerial || "",
        })}
        open={deviceFlowOpen}
        onCancel={closeDeviceFlow}
        footer={<Button onClick={closeDeviceFlow}>{t("common.close")}</Button>}
        width={960}
        zIndex={DEVICE_MODAL_Z_INDEX}
      >
        {currentDevice && (
          <>
            <Alert
              showIcon
              type={
                currentDevice.pipelineState === "PROCESSING_DELAY" ||
                ["DELAYED", "OFFLINE"].includes(
                  currentDevice.heartbeatState ||
                    currentDevice.connectionState ||
                    "",
                )
                  ? "warning"
                  : "info"
              }
              message={t("customersDevices.activeIsBindingOnly")}
              style={{ marginBottom: 16 }}
            />
            <Descriptions
              className="tds-detail-descriptions tds-device-flow-descriptions"
              bordered
              size="small"
              column={DEVICE_FLOW_COLUMNS}
              style={{ marginBottom: 16 }}
            >
              <Descriptions.Item
                label={t("common.description")}
                span={DEVICE_FLOW_DESCRIPTION_SPAN}
              >
                {currentDevice.description || "-"}
              </Descriptions.Item>
              <Descriptions.Item label={t("customersDevices.discoveredHosts")}>
                {currentDevice.realHostCount ?? "-"}
              </Descriptions.Item>
              <Descriptions.Item label={t("customersDevices.bindingStatus")}>
                <SemanticStatusTag
                  tone={
                    currentDevice.isActive !== false ? "success" : "neutral"
                  }
                >
                  {currentDevice.isActive !== false
                    ? t("customersDevices.active")
                    : t("customersDevices.inactive")}
                </SemanticStatusTag>
              </Descriptions.Item>
              <Descriptions.Item label={t("customersDevices.heartbeatStatus")}>
                {stateTag(
                  currentDevice.heartbeatState || currentDevice.connectionState,
                  "heartbeat",
                )}
              </Descriptions.Item>
              <Descriptions.Item
                label={t("customersDevices.dataActivityStatus")}
              >
                {stateTag(currentDevice.dataActivityState, "dataActivity")}
              </Descriptions.Item>
              <Descriptions.Item label={t("customersDevices.pipelineStatus")}>
                {stateTag(currentDevice.pipelineState, "pipeline")}
              </Descriptions.Item>
              <Descriptions.Item label={t("customersDevices.lastHeartbeatAt")}>
                {formatDateTime(currentDevice.lastHeartbeatAt)}
              </Descriptions.Item>
              <Descriptions.Item label={t("customersDevices.lastAttackAt")}>
                {formatDateTime(currentDevice.lastAttackAt)}
              </Descriptions.Item>
              <Descriptions.Item label={t("customersDevices.attackEvents24h")}>
                {currentDevice.attackEvents24h ?? 0}
              </Descriptions.Item>
              <Descriptions.Item label={t("common.createdAt")}>
                {currentDevice.createdAt
                  ? formatDateTimeMinute(
                      typeof currentDevice.createdAt === "number"
                        ? currentDevice.createdAt * 1000
                        : currentDevice.createdAt,
                    )
                  : "-"}
              </Descriptions.Item>
              <Descriptions.Item
                label={t("customersDevices.lastThreatAt")}
                span="filled"
              >
                {formatDateTime(currentDevice.lastThreatAt)}
              </Descriptions.Item>
              <Descriptions.Item
                label={t("customersDevices.heartbeatPolicy")}
                span="filled"
              >
                {t("customersDevices.heartbeatPolicySummary", {
                  interval: Math.round(
                    (currentDevice.expectedHeartbeatIntervalSeconds || 1800) /
                      60,
                  ),
                  delayed: Math.round(
                    (currentDevice.heartbeatDelayedAfterSeconds || 2100) / 60,
                  ),
                  offline: Math.round(
                    (currentDevice.heartbeatOfflineAfterSeconds || 3900) / 60,
                  ),
                })}
              </Descriptions.Item>
            </Descriptions>
            <Steps
              size="small"
              current={
                currentDevice.lastThreatAt
                  ? 3
                  : currentDevice.lastAttackAt
                    ? 2
                    : currentDevice.lastHeartbeatAt
                      ? 1
                      : 0
              }
              items={[
                { title: t("customersDevices.flowBound") },
                { title: t("customersDevices.flowHeartbeat") },
                { title: t("customersDevices.flowRawEvent") },
                { title: t("customersDevices.flowAssessment") },
              ]}
              style={{ marginBottom: 20 }}
            />
            <Typography.Title level={5}>
              {t("customersDevices.recentRawEvents")}
            </Typography.Title>
            <Table<DeviceAttackEvent>
              className="tds-governance-table"
              rowKey="id"
              size="small"
              loading={deviceEventsLoading}
              dataSource={deviceEvents}
              tableLayout="fixed"
              columns={deviceEventColumns}
              pagination={{
                current: deviceEventsPage,
                pageSize: 20,
                total: deviceEventsTotal,
                showSizeChanger: false,
                onChange: (page) =>
                  currentDevice && void loadDeviceFlow(currentDevice, page),
              }}
              scroll={{ x: deviceEventScrollX }}
            />
          </>
        )}
      </Modal>

      <Modal
        title={t("customersDevices.bindNewDevice")}
        open={bindDeviceModalOpen}
        onOk={handleBindDevice}
        confirmLoading={deviceMutation.submitting}
        cancelButtonProps={{ disabled: deviceMutation.submitting }}
        onCancel={() => {
          if (deviceMutation.submitting) return;
          setBindDeviceModalOpen(false);
          bindDeviceForm.resetFields();
        }}
        okText={t("customersDevices.bind")}
        cancelText={t("common.cancel")}
        zIndex={DEVICE_MODAL_Z_INDEX}
      >
        <Form form={bindDeviceForm} layout="vertical">
          <Form.Item
            name="devSerial"
            label={t("customersDevices.deviceSerial")}
            rules={[
              {
                required: true,
                message: t("customersDevices.validationDeviceSerialRequired"),
              },
            ]}
          >
            <Input placeholder={t("customersDevices.deviceSerialExample")} />
          </Form.Item>
          <Form.Item name="description" label={t("common.description")}>
            <Input.TextArea
              rows={2}
              placeholder={t("customersDevices.deviceDescriptionOptional")}
            />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={t("customersDevices.editDevice")}
        open={editDeviceModalOpen}
        onOk={handleEditDevice}
        onCancel={() =>
          !deviceMutation.submitting && setEditDeviceModalOpen(false)
        }
        confirmLoading={deviceMutation.submitting}
        cancelButtonProps={{ disabled: deviceMutation.submitting }}
        okText={t("common.save")}
        cancelText={t("common.cancel")}
        zIndex={DEVICE_MODAL_Z_INDEX}
      >
        <Form form={editDeviceForm} layout="vertical">
          <Form.Item label={t("customersDevices.deviceSerial")}>
            <Input value={currentDevice?.devSerial} disabled />
          </Form.Item>
          <Form.Item name="description" label={t("common.description")}>
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="isActive" label={t("common.status")}>
            <Select
              options={[
                { label: t("customersDevices.active"), value: true },
                { label: t("customersDevices.deactivated"), value: false },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
};

export const DevicesTab = () => {
  const { t } = useTranslation();
  const deviceTableToolbar = useProTableToolbar();
  const deviceActionRef = useRef<ActionType>();
  const [bindModalOpen, setBindModalOpen] = useState(false);
  const [bindForm] = Form.useForm();
  const deviceMutation = useSubmitGuard();

  const { effectiveCustomerId: customerId } = useScope();
  const [controlDevice, setControlDevice] = useState<Device | null>(null);

  const stateTag = (
    state?: string,
    kind: "heartbeat" | "dataActivity" | "pipeline" = "heartbeat",
  ) => {
    if (!state) {
      return (
        <SemanticStatusTag tone="neutral">
          {t("common.unknown")}
        </SemanticStatusTag>
      );
    }
    const tone: SemanticStatusTone =
      state === "ONLINE" || state === "ACTIVE" || state === "PROCESSED"
        ? "success"
        : state === "RECEIVING"
          ? "info"
          : state === "DELAYED" ||
              state === "IDLE" ||
              state === "PROCESSING_DELAY"
            ? "warning"
            : state === "OFFLINE"
              ? "danger"
              : state === "DISABLED"
                ? "neutral"
                : "warning";
    return (
      <SemanticStatusTag tone={tone}>
        {t(`customersDevices.${kind}State.${state}`)}
      </SemanticStatusTag>
    );
  };

  const handleBind = async () => {
    if (!deviceMutation.begin()) return;
    try {
      const values = await bindForm.validateFields();
      if (!customerId) return;
      const result = await customerService.addDevices(customerId, [
        { devSerial: values.devSerial, description: values.description },
      ]);
      if (result.succeeded !== 1 || result.failed !== 0) {
        throw new Error(
          result.failures[0]?.reason || t("customersDevices.bindDeviceFailed"),
        );
      }
      message.success(t("customersDevices.deviceBound"));
      setBindModalOpen(false);
      bindForm.resetFields();
      deviceActionRef.current?.reload();
    } catch (error) {
      message.error(
        error instanceof Error
          ? error.message
          : t("customersDevices.bindDeviceFailed"),
      );
    } finally {
      deviceMutation.end();
    }
  };

  const handleUnbind = async (devSerial: string) => {
    if (!customerId) return;
    if (!deviceMutation.begin()) return;
    try {
      await customerService.deleteDevice(customerId, devSerial);
      message.success(t("customersDevices.deviceUnbound"));
      deviceActionRef.current?.reload();
    } catch {
      message.error(t("customersDevices.unbindFailed"));
    } finally {
      deviceMutation.end();
    }
  };

  const columns: ProColumns<Device>[] = [
    {
      title: t("customersDevices.deviceSerial"),
      dataIndex: "devSerial",
      width: 200,
      copyable: true,
      ellipsis: true,
      render: (_, record) => (
        <NetworkIdentifier value={record.devSerial} kind="serial" />
      ),
    },
    {
      title: t("customersDevices.bindingStatus"),
      dataIndex: "isActive",
      width: 120,
      valueType: "select",
      valueEnum: {
        true: { text: t("customersDevices.active"), status: "Success" },
        false: { text: t("customersDevices.deactivated"), status: "Default" },
      },
      render: (_, record) => (
        <SemanticStatusTag
          tone={record.isActive !== false ? "success" : "neutral"}
        >
          {record.isActive !== false
            ? t("customersDevices.active")
            : t("customersDevices.deactivated")}
        </SemanticStatusTag>
      ),
    },
    {
      title: t("customersDevices.heartbeatStatus"),
      dataIndex: "heartbeatState",
      width: 120,
      search: false,
      render: (_, record) =>
        stateTag(record.heartbeatState || record.connectionState, "heartbeat"),
    },
    {
      title: t("customersDevices.dataActivityStatus"),
      dataIndex: "dataActivityState",
      width: 120,
      search: false,
      render: (_, record) => stateTag(record.dataActivityState, "dataActivity"),
    },
    {
      title: t("customersDevices.pipelineStatus"),
      dataIndex: "pipelineState",
      width: 140,
      search: false,
      render: (_, record) => (
        <Tag
          color={
            record.pipelineState === "PROCESSED"
              ? "green"
              : record.pipelineState === "RECEIVING"
                ? "blue"
                : record.pipelineState === "PROCESSING_DELAY"
                  ? "orange"
                  : "default"
          }
        >
          {record.pipelineState
            ? t(`customersDevices.pipelineState.${record.pipelineState}`)
            : t("common.unknown")}
        </Tag>
      ),
    },
    {
      title: t("common.description"),
      dataIndex: "description",
      width: 240,
      search: false,
      render: (_, record) => <TableCellText value={record.description} />,
    },
    {
      title: t("customersDevices.protectedHostCount"),
      dataIndex: "realHostCount",
      width: 140,
      search: false,
      sorter: true,
    },
    {
      title: t("customersDevices.boundAt"),
      dataIndex: "createdAt",
      width: 180,
      valueType: "dateTime",
      search: false,
      sorter: true,
      render: (_, record) => formatDateTimeMinute(record.createdAt),
    },
    {
      title: t("common.actions"),
      valueType: "option",
      width: 150,
      fixed: "right",
      // INTERACTION-005: a single action renders directly (no group wrapper).
      render: (_, record) => (
        <Space>
        <Button size="small" onClick={() => setControlDevice(record)}>设备控制</Button>
        <PermissionGate requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}>
          <Popconfirm
            title={t("customersDevices.confirmUnbindDevice")}
            onConfirm={() => handleUnbind(record.devSerial)}
          >
            <TableActionButton
              label={t("customersDevices.unbind")}
              danger
              icon={<DeleteOutlined />}
            />
          </Popconfirm>
        </PermissionGate>
        </Space>
      ),
    },
  ];

  return (
    <div className="tds-device-governance-workspace">
      <Drawer title={`设备控制 · ${controlDevice?.devSerial || ''}`} open={Boolean(controlDevice)} width="min(960px, 100vw)" destroyOnClose onClose={() => setControlDevice(null)}>
        {controlDevice && customerId && <DeviceControlPanel key={`${customerId}:${controlDevice.devSerial}`} customerId={customerId} devSerial={controlDevice.devSerial} />}
      </Drawer>
      <Card className="tds-governance-context" size="small">
        <Space align="center" wrap>
          <Text strong>{t("customersDevices.currentCustomer")}:</Text>
          {customerId ? (
            <Tag color="blue">{customerId}</Tag>
          ) : (
            <Text type="secondary">
              {t("customersDevices.selectCustomerInScope")}
            </Text>
          )}
        </Space>
      </Card>

      {!customerId ? (
        <Card className="tds-governance-empty">
          <div className="tds-governance-empty__body">
            {t("customersDevices.selectCustomerFirst")}
          </div>
        </Card>
      ) : (
        <>
          <ProTable<Device>
            {...deviceTableToolbar}
            className="tds-governance-table tds-device-list-table"
            headerTitle={t("customersDevices.deviceList")}
            actionRef={deviceActionRef}
            rowKey="devSerial"
            columns={columns}
            search={PRO_TABLE_SEARCH}
            request={async () => {
              if (!customerId) return { data: [], success: true, total: 0 };
              try {
                const data = await customerService.getDevices(customerId);
                return { data, success: true, total: data.length };
              } catch {
                message.error(t("customersDevices.loadDevicesFailed"));
                return { data: [], success: false, total: 0 };
              }
            }}
            toolBarRender={() => [
              <Button
                key="reload"
                icon={<ReloadOutlined />}
                onClick={() => deviceActionRef.current?.reload()}
              >
                {t("common.refresh")}
              </Button>,
              <PermissionGate
                key="bind"
                requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}
              >
                <Button
                  type="primary"
                  icon={<PlusOutlined />}
                  onClick={() => setBindModalOpen(true)}
                >
                  {t("customersDevices.bindDevice")}
                </Button>
              </PermissionGate>,
            ]}
            pagination={{ defaultPageSize: 20 }}
            tableLayout="fixed"
            scroll={{ x: 1400 }}
          />

          <Modal
            title={t("customersDevices.bindNewDevice")}
            open={bindModalOpen}
            onOk={handleBind}
            confirmLoading={deviceMutation.submitting}
            cancelButtonProps={{ disabled: deviceMutation.submitting }}
            onCancel={() => {
              if (deviceMutation.submitting) return;
              setBindModalOpen(false);
              bindForm.resetFields();
            }}
            okText={t("customersDevices.bind")}
            cancelText={t("common.cancel")}
          >
            <Form form={bindForm} layout="vertical">
              <Form.Item
                name="devSerial"
                label={t("customersDevices.deviceSerial")}
                rules={[
                  {
                    required: true,
                    message: t(
                      "customersDevices.validationDeviceSerialRequired",
                    ),
                  },
                ]}
              >
                <Input
                  placeholder={t("customersDevices.deviceSerialExample")}
                />
              </Form.Item>
              <Form.Item name="description" label={t("common.description")}>
                <Input.TextArea
                  rows={3}
                  placeholder={t("customersDevices.deviceDescriptionOptional")}
                />
              </Form.Item>
            </Form>
          </Modal>
        </>
      )}
    </div>
  );
};

const CustomersAndDevices = () => {
  const { t } = useTranslation();
  return (
    <div className="tds-governance-page tds-customers-devices-page">
      <Tabs
        className="tds-governance-tabs"
        items={[
          {
            key: "customers",
            label: t("customersDevices.customerManagement"),
            children: <CustomersTab />,
          },
          {
            key: "devices",
            label: t("customersDevices.deviceManagement"),
            children: <DevicesTab />,
          },
        ]}
      />
    </div>
  );
};

export default CustomersAndDevices;
