import { useState, useEffect, useRef, useCallback } from "react";
import {
  Card,
  Tag,
  Button,
  Space,
  Input,
  Tabs,
  Table,
  Popconfirm,
  Switch,
  message,
  Descriptions,
  Alert,
  Tooltip,
  Typography,
  Spin,
} from "antd";
import type { TableColumnsType } from "antd";
import {
  SearchOutlined,
  ReloadOutlined,
  DeleteOutlined,
  SyncOutlined,
  EyeOutlined,
  QuestionCircleOutlined,
} from "@ant-design/icons";
import { ProTable } from "@ant-design/pro-components";
import type { ActionType, ProColumns } from "@ant-design/pro-components";
import { useTranslation } from "react-i18next";
import type {
  ThreatIndicator,
  ThreatFeed,
  IpLookupResult,
  IntelStatistics,
} from "@/types";
import { IntelSeverity } from "@/types";
import intelService from "@/services/intel";
import { formatDateTimeMinute } from "@/utils/formatDateTime";
import { getConfigsByCategory } from "@/services/config";
import SemanticStatusTag, {
  type SemanticStatusTone,
} from "@/components/SemanticStatusTag";
import TableActionButton from "@/components/TableActionButton";
import TableActions from "@/components/TableActions";
import TableCellText from "@/components/TableCellText";
import { useResponsiveScrollX } from "@/components/tableScroll";
import { PRO_TABLE_SEARCH } from "@/components/proTableConfig";
import { useProTableToolbar } from "@/components/useProTableToolbar";
import "../investigate.css";

const severityColorMap: Record<string, string> = {
  [IntelSeverity.CRITICAL]: "red",
  [IntelSeverity.HIGH]: "orange",
  [IntelSeverity.MEDIUM]: "gold",
  [IntelSeverity.LOW]: "blue",
  [IntelSeverity.INFO]: "default",
};

function isFeedPollable(feed: ThreatFeed): boolean {
  return feed.pollable ?? feed.feedType?.toUpperCase() !== "INTERNAL";
}

/**
 * 威胁情报页面
 */
