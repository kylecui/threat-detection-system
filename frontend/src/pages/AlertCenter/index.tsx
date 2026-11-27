import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  Table,
  Segmented,
  Spin,
  Tag,
  Button,
  Space,
  Modal,
  Input,
  Select,
  Drawer,
  Descriptions,
  Typography,
  message,
} from "antd";
import {
  AppstoreOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  UserOutlined,
  ArrowUpOutlined,
  StopOutlined,
  ReloadOutlined,
  UnorderedListOutlined,
  EyeOutlined,
} from "@ant-design/icons";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { useTranslation } from "react-i18next";
import type {
  Alert,
  AlertAnalytics,
  GroupedAlertResponse,
  AlertQueryFilter,
  ManagedUser,
} from "@/types";
import { AlertStatus, AlertSeverity } from "@/types";
import alertService from "@/services/alert";
import { useScope } from "@/contexts/ScopeContext";
import { formatDateTime } from "@/utils/formatDateTime";
import { formatAlertOwner } from "@/utils/alertDisplay";
import type { TableColumnsType } from "antd";
import useSubmitGuard from "@/hooks/useSubmitGuard";
import NetworkIdentifier from "@/components/NetworkIdentifier";
import SemanticStatusTag, {
  type SemanticStatusTone,
} from "@/components/SemanticStatusTag";
import TableActionButton from "@/components/TableActionButton";
import TableActions from "@/components/TableActions";
import { useResponsiveScrollX } from "@/components/tableScroll";
import { PRO_TABLE_SEARCH } from "@/components/proTableConfig";
import { useProTableToolbar } from "@/components/useProTableToolbar";
import { listUsers } from "@/services/user";
import { useAuth } from "@/contexts/AuthContext";
import "../investigate.css";

/** 严重程度颜色 */
const severityColorMap: Record<string, string> = {
  [AlertSeverity.CRITICAL]: "red",
  [AlertSeverity.HIGH]: "orange",
  [AlertSeverity.MEDIUM]: "gold",
  [AlertSeverity.LOW]: "blue",
  [AlertSeverity.INFO]: "default",
};

/** 状态颜色 */
const statusToneMap: Record<string, SemanticStatusTone> = {
  [AlertStatus.NEW]: "info",
  [AlertStatus.IN_PROGRESS]: "warning",
  [AlertStatus.DEDUPLICATED]: "neutral",
  [AlertStatus.ENRICHED]: "info",
  [AlertStatus.NOTIFIED]: "info",
  [AlertStatus.ESCALATED]: "warning",
  [AlertStatus.RESOLVED]: "success",
  [AlertStatus.ARCHIVED]: "neutral",
};

/**
 * 告警中心页面
 */