const ThreatIntel = () => {
  const { t } = useTranslation();
  const indicatorTableToolbar = useProTableToolbar();
  const actionRef = useRef<ActionType>();
  const [statistics, setStatistics] = useState<IntelStatistics | null>(null);
  const [lookupIp, setLookupIp] = useState("");
  const [lookupResult, setLookupResult] = useState<IpLookupResult | null>(null);
  const [lookupLoading, setLookupLoading] = useState(false);
  const [feeds, setFeeds] = useState<ThreatFeed[]>([]);
  const [feedsLoading, setFeedsLoading] = useState(false);
  const [statisticsLoading, setStatisticsLoading] = useState(false);
  const [statisticsError, setStatisticsError] = useState(false);
  const [pollingFeedId, setPollingFeedId] = useState<number | null>(null);
  const [updatingFeedId, setUpdatingFeedId] = useState<number | null>(null);
  const [sightingIndicatorId, setSightingIndicatorId] = useState<number | null>(
    null,
  );
  const [deletingIndicatorId, setDeletingIndicatorId] = useState<number | null>(
    null,
  );
  const [configuredKeyCount, setConfiguredKeyCount] = useState<number | null>(
    null,
  );

  const loadStatistics = useCallback(async () => {
    setStatisticsLoading(true);
    try {
      const data = await intelService.getStatistics();
      setStatistics(data);
      setStatisticsError(false);
    } catch {
      setStatisticsError(true);
    } finally {
      setStatisticsLoading(false);
    }
  }, []);

  const loadFeeds = useCallback(async () => {
    setFeedsLoading(true);
    try {
      const data = await intelService.getFeeds();
      setFeeds(data);
    } catch {
      message.error(t("threatIntel.messageLoadFeedsFailed"));
    } finally {
      setFeedsLoading(false);
    }
  }, [t]);

  /** IP查询 */
  const handleLookup = async () => {
    if (!lookupIp.trim() || lookupLoading) return;
    setLookupLoading(true);
    try {
      const result = await intelService.lookup(lookupIp.trim());
      setLookupResult(result);
    } catch {
      message.error(t("threatIntel.messageLookupFailed"));
    } finally {
      setLookupLoading(false);
    }
  };

  /** 手动触发Feed轮询 */
  const handlePollFeed = async (id: number) => {
    if (pollingFeedId !== null) return;
    setPollingFeedId(id);
    try {
      const result = await intelService.pollFeed(id);
      if (result.success === false) {
        message.error(
          result.errorMessage || t("threatIntel.messagePollFailed"),
        );
      } else {
        message.success(
          t("threatIntel.messagePollComplete", {
            count: result.indicatorsProcessed || 0,
          }),
        );
      }
      await Promise.all([loadFeeds(), loadStatistics()]);
      actionRef.current?.reload();
    } catch (error) {
      const reason = (error as { response?: { data?: { message?: string } } })
        .response?.data?.message;
      message.error(reason || t("threatIntel.messagePollFailed"));
      await loadFeeds();
    } finally {
      setPollingFeedId(null);
    }
  };

  useEffect(() => {
    void loadStatistics();
    getConfigsByCategory("tire_api_keys")
      .then((configs) =>
        setConfiguredKeyCount(
          configs.filter((config) => config.hasValue).length,
        ),
      )
      .catch(() => setConfiguredKeyCount(null));
    loadFeeds();
  }, [loadFeeds, loadStatistics]);

  const handleFeedEnabledChange = async (
    feed: ThreatFeed,
    enabled: boolean,
  ) => {
    if (updatingFeedId !== null) return;
    setUpdatingFeedId(feed.id);
    try {
      await intelService.updateFeed(feed.id, { enabled });
      message.success(t("threatIntel.feedStatusSaved"));
      await Promise.all([loadFeeds(), loadStatistics()]);
    } catch {
      message.error(t("common.updateFailed"));
    } finally {
      setUpdatingFeedId(null);
    }
  };

  /** 删除指标 */
  const handleDeleteIndicator = async (id: number) => {
    if (deletingIndicatorId !== null) return;
    setDeletingIndicatorId(id);
    try {
      await intelService.deleteIndicator(id);
      message.success(t("common.deleteSuccess"));
      actionRef.current?.reload();
      void loadStatistics();
    } catch {
      message.error(t("common.deleteFailed"));
    } finally {
      setDeletingIndicatorId(null);
    }
  };

  /** 添加目击记录 */
  const handleSighting = async (id: number) => {
    if (sightingIndicatorId !== null) return;
    setSightingIndicatorId(id);
    try {
      await intelService.addSighting(id);
      message.success(t("threatIntel.messageSightingAdded"));
      actionRef.current?.reload();
    } catch {
      message.error(t("common.operationFailed"));
    } finally {
      setSightingIndicatorId(null);
    }
  };

  const indicatorColumns: ProColumns<ThreatIndicator>[] = [
    {
      title: t("common.id"),
      dataIndex: "id",
      width: 72,
      align: "center",
      search: false,
    },
    {
      title: t("threatIntel.iocValue"),
      dataIndex: "iocValue",
      copyable: true,
      ellipsis: true,
      width: 200,
    },
    {
      title: t("threatIntel.iocType"),
      dataIndex: "iocType",
      width: 100,
      valueType: "select",
      valueEnum: {
        IP_V4: { text: "IPv4" },
        IP_V6: { text: "IPv6" },
        CIDR: { text: "CIDR" },
        DOMAIN: { text: t("threatIntel.typeDomain") },
        FILE_HASH: { text: t("threatIntel.typeFileHash") },
      },
    },
    {
      title: t("common.severity"),
      dataIndex: "severity",
      width: 90,
      valueType: "select",
      valueEnum: {
        CRITICAL: { text: t("common.severityCritical") },
        HIGH: { text: t("common.severityHigh") },
        MEDIUM: { text: t("common.severityMedium") },
        LOW: { text: t("common.severityLow") },
        INFO: { text: t("common.severityInfo") },
      },
      render: (_, record) => (
        <Tag color={severityColorMap[record.severity]}>{record.severity}</Tag>
      ),
    },
    {
      title: t("threatIntel.confidence"),
      dataIndex: "confidence",
      width: 96,
      align: "center",
      search: false,
      render: (_, record) => `${record.confidence}%`,
    },
    {
      title: t("common.source"),
      dataIndex: "sourceName",
      width: 140,
      search: false,
      render: (_, record) => <TableCellText value={record.sourceName} />,
    },
    {
      title: t("threatIntel.sightingCount"),
      dataIndex: "sightingCount",
      width: 104,
      align: "center",
      search: false,
    },
    {
      title: t("threatIntel.lastSeen"),
      dataIndex: "lastSeenAt",
      width: 160,
      search: false,
      render: (_, record) => formatDateTimeMinute(record.lastSeenAt),
    },
    {
      title: t("common.actions"),
      width: 108,
      key: "actions",
      fixed: "right",
      search: false,
      render: (_, record) => (
        <TableActions label={t("common.actions")}>
          <TableActionButton
            label={t("threatIntel.recordSighting")}
            icon={<EyeOutlined />}
            loading={sightingIndicatorId === record.id}
            disabled={
              sightingIndicatorId !== null && sightingIndicatorId !== record.id
            }
            onClick={() => handleSighting(record.id)}
          />
          <Popconfirm
            title={t("common.confirmDelete")}
            onConfirm={() => handleDeleteIndicator(record.id)}
          >
            <TableActionButton
              label={t("common.delete")}
              danger
              icon={<DeleteOutlined />}
              loading={deletingIndicatorId === record.id}
              disabled={
                deletingIndicatorId !== null &&
                deletingIndicatorId !== record.id
              }
            />
          </Popconfirm>
        </TableActions>
      ),
    },
  ];

  const feedColumns: TableColumnsType<ThreatFeed> = [
    {
      title: t("common.id"),
      dataIndex: "id",
      key: "id",
      width: 72,
      responsive: ["md"],
    },
    {
      title: t("common.name"),
      dataIndex: "feedName",
      key: "feedName",
      width: 148,
      ellipsis: true,
      render: (value: string) => (
        <Typography.Text strong>{value || "-"}</Typography.Text>
      ),
    },
    {
      title: "URL",
      dataIndex: "feedUrl",
      key: "feedUrl",
      width: 260,
      ellipsis: true,
      responsive: ["xxl"],
      render: (value?: string) => (
        <TableCellText
          value={value}
          className="tds-table-cell-text--secondary"
        />
      ),
    },
    {
      title: t("common.type"),
      dataIndex: "feedType",
      key: "feedType",
      width: 104,
      responsive: ["lg"],
    },
    {
      title: t("threatIntel.apiKeyVariable"),
      dataIndex: "apiKeyEnvVar",
      key: "apiKeyEnvVar",
      width: 168,
      ellipsis: true,
      responsive: ["xxl"],
      render: (value?: string) => value || t("threatIntel.notRequired"),
    },
    {
      title: t("common.enabled"),
      dataIndex: "enabled",
      key: "enabled",
      width: 76,
      align: "center",
      render: (enabled: boolean, record: ThreatFeed) => (
        <Switch
          checked={enabled}
          loading={updatingFeedId === record.id}
          disabled={updatingFeedId !== null && updatingFeedId !== record.id}
          aria-label={t("threatIntel.toggleFeed", { name: record.feedName })}
          onChange={(checked) => void handleFeedEnabledChange(record, checked)}
        />
      ),
    },
    {
      title: t("threatIntel.indicatorCount"),
      dataIndex: "indicatorCount",
      key: "indicatorCount",
      width: 120,
      align: "center",
      responsive: ["sm"],
    },
    {
      title: t("common.status"),
      dataIndex: "lastPollStatus",
      key: "lastPollStatus",
      width: 148,
      align: "center",
      render: (value?: string, record?: ThreatFeed) => {
        if (record && !isFeedPollable(record)) {
          return (
            <SemanticStatusTag tone="neutral">
              {record.feedType?.toUpperCase() === "INTERNAL"
                ? t("threatIntel.localSource")
                : t("threatIntel.pollUnsupported")}
            </SemanticStatusTag>
          );
        }
        if (!value) {
          return (
            <SemanticStatusTag tone="neutral">
              {t("threatIntel.notPolled")}
            </SemanticStatusTag>
          );
        }
        const normalized = value.toUpperCase();
        const tone: SemanticStatusTone =
          normalized === "SUCCESS"
            ? "success"
            : normalized === "FAILED"
              ? "danger"
              : normalized === "RATE_LIMITED"
                ? "warning"
                : "info";
        const label =
          normalized === "SUCCESS"
            ? t("threatIntel.pollSuccess")
            : normalized === "FAILED"
              ? t("threatIntel.pollFailed")
              : normalized === "RATE_LIMITED"
                ? t("threatIntel.rateLimited")
                : t("threatIntel.polling");
        const tag = <SemanticStatusTag tone={tone}>{label}</SemanticStatusTag>;
        return record?.lastPollError ? (
          <Tooltip title={record.lastPollError}>
            <span>{tag}</span>
          </Tooltip>
        ) : (
          tag
        );
      },
    },
    {
      title: t("threatIntel.lastPolled"),
      dataIndex: "lastPollAt",
      key: "lastPollAt",
      width: 156,
      responsive: ["md"],
      render: (val?: string) => (val ? formatDateTimeMinute(val) : "-"),
    },
    {
      title: t("common.actions"),
      key: "actions",
      width: 120,
      fixed: "right",
      align: "center",
      render: (_: unknown, record: ThreatFeed) => {
        if (!isFeedPollable(record)) {
          return (
            <TableCellText
              className="tds-table-cell-text--secondary"
              value={
                record.feedType?.toUpperCase() === "INTERNAL"
                  ? t("threatIntel.noPollRequired")
                  : t("threatIntel.pollUnsupported")
              }
            />
          );
        }

        const button = (
          <TableActionButton
            label={t("threatIntel.poll")}
            icon={<SyncOutlined />}
            loading={pollingFeedId === record.id}
            disabled={
              !record.enabled ||
              (pollingFeedId !== null && pollingFeedId !== record.id)
            }
            onClick={() => handlePollFeed(record.id)}
          />
        );

        if (!record.enabled) {
          return (
            <Tooltip title={t("threatIntel.enableBeforePoll")}>
              <span>{button}</span>
            </Tooltip>
          );
        }
        if (record.pollBatchLimit) {
          return (
            <Tooltip
              title={t("threatIntel.pollBatchLimit", {
                count: record.pollBatchLimit,
              })}
            >
              <span>{button}</span>
            </Tooltip>
          );
        }
        return button;
      },
    },
  ];


  // Scroll budget of the feed columns visible at the current breakpoint.
  const feedScrollX = useResponsiveScrollX(feedColumns);

  return (
    <div className="tds-investigate-page tds-threat-intel-page">
      {configuredKeyCount === 0 && (
        <Alert
          type="warning"
          showIcon
          message={t("threatIntel.apiKeyMissingTitle")}
          description={t("threatIntel.apiKeyMissingDescription")}
        />
      )}
      {configuredKeyCount !== null &&
        configuredKeyCount > 0 &&
        !feeds.some((feed) => feed.feedName !== "internal" && feed.enabled) && (
          <Alert
            type="info"
            showIcon
            message={t("threatIntel.feedDisabledTitle")}
            description={t("threatIntel.feedDisabledDescription")}
          />
        )}
      {statisticsError && (
        <Alert
          type="error"
          showIcon
          message={t("threatIntel.statisticsLoadFailed")}
          action={
            <Button size="small" onClick={() => void loadStatistics()}>
              {t("threatIntel.retry")}
            </Button>
          }
        />
      )}
      <Spin spinning={statisticsLoading && !statistics}>
        <section className="tds-investigate-metrics" aria-label={t("threatIntel.threatIndicatorManagement")}>
          <div className="tds-investigate-metric">
            <Tooltip title={t("threatIntel.totalIndicatorsHelp")}>
              <span className="tds-investigate-metric__label">
                {t("threatIntel.totalIndicators")}
                <QuestionCircleOutlined aria-hidden="true" />
              </span>
            </Tooltip>
            <strong>{statistics?.totalIndicators ?? 0}</strong>
          </div>
          <div className="tds-investigate-metric tds-investigate-metric--positive">
            <Tooltip title={t("threatIntel.activeIndicatorsHelp")}>
              <span className="tds-investigate-metric__label">
                {t("threatIntel.activeIndicators")}
                <QuestionCircleOutlined aria-hidden="true" />
              </span>
            </Tooltip>
            <strong>{statistics?.activeIndicators ?? 0}</strong>
          </div>
          <div className="tds-investigate-metric">
            <span className="tds-investigate-metric__label">
              {t("threatIntel.feedCount")}
            </span>
            <strong>{statistics?.totalFeeds ?? 0}</strong>
          </div>
          <div className="tds-investigate-metric tds-investigate-metric--accent">
            <Tooltip title={t("threatIntel.activeFeedsHelp")}>
              <span className="tds-investigate-metric__label">
                {t("threatIntel.activeFeeds")}
                <QuestionCircleOutlined aria-hidden="true" />
              </span>
            </Tooltip>
            <strong>{statistics?.activeFeeds ?? 0}</strong>
          </div>
        </section>
      </Spin>

      {/* IP查询 */}
      <Card className="tds-investigate-panel tds-intel-lookup" title={t("threatIntel.ipLookup")} size="small">
        <Space className="tds-intel-lookup__form" wrap style={{ width: "100%" }}>
          <Input
            name="ip-address"
            aria-label={t("threatIntel.inputIpAddress")}
            autoComplete="off"
            spellCheck={false}
            placeholder={t("threatIntel.inputIpAddress")}
            value={lookupIp}
            onChange={(e) => setLookupIp(e.target.value)}
            onPressEnter={handleLookup}
            style={{ width: "min(300px, 100%)" }}
            prefix={<SearchOutlined />}
          />
          <Button
            type="primary"
            loading={lookupLoading}
            disabled={!lookupIp.trim()}
            onClick={handleLookup}
          >
            {t("common.search")}
          </Button>
        </Space>
        {lookupResult && (
          <Descriptions
            className="tds-detail-descriptions"
            bordered
            size="small"
            column={{ xs: 1, sm: 2, lg: 3 }}
            style={{ marginTop: 16 }}
          >
            <Descriptions.Item label="IP">{lookupResult.ip}</Descriptions.Item>
            <Descriptions.Item label={t("threatIntel.found")}>
              {lookupResult.found ? (
                <Tag color="red">{t("threatIntel.hit")}</Tag>
              ) : (
                <Tag color="green">{t("threatIntel.notHit")}</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label={t("threatIntel.confidence")}>
              {lookupResult.confidence}%
            </Descriptions.Item>
            <Descriptions.Item label={t("common.severity")}>
              <Tag color={severityColorMap[lookupResult.severity]}>
                {lookupResult.severity}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label={t("threatIntel.intelWeight")}>
              {lookupResult.intelWeight.toFixed(2)}
            </Descriptions.Item>
            <Descriptions.Item label={t("threatIntel.matchedIndicators")}>
              {lookupResult.indicatorCount}
            </Descriptions.Item>
            <Descriptions.Item label={t("common.source")}>
              {lookupResult.sources.length > 0
                ? lookupResult.sources.join(", ")
                : "-"}
            </Descriptions.Item>
            <Descriptions.Item label={t("threatIntel.lastSeen")}>
              {lookupResult.lastSeenAt
                ? formatDateTimeMinute(lookupResult.lastSeenAt)
                : "-"}
            </Descriptions.Item>
            <Descriptions.Item label={t("threatIntel.tireScore")}>
              {lookupResult.tireScore ?? "-"}
            </Descriptions.Item>
            <Descriptions.Item label={t("threatIntel.tireLevel")}>
              {lookupResult.tireLevel || "-"}
            </Descriptions.Item>
            <Descriptions.Item label={t("threatIntel.tireVerdict")} span={3}>
              {lookupResult.tireVerdict || "-"}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Card>

      <Tabs
        className="tds-investigate-tabs tds-threat-intel-tabs"
        defaultActiveKey="indicators"
        items={[
          {
            key: "indicators",
            label: t("threatIntel.threatIndicators"),
            children: (
              <ProTable<ThreatIndicator>
                {...indicatorTableToolbar}
                className="tds-investigate-table"
                headerTitle={t("threatIntel.threatIndicatorManagement")}
                actionRef={actionRef}
                rowKey="id"
                columns={indicatorColumns}
                request={async (params) => {
                  try {
                    const result = await intelService.getIndicators({
                      page: (params.current || 1) - 1,
                      size: params.pageSize || 20,
                      iocType: params.iocType,
                      severity: params.severity,
                      sort: "id,desc",
                    });
                    return {
                      data: result.content,
                      total: result.totalElements,
                      success: true,
                    };
                  } catch {
                    message.error(t("threatIntel.messageLoadIndicatorsFailed"));
                    return { data: [], total: 0, success: false };
                  }
                }}
                pagination={{ defaultPageSize: 20 }}
                search={PRO_TABLE_SEARCH}
                tableLayout="fixed"
                scroll={{ x: 1120 }}
                toolBarRender={() => [
                  <Button
                    key="refresh"
                    icon={<ReloadOutlined />}
                    onClick={() => actionRef.current?.reload()}
                  >
                    {t("common.refresh")}
                  </Button>,
                ]}
              />
            ),
          },
          {
            key: "feeds",
            label: t("threatIntel.feedManagement"),
            children: (
              <Card className="tds-investigate-panel tds-intel-feed-panel" variant="borderless">
                <Space
                  direction="vertical"
                  size={12}
                  style={{ width: "100%", marginBottom: 16 }}
                >
                  <Button
                    icon={<ReloadOutlined />}
                    loading={feedsLoading}
                    onClick={() =>
                      void Promise.all([loadFeeds(), loadStatistics()])
                    }
                  >
                    {t("common.refresh")}
                  </Button>
                  <Typography.Text type="secondary">
                    {t("threatIntel.feedSemanticsHelp")}
                  </Typography.Text>
                </Space>
                <Table
                  className="tds-investigate-table"
                  columns={feedColumns}
                  dataSource={feeds}
                  rowKey="id"
                  loading={feedsLoading}
                  size="small"
                  tableLayout="fixed"
                  pagination={false}
                  scroll={{ x: feedScrollX }}
                />
              </Card>
            ),
          },
        ]}
      />
    </div>
  );
};

export default ThreatIntel;