const AlertCenter = () => {
  const { t } = useTranslation();
  const alertTableToolbar = useProTableToolbar();
  const { user: currentUser } = useAuth();
  const actionRef = useRef<ActionType>();
  const { effectiveCustomerId, initialized } = useScope();
  const [analytics, setAnalytics] = useState<AlertAnalytics | null>(null);
  const [resolveModalOpen, setResolveModalOpen] = useState(false);
  const [assignModalOpen, setAssignModalOpen] = useState(false);
  const [escalateModalOpen, setEscalateModalOpen] = useState(false);
  const [currentAlert, setCurrentAlert] = useState<Alert | null>(null);
  const [resolution, setResolution] = useState("");
  const [assignTo, setAssignTo] = useState("");
  const [assignableUsers, setAssignableUsers] = useState<ManagedUser[]>([]);
  const [assignableUsersLoading, setAssignableUsersLoading] = useState(false);
  const [escalateReason, setEscalateReason] = useState("");
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailAlert, setDetailAlert] = useState<Alert | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const alertMutation = useSubmitGuard();
  const [cancellingEscalationId, setCancellingEscalationId] = useState<
    number | null
  >(null);
  const [viewMode, setViewMode] = useState<"detail" | "grouped">("grouped");
  const [groupedData, setGroupedData] = useState<GroupedAlertResponse[]>([]);
  const [groupedTotal, setGroupedTotal] = useState(0);
  const [groupedLoading, setGroupedLoading] = useState(false);
  const [expandedMacs, setExpandedMacs] = useState<string[]>([]);
  const [subRows, setSubRows] = useState<Record<string, Alert[]>>({});

  const openAlertDetail = useCallback(
    async (alert: Alert) => {
      setDetailAlert(alert);
      setDetailOpen(true);
      setDetailLoading(true);
      try {
        setDetailAlert(await alertService.getAlert(alert.id));
      } catch {
        message.error(t("alertCenter.detailLoadFailed"));
      } finally {
        setDetailLoading(false);
      }
    },
    [t],
  );

  const openAssignment = useCallback(
    async (alert: Alert) => {
      setCurrentAlert(alert);
      setAssignTo(alert.assignedTo || "");
      setAssignModalOpen(true);
      setAssignableUsersLoading(true);
      try {
        const users = await listUsers();
        setAssignableUsers(users.filter((user) => user.enabled));
      } catch {
        setAssignableUsers([]);
        message.warning(t("alertCenter.assigneeLoadFailed"));
      } finally {
        setAssignableUsersLoading(false);
      }
    },
    [t],
  );

  const assigneeOptions = useMemo(() => {
    const candidates = new Map<string, { label: string; value: string }>();
    if (currentUser?.username) {
      candidates.set(currentUser.username, {
        value: currentUser.username,
        label: currentUser.displayName
          ? `${currentUser.displayName} (${currentUser.username})`
          : currentUser.username,
      });
    }
    assignableUsers
      .filter(
        (user) =>
          !effectiveCustomerId ||
          !user.customerId ||
          user.customerId === effectiveCustomerId,
      )
      .forEach((user) =>
        candidates.set(user.username, {
          value: user.username,
          label: user.displayName
            ? `${user.displayName} (${user.username})`
            : user.username,
        }),
      );
    if (assignTo && !candidates.has(assignTo)) {
      candidates.set(assignTo, { value: assignTo, label: assignTo });
    }
    return [...candidates.values()];
  }, [assignTo, assignableUsers, currentUser, effectiveCustomerId]);

  /**
   * Older rows keep the legacy service-prefixed title in the database. When
   * their event metadata is available, render the same compact, localized
   * title now used for newly ingested alerts so an upgrade fixes the existing
   * list without destructively rewriting historical records.
   */
  const formatAlertTitle = useCallback(
    (alert: Alert) => {
      if (
        alert.source !== "stream-processing-service" ||
        alert.eventType !== "THREAT_DETECTION" ||
        !alert.metadata
      ) {
        return alert.title;
      }

      try {
        const metadata = JSON.parse(alert.metadata) as Record<string, unknown>;
        const tier = Number(metadata.tier);
        const attackCount = Number(metadata.attackCount);
        const mac = alert.attackMac || String(metadata.attackMac || "");
        if (!mac || !Number.isFinite(attackCount)) return alert.title;

        const windowLabel =
          tier === 1
            ? t("threatList.window30s")
            : tier === 2
              ? t("threatList.window5m")
              : tier === 3
                ? t("threatList.window15m")
                : t("common.unknown");
        return t("alertCenter.compactTitle", {
          window: windowLabel,
          mac,
          count: attackCount,
        });
      } catch {
        return alert.title;
      }
    },
    [t],
  );

  /** 加载分析数据 */
  useEffect(() => {
    alertService
      .getAnalytics()
      .then(setAnalytics)
      .catch(() => {});
  }, []);

  const fetchGroupedAlerts = useCallback(
    async (p = 1, ps = 20) => {
      if (!initialized) return;
      setGroupedLoading(true);
      try {
        const filter: AlertQueryFilter = {
          page: p - 1,
          size: ps,
          sortBy: "latest_alert_time",
          sortDir: "DESC",
        };
        if (effectiveCustomerId) {
          filter.customerId = effectiveCustomerId;
        }
        const result = await alertService.getGroupedAlerts(filter);
        setGroupedData(result.content);
        setGroupedTotal(result.totalElements);
      } catch {
        message.error(t("common.operationFailed"));
      } finally {
        setGroupedLoading(false);
      }
    },
    [effectiveCustomerId, initialized, t],
  );

  useEffect(() => {
    if (viewMode === "grouped") {
      void fetchGroupedAlerts();
    }
  }, [viewMode, fetchGroupedAlerts]);

  const handleAlertExpand = useCallback(
    async (expanded: boolean, record: GroupedAlertResponse) => {
      if (!expanded) {
        setExpandedMacs((prev) => prev.filter((m) => m !== record.attackMac));
        return;
      }
      setExpandedMacs((prev) =>
        prev.includes(record.attackMac) ? prev : [...prev, record.attackMac],
      );
      if (subRows[record.attackMac]) return;
      try {
        const result = await alertService.getAlerts({
          customerId: effectiveCustomerId,
          page: 0,
          size: 100,
          sortBy: "created_at",
          sortDir: "DESC",
        });
        const filtered = result.content.filter(
          (a: Alert) => a.attackMac === record.attackMac,
        );
        setSubRows((prev) => ({ ...prev, [record.attackMac]: filtered }));
      } catch {
        message.error(t("common.operationFailed"));
      }
    },
    [effectiveCustomerId, subRows, t],
  );

  /** 处理解决告警 */
  const handleResolve = async () => {
    if (!currentAlert) return;
    if (!resolution.trim() || !currentUser?.username) return;
    if (!alertMutation.begin()) return;
    try {
      await alertService.resolve(currentAlert.id, {
        resolution: resolution.trim(),
        resolvedBy: currentUser.username,
      });
      message.success(t("alertCenter.messageResolved"));
      setResolveModalOpen(false);
      setResolution("");
      actionRef.current?.reload();
      void fetchGroupedAlerts();
      void alertService
        .getAnalytics()
        .then(setAnalytics)
        .catch(() => {});
    } catch {
      message.error(t("common.operationFailed"));
    } finally {
      alertMutation.end();
    }
  };

  /** 处理分配 */
  const handleAssign = async () => {
    if (!currentAlert) return;
    if (!assignTo) return;
    if (!alertMutation.begin()) return;
    try {
      await alertService.assign(currentAlert.id, assignTo);
      message.success(t("alertCenter.messageAssigned"));
      setAssignModalOpen(false);
      setAssignTo("");
      actionRef.current?.reload();
      void fetchGroupedAlerts();
    } catch {
      message.error(t("common.operationFailed"));
    } finally {
      alertMutation.end();
    }
  };

  /** 处理升级 */
  const handleEscalate = async () => {
    if (!currentAlert) return;
    if (!escalateReason.trim()) return;
    if (!alertMutation.begin()) return;
    try {
      await alertService.escalate(currentAlert.id, escalateReason.trim());
      message.success(t("alertCenter.messageEscalated"));
      setEscalateModalOpen(false);
      setEscalateReason("");
      actionRef.current?.reload();
      void fetchGroupedAlerts();
    } catch {
      message.error(t("common.operationFailed"));
    } finally {
      alertMutation.end();
    }
  };

  /** 取消升级 */
  const handleCancelEscalation = async (alert: Alert) => {
    if (!alertMutation.begin()) return;
    setCancellingEscalationId(alert.id);
    try {
      await alertService.cancelEscalation(alert.id);
      message.success(t("alertCenter.messageEscalationCancelled"));
      actionRef.current?.reload();
      void fetchGroupedAlerts();
    } catch {
      message.error(t("common.operationFailed"));
    } finally {
      setCancellingEscalationId(null);
      alertMutation.end();
    }
  };

  const columns: ProColumns<Alert>[] = [
    {
      title: t("common.id"),
      dataIndex: "id",
      width: 64,
      responsive: ["xl"],
      search: false,
    },
    {
      title: t("alertCenter.titleColumn"),
      dataIndex: "title",
      ellipsis: true,
      width: 240,
      search: false,
      render: (_, record) => formatAlertTitle(record),
    },
    {
      title: t("common.severity"),
      dataIndex: "severity",
      width: 88,
      align: "center",
      valueType: "select",
      valueEnum: {
        CRITICAL: { text: t("common.severityCritical"), status: "Error" },
        HIGH: { text: t("common.severityHighRisk"), status: "Warning" },
        MEDIUM: { text: t("common.severityMediumRisk"), status: "Processing" },
        LOW: { text: t("common.severityLowRisk"), status: "Default" },
        INFO: { text: t("common.severityInfo"), status: "Default" },
      },
      render: (_, record) => (
        <Tag color={severityColorMap[record.severity]}>{record.severity}</Tag>
      ),
    },
    {
      title: t("common.status"),
      dataIndex: "status",
      width: 96,
      align: "center",
      valueType: "select",
      valueEnum: {
        NEW: { text: t("alertCenter.statusPending") },
        IN_PROGRESS: { text: t("alertCenter.statusInProgress") },
        RESOLVED: { text: t("alertCenter.statusResolved") },
        ARCHIVED: { text: t("alertCenter.statusArchived") },
      },
      render: (_, record) => (
        <SemanticStatusTag tone={statusToneMap[record.status] || "neutral"}>
          {t(`alertCenter.status.${record.status}`, {
            defaultValue: record.status,
          })}
        </SemanticStatusTag>
      ),
    },
    {
      title: t("alertCenter.attackMac"),
      dataIndex: "attackMac",
      width: 192,
      responsive: ["md"],
      search: false,
      render: (_, record) => (
        <NetworkIdentifier value={record.attackMac} kind="mac" />
      ),
    },
    {
      title: t("alertCenter.threatScore"),
      dataIndex: "threatScore",
      width: 104,
      align: "center",
      responsive: ["xl"],
      search: false,
      render: (_, record) => record.threatScore?.toFixed(2) || "-",
    },
    {
      title: t("alertCenter.handlingLevel"),
      dataIndex: "escalationLevel",
      width: 112,
      align: "center",
      responsive: ["xl"],
      search: false,
      render: (_, record) =>
        (record.escalationLevel || 0) > 0 ? (
          <Tag color="volcano">
            {t("alertCenter.escalatedLevel", { level: record.escalationLevel })}
          </Tag>
        ) : (
          "-"
        ),
    },
    {
      title: t("alertCenter.owner"),
      dataIndex: "assignedTo",
      width: 116,
      responsive: ["lg"],
      search: false,
      ellipsis: true,
      render: (_, record) => {
        const owner = formatAlertOwner(record.assignedTo);
        return (
          <Typography.Text
            ellipsis={{ tooltip: owner }}
            style={{ display: "block" }}
          >
            {owner}
          </Typography.Text>
        );
      },
    },
    {
      title: t("common.createdAt"),
      dataIndex: "createdAt",
      width: 180,
      valueType: "dateTime",
      search: false,
      render: (_, record) => formatDateTime(record.createdAt),
    },
    {
      title: t("common.actions"),
      width: 200,
      fixed: "right",
      align: "center",
      key: "actions",
      search: false,
      render: (_, record) => (
        <TableActions label={t("common.actions")}>
          <TableActionButton
            label={t("alertCenter.viewDetails")}
            icon={<EyeOutlined />}
            onClick={() => void openAlertDetail(record)}
          />
          {record.status !== AlertStatus.RESOLVED &&
            record.status !== AlertStatus.ARCHIVED && (
              <>
                <TableActionButton
                  label={t("alertCenter.resolve")}
                  icon={<CheckCircleOutlined />}
                  onClick={() => {
                    setCurrentAlert(record);
                    setResolveModalOpen(true);
                  }}
                />
                <TableActionButton
                  label={t("alertCenter.assign")}
                  icon={<UserOutlined />}
                  onClick={() => void openAssignment(record)}
                />
                {(record.escalationLevel || 0) <= 0 ? (
                  <TableActionButton
                    label={t("alertCenter.escalate")}
                    danger
                    icon={<ArrowUpOutlined />}
                    onClick={() => {
                      setCurrentAlert(record);
                      setEscalateModalOpen(true);
                    }}
                  />
                ) : (
                  <TableActionButton
                    label={t("alertCenter.cancelEscalation")}
                    icon={<StopOutlined />}
                    loading={cancellingEscalationId === record.id}
                    disabled={
                      alertMutation.submitting &&
                      cancellingEscalationId !== record.id
                    }
                    onClick={() => handleCancelEscalation(record)}
                  />
                )}
              </>
            )}
        </TableActions>
      ),
    },
  ];

  // Scroll budget of the columns visible at the current breakpoint.
  const detailScrollX = useResponsiveScrollX(columns);

  const groupedAlertColumns: TableColumnsType<GroupedAlertResponse> = useMemo(
    () => [
      {
        title: t("alertCenter.attackMac"),
        dataIndex: "attackMac",
        width: 192,
        render: (mac: string) => <NetworkIdentifier value={mac} kind="mac" />,
      },
      {
        title: t("common.severity"),
        dataIndex: "maxSeverity",
        width: 100,
        align: "center",
        render: (severity: string) => (
          <Tag color={severityColorMap[severity] || "default"}>{severity}</Tag>
        ),
      },
      {
        title: t("alertCenter.threatScore"),
        dataIndex: "maxThreatScore",
        width: 100,
        align: "center",
        render: (score: number) => score?.toFixed(2) || "-",
      },
      {
        title: t("alertCenter.groupedAlertCount"),
        dataIndex: "alertCount",
        width: 100,
        align: "center",
      },
      {
        title: t("alertCenter.groupedUnresolved"),
        dataIndex: "unresolvedCount",
        width: 120,
        align: "center",
        render: (count: number) =>
          count > 0 ? (
            <Tag color="error">{count}</Tag>
          ) : (
            <Tag color="success">0</Tag>
          ),
      },
      {
        title: t("common.createdAt"),
        dataIndex: "latestAlertTime",
        width: 180,
        render: (time: string) => formatDateTime(time),
      },
    ],
    [t],
  );

  const subAlertColumns: TableColumnsType<Alert> = useMemo(
    () => [
      {
        title: t("common.id"),
        dataIndex: "id",
        width: 72,
        align: "center",
      },
      {
        title: t("alertCenter.titleColumn"),
        dataIndex: "title",
        width: 240,
        align: "center",
        ellipsis: true,
        render: (_, record) => formatAlertTitle(record),
      },
      {
        title: t("common.severity"),
        dataIndex: "severity",
        width: 100,
        align: "center",
        render: (severity: string) => (
          <Tag color={severityColorMap[severity] || "default"}>{severity}</Tag>
        ),
      },
      {
        title: t("common.status"),
        dataIndex: "status",
        width: 110,
        align: "center",
        render: (status: string) => (
          <SemanticStatusTag tone={statusToneMap[status] || "neutral"}>
            {t(`alertCenter.status.${status}`, { defaultValue: status })}
          </SemanticStatusTag>
        ),
      },
      {
        title: t("alertCenter.threatScore"),
        dataIndex: "threatScore",
        width: 100,
        align: "center",
        render: (score: number) => score?.toFixed(2) || "-",
      },
      {
        title: t("common.createdAt"),
        dataIndex: "createdAt",
        width: 180,
        align: "center",
        render: (time: string) => formatDateTime(time),
      },
      {
        title: t("common.actions"),
        key: "actions",
        width: 72,
        fixed: "right",
        align: "center",
        render: (_, record) => (
          <TableActionButton
            label={t("alertCenter.viewDetails")}
            icon={<EyeOutlined />}
            onClick={() => void openAlertDetail(record)}
          />
        ),
      },
    ],
    [formatAlertTitle, openAlertDetail, t],
  );

  return (
    <div className="tds-investigate-page tds-alert-center">
      <section className="tds-investigate-metrics" aria-label={t("alertCenter.listTitle")}>
        <div className="tds-investigate-metric">
          <span className="tds-investigate-metric__label">
            <ExclamationCircleOutlined />
            {t("alertCenter.totalAlerts")}
          </span>
          <strong>{analytics?.totalAlerts || 0}</strong>
        </div>
        <div className="tds-investigate-metric tds-investigate-metric--positive">
          <span className="tds-investigate-metric__label">
            <CheckCircleOutlined />
            {t("alertCenter.resolved")}
          </span>
          <strong>{analytics?.resolvedAlerts || 0}</strong>
        </div>
        <div className="tds-investigate-metric tds-investigate-metric--danger">
          <span className="tds-investigate-metric__label">
            <ArrowUpOutlined />
            {t("alertCenter.escalated")}
          </span>
          <strong>{analytics?.escalatedAlerts || 0}</strong>
        </div>
        <div className="tds-investigate-metric">
          <span className="tds-investigate-metric__label">
            {t("alertCenter.avgResolutionTime")}
          </span>
          <strong>
            {analytics?.averageResolutionTime
              ? `${Math.round(analytics.averageResolutionTime / 60)}${t("common.minuteShort")}`
              : "-"}
          </strong>
        </div>
      </section>

      <section className="tds-investigate-panel tds-investigate-queue">
        <div className="tds-investigate-view-switch">
          <Segmented
            value={viewMode}
            onChange={(val) => setViewMode(val as "detail" | "grouped")}
            options={[
              {
                label: t("alertCenter.detailView"),
                value: "detail",
                icon: <UnorderedListOutlined />,
              },
              {
                label: t("alertCenter.groupedView"),
                value: "grouped",
                icon: <AppstoreOutlined />,
              },
            ]}
          />
        </div>

          {viewMode === "detail" && (
            <ProTable<Alert>
              {...alertTableToolbar}
              className="tds-investigate-table"
              headerTitle={t("alertCenter.listTitle")}
              actionRef={actionRef}
              rowKey="id"
              columns={columns}
              tableLayout="fixed"
              request={async (params) => {
                try {
                  const result = await alertService.getAlerts({
                    customerId: effectiveCustomerId,
                    page: (params.current || 1) - 1,
                    size: params.pageSize || 20,
                    status: params.status,
                    severity: params.severity,
                    sortBy: "created_at",
                    sortDir: "DESC",
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
              pagination={{ defaultPageSize: 20, showSizeChanger: true }}
              search={PRO_TABLE_SEARCH}
              scroll={{ x: detailScrollX }}
              toolBarRender={() => [
                <Button
                  key="refresh"
                  icon={<ReloadOutlined />}
                  onClick={() => {
                    actionRef.current?.reload();
                    alertService
                      .getAnalytics()
                      .then(setAnalytics)
                      .catch(() => {});
                  }}
                >
                  {t("common.refresh")}
                </Button>,
              ]}
            />
          )}

          {viewMode === "grouped" && (
            <Table<GroupedAlertResponse>
              className="tds-investigate-table"
              rowKey="attackMac"
              columns={groupedAlertColumns}
              tableLayout="fixed"
              dataSource={groupedData}
              loading={groupedLoading}
              scroll={{ x: 820 }}
              pagination={{
                total: groupedTotal,
                defaultPageSize: 20,
                showSizeChanger: true,
                showTotal: (all) => `${all} ${t("threatList.items")}`,
                onChange: (p, ps) => {
                  void fetchGroupedAlerts(p, ps);
                },
              }}
              expandable={{
                expandedRowKeys: expandedMacs,
                onExpand: (expanded, record) => {
                  void handleAlertExpand(expanded, record);
                },
                expandedRowRender: (record) => {
                  const rows = subRows[record.attackMac];
                  if (!rows) return <Spin size="small" />;
                  return (
                    <Table<Alert>
                      className="tds-investigate-table tds-investigate-table--nested"
                      rowKey="id"
                      columns={subAlertColumns}
                      dataSource={rows}
                      pagination={false}
                      size="small"
                      tableLayout="fixed"
                      scroll={{ x: 874 }}
                    />
                  );
                },
              }}
            />
          )}
      </section>

      {/* 解决告警弹窗 */}
      <Modal
        title={t("alertCenter.resolveAlert")}
        open={resolveModalOpen}
        onOk={handleResolve}
        onCancel={() => {
          if (alertMutation.submitting) return;
          setResolveModalOpen(false);
          setResolution("");
        }}
        confirmLoading={alertMutation.submitting}
        cancelButtonProps={{ disabled: alertMutation.submitting }}
        okButtonProps={{
          disabled: !resolution.trim() || !currentUser?.username,
        }}
        okText={t("alertCenter.confirmResolve")}
      >
        <Space direction="vertical" style={{ width: "100%" }}>
          <Typography.Text type="secondary">
            {t("alertCenter.resolverNotice", {
              name:
                currentUser?.displayName ||
                currentUser?.username ||
                t("common.unknown"),
            })}
          </Typography.Text>
          <div className="tds-counted-textarea">
            <Input.TextArea
              aria-label={t("alertCenter.resolution")}
              placeholder={t("alertCenter.resolution")}
              rows={4}
              maxLength={1000}
              showCount
              value={resolution}
              onChange={(e) => setResolution(e.target.value)}
            />
          </div>
        </Space>
      </Modal>

      {/* 分配告警弹窗 */}
      <Modal
        title={t("alertCenter.assignAlert")}
        open={assignModalOpen}
        onOk={handleAssign}
        onCancel={() => {
          if (alertMutation.submitting) return;
          setAssignModalOpen(false);
          setAssignTo("");
        }}
        confirmLoading={alertMutation.submitting}
        cancelButtonProps={{ disabled: alertMutation.submitting }}
        okButtonProps={{ disabled: !assignTo }}
        okText={t("alertCenter.confirmAssign")}
      >
        <Select
          aria-label={t("alertCenter.assignedTo")}
          showSearch
          optionFilterProp="label"
          loading={assignableUsersLoading}
          placeholder={t("alertCenter.assignedTo")}
          value={assignTo}
          onChange={setAssignTo}
          options={assigneeOptions}
          style={{ width: "100%" }}
          notFoundContent={t("alertCenter.noAssignees")}
        />
      </Modal>

      {/* 提级处理弹窗 */}
      <Modal
        title={t("alertCenter.escalateAlert")}
        open={escalateModalOpen}
        onOk={handleEscalate}
        onCancel={() => {
          if (alertMutation.submitting) return;
          setEscalateModalOpen(false);
          setEscalateReason("");
        }}
        confirmLoading={alertMutation.submitting}
        cancelButtonProps={{ disabled: alertMutation.submitting }}
        okButtonProps={{ disabled: !escalateReason.trim() }}
        okText={t("alertCenter.confirmEscalate")}
      >
        <div className="tds-counted-textarea">
          <Input.TextArea
            aria-label={t("alertCenter.escalationReason")}
            placeholder={t("alertCenter.escalationReason")}
            rows={3}
            maxLength={1000}
            showCount
            value={escalateReason}
            onChange={(e) => setEscalateReason(e.target.value)}
          />
        </div>
      </Modal>

      <Drawer
        title={t("alertCenter.detailTitle")}
        open={detailOpen}
        width="min(560px, calc(100vw - 24px))"
        onClose={() => setDetailOpen(false)}
      >
        <Spin spinning={detailLoading}>
          {detailAlert && (
            <Descriptions
              className="tds-detail-descriptions tds-alert-detail-descriptions"
              column={1}
              size="small"
              bordered
              labelStyle={{ width: 132 }}
            >
              <Descriptions.Item label={t("common.id")}>
                {detailAlert.id}
              </Descriptions.Item>
              <Descriptions.Item label={t("alertCenter.titleColumn")}>
                {formatAlertTitle(detailAlert)}
              </Descriptions.Item>
              <Descriptions.Item label={t("common.severity")}>
                <Tag color={severityColorMap[detailAlert.severity]}>
                  {detailAlert.severity}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label={t("alertCenter.workflowStatus")}>
                <SemanticStatusTag
                  tone={statusToneMap[detailAlert.status] || "neutral"}
                >
                  {t(`alertCenter.status.${detailAlert.status}`, {
                    defaultValue: detailAlert.status,
                  })}
                </SemanticStatusTag>
              </Descriptions.Item>
              <Descriptions.Item label={t("alertCenter.attackMac")}>
                <NetworkIdentifier value={detailAlert.attackMac} kind="mac" />
              </Descriptions.Item>
              <Descriptions.Item label={t("alertCenter.threatScore")}>
                {detailAlert.threatScore?.toFixed(2) || "-"}
              </Descriptions.Item>
              <Descriptions.Item label={t("alertCenter.owner")}>
                {detailAlert.assignedTo || t("alertCenter.unassigned")}
              </Descriptions.Item>
              <Descriptions.Item label={t("alertCenter.handlingLevel")}>
                {(detailAlert.escalationLevel || 0) > 0
                  ? t("alertCenter.escalatedLevel", {
                      level: detailAlert.escalationLevel,
                    })
                  : t("alertCenter.standardHandling")}
              </Descriptions.Item>
              {(detailAlert.escalationLevel || 0) > 0 && (
                <Descriptions.Item label={t("alertCenter.escalationReason")}>
                  {detailAlert.escalationReason || "-"}
                </Descriptions.Item>
              )}
              <Descriptions.Item label={t("common.createdAt")}>
                {formatDateTime(detailAlert.createdAt)}
              </Descriptions.Item>
              <Descriptions.Item label={t("alertCenter.updatedAt")}>
                {formatDateTime(detailAlert.updatedAt)}
              </Descriptions.Item>
              <Descriptions.Item label={t("common.description")}>
                <Typography.Paragraph
                  style={{ marginBottom: 0, whiteSpace: "pre-wrap" }}
                >
                  {detailAlert.description || "-"}
                </Typography.Paragraph>
              </Descriptions.Item>
              {detailAlert.status === AlertStatus.RESOLVED && (
                <>
                  <Descriptions.Item label={t("alertCenter.resolvedBy")}>
                    {detailAlert.resolvedBy || "-"}
                  </Descriptions.Item>
                  <Descriptions.Item label={t("alertCenter.resolvedAt")}>
                    {formatDateTime(detailAlert.resolvedAt)}
                  </Descriptions.Item>
                  <Descriptions.Item label={t("alertCenter.resolution")}>
                    <Typography.Paragraph
                      style={{ marginBottom: 0, whiteSpace: "pre-wrap" }}
                    >
                      {detailAlert.resolution || "-"}
                    </Typography.Paragraph>
                  </Descriptions.Item>
                </>
              )}
            </Descriptions>
          )}
        </Spin>
      </Drawer>
    </div>
  );
};

export default AlertCenter;
