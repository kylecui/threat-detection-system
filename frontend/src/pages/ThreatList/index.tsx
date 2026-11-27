import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Button,
  Alert,
  Card,
  Collapse,
  DatePicker,
  Descriptions,
  Divider,
  Drawer,
  Grid,
  Input,
  Row,
  Col,
  Modal,
  Select,
  Segmented,
  Space,
  Spin,
  Table,
  Tag,
  Tooltip,
  Typography,
  message,
} from "antd";
import {
  AppstoreOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  DownloadOutlined,
  ExclamationCircleOutlined,
  EyeOutlined,
  ReloadOutlined,
  UnorderedListOutlined,
} from "@ant-design/icons";
import type { TableColumnsType, TableProps } from "antd";
import type { Dayjs } from "dayjs";
import dayjs from "dayjs";
import { formatDateTime as formatLocalizedDateTime } from "@/utils/formatDateTime";
import { useTranslation } from "react-i18next";
import threatService from "@/services/threat";
import type {
  GroupedThreatResponse,
  ThreatAssessment,
  ThreatQueryFilter,
} from "@/types";
import { ThreatLevel } from "@/types";
import { useScope } from "@/contexts/ScopeContext";
import PermissionGate from "@/components/PermissionGate";
import NetworkIdentifier from "@/components/NetworkIdentifier";
import { useResponsiveScrollX } from "@/components/tableScroll";
import { usePermission } from "@/hooks/usePermission";
import "../investigate.css";

const { Search } = Input;
const { RangePicker } = DatePicker;
const { Text } = Typography;

type ThreatLevelFilterValue = ThreatLevel | "ALL";
type DetectionTierFilterValue = 1 | 2 | 3 | "ALL";

type TimeRangeValue = [Dayjs | null, Dayjs | null] | null;

const DEFAULT_PAGE = 1;
const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 450;

const THREAT_LEVEL_COLOR_MAP: Record<ThreatLevel, string> = {
  [ThreatLevel.CRITICAL]: "red",
  [ThreatLevel.HIGH]: "orange",
  [ThreatLevel.MEDIUM]: "gold",
  [ThreatLevel.LOW]: "blue",
  [ThreatLevel.INFO]: "default",
};

function formatDateTime(value?: string): string {
  return formatLocalizedDateTime(value);
}

function formatNumber(value?: number): string {
  if (typeof value !== "number" || Number.isNaN(value)) return "-";
  return value.toFixed(2);
}

function parsePortList(portList?: string): string[] {
  if (!portList) return [];
  return portList
    .split(/[\s,;|]+/)
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
}

function normalizeMitigationRecommendations(input?: string[]): string[] {
  if (!input || input.length === 0) return [];
  return input.map((item) => item.trim()).filter((item) => item.length > 0);
}

function triggerBlobDownload(
  content: string,
  fileName: string,
  mimeType: string,
) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = fileName;
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  URL.revokeObjectURL(url);
}

/**
 * 威胁列表页面（增强版）
 *
 * 能力清单：
 * 1) 筛选栏（威胁等级 / 时间范围 / 攻击者MAC）
 * 2) 行选择 + 批量删除
 * 3) 行点击详情抽屉（按需拉取详情）
 * 4) 当前页导出 CSV / JSON
 * 5) 保持原分页行为与字段映射
 */
const ThreatList = () => {
  const { t } = useTranslation();
  const detectionWindowLabel = useCallback(
    (tier?: number): string => {
      if (tier === 1) return t("overview.tier1Window");
      if (tier === 2) return t("overview.tier2Window");
      if (tier === 3) return t("overview.tier3Window");
      return t("common.unknown");
    },
    [t],
  );
  const screens = Grid.useBreakpoint();
  const { effectiveCustomerId, initialized } = useScope();
  const { isSuperAdmin } = usePermission();
  const isDevMode = useMemo(() => {
    if (!isSuperAdmin) return false;
    if (new URLSearchParams(window.location.search).get("dev") === "1")
      return true;
    return localStorage.getItem("tds_dev_mode") === "true";
  }, [isSuperAdmin]);

  const threatLevelOptions: Array<{
    label: string;
    value: ThreatLevelFilterValue;
  }> = [
    { label: t("common.all"), value: "ALL" },
    { label: ThreatLevel.CRITICAL, value: ThreatLevel.CRITICAL },
    { label: ThreatLevel.HIGH, value: ThreatLevel.HIGH },
    { label: ThreatLevel.MEDIUM, value: ThreatLevel.MEDIUM },
    { label: ThreatLevel.LOW, value: ThreatLevel.LOW },
    { label: ThreatLevel.INFO, value: ThreatLevel.INFO },
  ];

  // ─────────────────────────────────────────────────────────────
  // 基础列表状态
  // ─────────────────────────────────────────────────────────────
  const [loading, setLoading] = useState(false);
  const [threats, setThreats] = useState<ThreatAssessment[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(DEFAULT_PAGE);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [viewMode, setViewMode] = useState<"detail" | "grouped">("grouped");
  const [groupedData, setGroupedData] = useState<GroupedThreatResponse[]>([]);
  const [groupedTotal, setGroupedTotal] = useState(0);
  const [groupedLoading, setGroupedLoading] = useState(false);
  const [expandedMacs, setExpandedMacs] = useState<string[]>([]);
  const [subRows, setSubRows] = useState<Record<string, ThreatAssessment[]>>(
    {},
  );

  // ─────────────────────────────────────────────────────────────
  // 筛选状态
  // ─────────────────────────────────────────────────────────────
  const [threatLevelFilter, setThreatLevelFilter] =
    useState<ThreatLevelFilterValue>("ALL");
  const [detectionTierFilter, setDetectionTierFilter] =
    useState<DetectionTierFilterValue>("ALL");
  const [timeRangeFilter, setTimeRangeFilter] = useState<TimeRangeValue>(null);
  const [attackMacInput, setAttackMacInput] = useState("");
  const [debouncedAttackMac, setDebouncedAttackMac] = useState("");

  // ─────────────────────────────────────────────────────────────
  // 行选择与批量动作状态
  // ─────────────────────────────────────────────────────────────
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

  // ─────────────────────────────────────────────────────────────
  // 详情抽屉状态
  // ─────────────────────────────────────────────────────────────
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerLoading, setDrawerLoading] = useState(false);
  const [activeThreatId, setActiveThreatId] = useState<number | null>(null);
  const [activeThreatDetail, setActiveThreatDetail] =
    useState<ThreatAssessment | null>(null);

  // ─────────────────────────────────────────────────────────────
  // 防抖搜索：攻击者MAC
  // ─────────────────────────────────────────────────────────────
  useEffect(() => {
    const handler = window.setTimeout(() => {
      setDebouncedAttackMac(attackMacInput.trim());
    }, SEARCH_DEBOUNCE_MS);

    return () => {
      window.clearTimeout(handler);
    };
  }, [attackMacInput]);

  // ─────────────────────────────────────────────────────────────
  // 查询参数构建
  // ─────────────────────────────────────────────────────────────
  const queryFilter = useMemo<ThreatQueryFilter>(() => {
    const filter: ThreatQueryFilter = {
      customer_id: effectiveCustomerId,
      page: page - 1,
      size: pageSize,
    };

    if (threatLevelFilter !== "ALL") {
      filter.threat_level = threatLevelFilter;
    }

    if (detectionTierFilter !== "ALL") {
      filter.detection_tier = detectionTierFilter;
    }

    if (
      timeRangeFilter &&
      timeRangeFilter.length === 2 &&
      timeRangeFilter[0] &&
      timeRangeFilter[1]
    ) {
      filter.start_time = timeRangeFilter[0].startOf("day").toISOString();
      filter.end_time = timeRangeFilter[1].endOf("day").toISOString();
    }

    if (debouncedAttackMac) {
      filter.attack_mac = debouncedAttackMac;
    }

    return filter;
  }, [
    effectiveCustomerId,
    page,
    pageSize,
    threatLevelFilter,
    detectionTierFilter,
    timeRangeFilter,
    debouncedAttackMac,
  ]);

  // ─────────────────────────────────────────────────────────────
  // 数据加载
  // ─────────────────────────────────────────────────────────────
  const loadThreats = useCallback(async () => {
    if (!initialized || !effectiveCustomerId || viewMode !== "detail") {
      setThreats([]);
      setTotal(0);
      return;
    }
    try {
      setLoading(true);
      const response = await threatService.getThreatList(queryFilter);
      setThreats(response.content || []);
      setTotal(response.totalElements || 0);
    } catch {
      message.error(t("threatList.messageLoadFailed"));
    } finally {
      setLoading(false);
    }
  }, [effectiveCustomerId, initialized, queryFilter, t, viewMode]);

  const fetchGroupedData = useCallback(
    async (p = 1, ps = DEFAULT_PAGE_SIZE) => {
      if (!effectiveCustomerId || !initialized) {
        setGroupedData([]);
        setGroupedTotal(0);
        return;
      }
      setGroupedLoading(true);
      try {
        const result = await threatService.getGroupedThreats({
          customer_id: effectiveCustomerId,
          page: p - 1,
          size: ps,
        });
        setGroupedData(result.content);
        setGroupedTotal(result.totalElements);
      } catch {
        message.error(t("threatList.messageLoadFailed"));
      } finally {
        setGroupedLoading(false);
      }
    },
    [effectiveCustomerId, initialized, t],
  );

  useEffect(() => {
    loadThreats();
  }, [loadThreats]);

  useEffect(() => {
    if (viewMode === "grouped") {
      void fetchGroupedData();
    }
  }, [viewMode, fetchGroupedData]);

  // 筛选变更时回到第一页
  useEffect(() => {
    setPage(DEFAULT_PAGE);
  }, [
    threatLevelFilter,
    detectionTierFilter,
    timeRangeFilter,
    debouncedAttackMac,
  ]);

  // ─────────────────────────────────────────────────────────────
  // 详情抽屉逻辑
  // ─────────────────────────────────────────────────────────────
  const loadThreatDetail = useCallback(
    async (id: number) => {
      try {
        setDrawerLoading(true);
        const detail = await threatService.getThreatDetail(id);
        setActiveThreatDetail(detail);
      } catch {
        message.error(t("threatList.messageLoadDetailFailed"));
        setActiveThreatDetail(null);
      } finally {
        setDrawerLoading(false);
      }
    },
    [t],
  );

  const openDetailDrawer = useCallback(
    (id: number) => {
      setActiveThreatId(id);
      setDrawerOpen(true);
      void loadThreatDetail(id);
    },
    [loadThreatDetail],
  );

  const closeDetailDrawer = useCallback(() => {
    setDrawerOpen(false);
    setActiveThreatId(null);
    setActiveThreatDetail(null);
  }, []);

  // ─────────────────────────────────────────────────────────────
  // 单条删除与批量删除
  // ─────────────────────────────────────────────────────────────
  const handleDelete = useCallback(
    (id: number) => {
      Modal.confirm({
        title: t("common.confirmDelete"),
        icon: <ExclamationCircleOutlined />,
        content: t("threatList.confirmDeleteRecord", { id }),
        okText: t("common.delete"),
        okType: "danger",
        cancelText: t("common.cancel"),
        onOk: async () => {
          try {
            await threatService.deleteThreat(id);
            message.success(t("common.deleteSuccess"));

            if (activeThreatId === id) {
              closeDetailDrawer();
            }

            setSelectedRowKeys((prev) => prev.filter((key) => key !== id));
            await loadThreats();
          } catch {
            message.error(t("common.deleteFailed"));
          }
        },
      });
    },
    [activeThreatId, closeDetailDrawer, loadThreats, t],
  );

  const handleBatchDelete = useCallback(() => {
    const ids = selectedRowKeys
      .map((key) => Number(key))
      .filter((id) => Number.isFinite(id));

    if (ids.length === 0) {
      message.warning(t("threatList.selectRecordsFirst"));
      return;
    }

    Modal.confirm({
      title: t("threatList.confirmBatchDeleteTitle"),
      icon: <ExclamationCircleOutlined />,
      content: t("threatList.confirmBatchDeleteContent", { count: ids.length }),
      okText: t("threatList.batchDelete"),
      okType: "danger",
      cancelText: t("common.cancel"),
      onOk: async () => {
        try {
          await threatService.batchDeleteThreats(ids);
          message.success(t("threatList.batchDeleted", { count: ids.length }));
          setSelectedRowKeys([]);

          if (activeThreatId && ids.includes(activeThreatId)) {
            closeDetailDrawer();
          }

          await loadThreats();
        } catch {
          message.error(t("threatList.messageBatchDeleteFailed"));
        }
      },
    });
  }, [selectedRowKeys, activeThreatId, closeDetailDrawer, loadThreats, t]);

  // ─────────────────────────────────────────────────────────────
  // 导出逻辑（当前页）
  // ─────────────────────────────────────────────────────────────
  const exportCsv = useCallback(() => {
    if (threats.length === 0) {
      message.warning(t("threatList.noExportDataCurrentPage"));
      return;
    }

    const csv = threatService.exportThreatsToCsv(threats);
    const fileName = `threat-list-${dayjs().format("YYYYMMDD-HHmmss")}.csv`;
    triggerBlobDownload(csv, fileName, "text/csv;charset=utf-8;");
    message.success(t("threatList.csvExportSuccess"));
  }, [threats, t]);

  const exportJson = useCallback(() => {
    if (threats.length === 0) {
      message.warning(t("threatList.noExportDataCurrentPage"));
      return;
    }

    const json = threatService.exportThreatsToJson(threats);
    const fileName = `threat-list-${dayjs().format("YYYYMMDD-HHmmss")}.json`;
    triggerBlobDownload(json, fileName, "application/json;charset=utf-8;");
    message.success(t("threatList.jsonExportSuccess"));
  }, [threats, t]);

  // ─────────────────────────────────────────────────────────────
  // 筛选重置
  // ─────────────────────────────────────────────────────────────
  const resetFilters = useCallback(() => {
    setThreatLevelFilter("ALL");
    setDetectionTierFilter("ALL");
    setTimeRangeFilter(null);
    setAttackMacInput("");
    setDebouncedAttackMac("");
    setPage(DEFAULT_PAGE);
  }, []);

  // ─────────────────────────────────────────────────────────────
  // 渲染助手
  // ─────────────────────────────────────────────────────────────
  const renderThreatLevelTag = useCallback((level: ThreatLevel) => {
    return <Tag color={THREAT_LEVEL_COLOR_MAP[level]}>{level}</Tag>;
  }, []);

  const renderPortTags = useCallback((portList?: string) => {
    const ports = parsePortList(portList);
    if (ports.length === 0) {
      return <Text type="secondary">-</Text>;
    }

    return (
      <Space wrap>
        {ports.map((port) => (
          <Tag key={port} color="geekblue">
            {port}
          </Tag>
        ))}
      </Space>
    );
  }, []);

  const renderMitigationList = useCallback(
    (recommendations?: string[]) => {
      const list = normalizeMitigationRecommendations(recommendations);
      if (list.length === 0) {
        return (
          <Text type="secondary">{t("threatList.noRecommendations")}</Text>
        );
      }

      return (
        <ul style={{ margin: 0, paddingInlineStart: 18 }}>
          {list.map((item, index) => (
            <li key={`${item}-${index}`}>{item}</li>
          ))}
        </ul>
      );
    },
    [t],
  );

  const handleExpand = useCallback(
    async (expanded: boolean, record: GroupedThreatResponse) => {
      if (!expanded) {
        setExpandedMacs((prev) => prev.filter((m) => m !== record.attackMac));
        return;
      }
      setExpandedMacs((prev) =>
        prev.includes(record.attackMac) ? prev : [...prev, record.attackMac],
      );
      if (subRows[record.attackMac]) return;
      try {
        const result = await threatService.getThreatList({
          customer_id: record.customerId,
          attack_mac: record.attackMac,
          page: 0,
          size: 100,
        });
        setSubRows((prev) => ({ ...prev, [record.attackMac]: result.content }));
      } catch {
        message.error(t("threatList.messageLoadFailed"));
      }
    },
    [subRows, t],
  );

  // ─────────────────────────────────────────────────────────────
  // 表格列定义
  // ─────────────────────────────────────────────────────────────
  const columns: TableColumnsType<ThreatAssessment> = useMemo(
    () => [
      {
        title: "ID",
        dataIndex: "id",
        key: "id",
        width: 80,
        align: "center",
        responsive: ["xxl"],
      },
      {
        title: t("threatList.assessmentTime"),
        dataIndex: "assessmentTime",
        key: "assessmentTime",
        width: 180,
        align: "center",
        render: (time: string) => formatDateTime(time),
      },
      {
        title: t("threatList.attackMac"),
        dataIndex: "attackMac",
        key: "attackMac",
        width: 192,
        align: "center",
        render: (mac: string) => <NetworkIdentifier value={mac} kind="mac" />,
      },
      {
        title: t("threatList.attackIp"),
        dataIndex: "attackIp",
        key: "attackIp",
        width: 152,
        align: "center",
        responsive: ["xl"],
        render: (ip?: string) => <NetworkIdentifier value={ip} kind="ip" />,
      },
      {
        title: t("threatList.threatLevel"),
        dataIndex: "threatLevel",
        key: "threatLevel",
        width: 108,
        align: "center",
        render: (level: ThreatLevel) => renderThreatLevelTag(level),
      },
      {
        title: t("threatList.detectionWindow"),
        dataIndex: "detectionTier",
        key: "detectionTier",
        width: 156,
        align: "center",
        render: (tier?: number) => (
          <Tooltip title={t("overview.detectionWindowHelp")}>
            <Tag
              color={
                tier === 1
                  ? "cyan"
                  : tier === 2
                    ? "blue"
                    : tier === 3
                      ? "purple"
                      : "default"
              }
            >
              {detectionWindowLabel(tier)}
            </Tag>
          </Tooltip>
        ),
      },
      {
        title: t("threatList.threatScore"),
        dataIndex: "threatScore",
        key: "threatScore",
        width: 104,
        align: "center",
        render: (score: number) => formatNumber(score),
      },
      {
        title: t("threatList.attackCount"),
        dataIndex: "attackCount",
        key: "attackCount",
        width: 104,
        align: "center",
      },
      {
        title: t("threatList.uniqueIps"),
        dataIndex: "uniqueIps",
        key: "uniqueIps",
        width: 92,
        align: "center",
        responsive: ["xl"],
      },
      {
        title: t("threatList.uniquePorts"),
        dataIndex: "uniquePorts",
        key: "uniquePorts",
        width: 104,
        align: "center",
        responsive: ["xl"],
      },
      {
        title: t("threatList.uniqueDevices"),
        dataIndex: "uniqueDevices",
        key: "uniqueDevices",
        width: 120,
        align: "center",
        responsive: ["xxl"],
      },
      {
        title: t("common.actions"),
        key: "action",
        width: 120,
        fixed: "right",
        align: "center",
        render: (_, record) => (
          <PermissionGate requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}>
            <Button
              type="link"
              danger
              aria-label={t("threatList.deleteThreatRecordAria", {
                id: record.id,
              })}
              icon={<DeleteOutlined />}
              onClick={(event) => {
                event.stopPropagation();
                handleDelete(record.id);
              }}
            >
              {t("common.delete")}
            </Button>
          </PermissionGate>
        ),
      },
    ],
    [detectionWindowLabel, handleDelete, renderThreatLevelTag, t],
  );

  // Scroll budget of the columns visible at the current breakpoint
  // (shared by the detail table and the grouped view's nested table).
  const threatScrollX = useResponsiveScrollX(columns);

  const groupedColumns: TableColumnsType<GroupedThreatResponse> = useMemo(
    () => [
      {
        title: t("threatList.attackMac"),
        dataIndex: "attackMac",
        width: 192,
        align: "center",
        render: (mac: string) => <NetworkIdentifier value={mac} kind="mac" />,
      },
      {
        title: t("threatList.threatLevel"),
        dataIndex: "maxThreatLevel",
        width: 112,
        align: "center",
        render: (level: string) => {
          const colorMap: Record<string, string> = {
            CRITICAL: "red",
            HIGH: "orange",
            MEDIUM: "gold",
            LOW: "blue",
            INFO: "default",
          };
          return <Tag color={colorMap[level] || "default"}>{level}</Tag>;
        },
      },
      {
        title: t("threatList.threatScore"),
        dataIndex: "maxThreatScore",
        width: 120,
        align: "center",
        render: (score: number) => score?.toFixed(2),
        sorter: (a: GroupedThreatResponse, b: GroupedThreatResponse) =>
          a.maxThreatScore - b.maxThreatScore,
      },
      {
        title: t("threatList.groupedAssessmentCount"),
        dataIndex: "assessmentCount",
        width: 112,
        align: "center",
      },
      {
        title: t("threatList.groupedTotalAttacks"),
        dataIndex: "totalAttackCount",
        width: 132,
        align: "center",
        render: (count: number) => count?.toLocaleString(),
      },
      {
        title: t("threatList.groupedTierCount"),
        dataIndex: "tierCount",
        width: 96,
        align: "center",
      },
      {
        title: t("threatList.assessmentTime"),
        dataIndex: "latestAssessmentTime",
        width: 180,
        align: "center",
        render: (time: string) => formatDateTime(time),
      },
    ],
    [t],
  );

  const rowSelection = useMemo<TableProps<ThreatAssessment>["rowSelection"]>(
    () => ({
      selectedRowKeys,
      columnWidth: 48,
      onChange: (keys: React.Key[]) => setSelectedRowKeys(keys),
    }),
    [selectedRowKeys],
  );

  return (
    <div className="tds-investigate-page tds-threat-list-page">
      <div className="tds-investigate-view-switch">
        <Segmented
          aria-label={t("threatList.title")}
          value={viewMode}
          onChange={(val) => setViewMode(val as "detail" | "grouped")}
          options={[
            {
              label: t("threatList.detailView"),
              value: "detail",
              icon: <UnorderedListOutlined />,
            },
            {
              label: t("threatList.groupedView"),
              value: "grouped",
              icon: <AppstoreOutlined />,
            },
          ]}
        />
      </div>

      {initialized && !effectiveCustomerId && (
        <Alert
          type="info"
          showIcon
          message={t("threatList.customerRequired")}
        />
      )}

      {/* ────────────────────────── 顶部过滤与导出栏 ────────────────────────── */}
      <Card
        className="tds-investigate-panel tds-threat-list-panel"
        variant="borderless"
        title={t("threatList.title")}
      >
        <Space direction="vertical" size="middle" style={{ width: "100%" }}>
          <Row className="tds-investigate-filters" gutter={[16, 16]} style={{ width: "100%" }}>
            <Col xs={24} sm={12} lg={viewMode === "detail" ? 4 : 5}>
              <Select<ThreatLevelFilterValue>
                aria-label={t("threatList.threatLevel")}
                style={{ width: "100%" }}
                value={threatLevelFilter}
                options={threatLevelOptions}
                onChange={(value) => setThreatLevelFilter(value)}
                placeholder={t("threatList.threatLevel")}
              />
            </Col>

            {viewMode === "detail" && (
              <Col xs={24} sm={12} lg={4}>
                <Select<DetectionTierFilterValue>
                  aria-label={t("threatList.detectionWindow")}
                  style={{ width: "100%" }}
                  value={detectionTierFilter}
                  options={[
                    { label: t("common.all"), value: "ALL" },
                    { label: t("threatList.window30s"), value: 1 },
                    { label: t("threatList.window5m"), value: 2 },
                    { label: t("threatList.window15m"), value: 3 },
                  ]}
                  onChange={(value) => setDetectionTierFilter(value)}
                  placeholder={t("threatList.detectionWindow")}
                />
              </Col>
            )}

            <Col xs={24} sm={12} lg={viewMode === "detail" ? 5 : 6}>
              <RangePicker
                aria-label={`${t("threatList.startDate")} - ${t("threatList.endDate")}`}
                style={{ width: "100%" }}
                value={timeRangeFilter}
                onChange={(value) => setTimeRangeFilter(value)}
                placeholder={[
                  t("threatList.startDate"),
                  t("threatList.endDate"),
                ]}
                allowClear
              />
            </Col>

            <Col xs={24} sm={12} lg={viewMode === "detail" ? 5 : 7}>
              <Search
                aria-label={t("threatList.searchAttackMac")}
                style={{ width: "100%" }}
                value={attackMacInput}
                onChange={(event) => setAttackMacInput(event.target.value)}
                onSearch={(value) => {
                  setAttackMacInput(value);
                  setDebouncedAttackMac(value.trim());
                }}
                placeholder={t("threatList.searchAttackMac")}
                allowClear
              />
            </Col>

            <Col xs={12} sm={6} lg={3}>
              <Button style={{ width: "100%" }} onClick={resetFilters}>
                {t("threatList.resetFilters")}
              </Button>
            </Col>

            <Col xs={12} sm={6} lg={3}>
              <Button
                style={{ width: "100%" }}
                aria-label={t("threatList.refreshAria")}
                icon={<ReloadOutlined />}
                onClick={() => {
                  if (viewMode === "grouped") {
                    void fetchGroupedData();
                    return;
                  }
                  void loadThreats();
                }}
              >
                {t("common.refresh")}
              </Button>
            </Col>
          </Row>

          <Divider style={{ margin: "4px 0" }} />

          <Space
            className="tds-investigate-toolbar"
            wrap
            style={{
              width: "100%",
              justifyContent: "space-between",
            }}
          >
            <Space>
              <Button icon={<DownloadOutlined />} onClick={exportCsv}>
                {t("threatList.exportCsv")}
              </Button>
              <Button icon={<DownloadOutlined />} onClick={exportJson}>
                {t("threatList.exportJson")}
              </Button>
            </Space>

            <Text type="secondary">
              {t("common.totalItems", {
                count: viewMode === "grouped" ? groupedTotal : total,
              })}
            </Text>
          </Space>

          {selectedRowKeys.length > 0 && (
            <Card
              className="tds-investigate-selection"
              size="small"
            >
              <Space style={{ width: "100%", justifyContent: "space-between" }}>
                <Text>
                  {t("threatList.selected")}{" "}
                  <Text strong>{selectedRowKeys.length}</Text>{" "}
                  {t("threatList.items")}
                </Text>
                <Space>
                  <PermissionGate
                    requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}
                  >
                    <Button
                      danger
                      aria-label={t("threatList.batchDeleteAria")}
                      icon={<DeleteOutlined />}
                      onClick={handleBatchDelete}
                    >
                      {t("threatList.batchDelete")}
                    </Button>
                  </PermissionGate>
                  <Button onClick={() => setSelectedRowKeys([])}>
                    {t("threatList.clearSelection")}
                  </Button>
                </Space>
              </Space>
            </Card>
          )}

          {viewMode === "detail" && (
            <Table<ThreatAssessment>
              className="tds-threat-list-table"
              columns={columns}
              dataSource={threats}
              rowKey="id"
              loading={loading}
              rowSelection={rowSelection}
              tableLayout="fixed"
              scroll={{ x: threatScrollX }}
              onRow={(record) => ({
                onClick: () => openDetailDrawer(record.id),
                onKeyDown: (event) => {
                  if (event.key !== "Enter" && event.key !== " ") return;
                  event.preventDefault();
                  openDetailDrawer(record.id);
                },
                tabIndex: 0,
                role: "button",
                "aria-label": `${t("threatList.threatDetail")} #${record.id}`,
                style: { cursor: "pointer" },
              })}
              pagination={{
                current: page,
                pageSize,
                total,
                showSizeChanger: true,
                showQuickJumper: true,
                showTotal: (all) => t("common.totalItems", { count: all }),
                onChange: (newPage, newPageSize) => {
                  setPage(newPage);
                  setPageSize(newPageSize);
                },
              }}
            />
          )}

          {viewMode === "grouped" && (
            <Table<GroupedThreatResponse>
              className="tds-threat-list-table"
              rowKey="attackMac"
              columns={groupedColumns}
              tableLayout="fixed"
              dataSource={groupedData}
              loading={groupedLoading}
              scroll={{ x: 944 }}
              pagination={{
                total: groupedTotal,
                defaultPageSize: DEFAULT_PAGE_SIZE,
                showSizeChanger: true,
                showTotal: (all) => `${all} ${t("threatList.items")}`,
                onChange: (p, ps) => {
                  void fetchGroupedData(p, ps);
                },
              }}
              expandable={{
                columnWidth: 48,
                expandedRowKeys: expandedMacs,
                onExpand: (expanded, record) => {
                  void handleExpand(expanded, record);
                },
                expandedRowRender: (record) => {
                  const rows = subRows[record.attackMac];
                  if (!rows) return <Spin size="small" />;
                  return (
                    <Table<ThreatAssessment>
                      className="tds-threat-list-table tds-threat-list-table--nested"
                      rowKey="id"
                      columns={columns}
                      dataSource={rows}
                      pagination={false}
                      size="small"
                      tableLayout="fixed"
                      scroll={{ x: threatScrollX }}
                    />
                  );
                },
              }}
            />
          )}
        </Space>
      </Card>

      {/* ────────────────────────── 详情抽屉 ────────────────────────── */}
      <Drawer
        title={
          <Space>
            <EyeOutlined />
            <span>{t("threatList.threatDetail")}</span>
            {activeThreatId ? (
              <Text type="secondary">#{activeThreatId}</Text>
            ) : null}
          </Space>
        }
        placement="right"
        width={screens.md ? 600 : "100%"}
        open={drawerOpen}
        onClose={closeDetailDrawer}
        destroyOnHidden
      >
        {drawerLoading && (
          <div style={{ textAlign: "center", padding: 48 }}>
            <Spin tip={t("threatList.loadingDetail")} />
          </div>
        )}

        {!drawerLoading && !activeThreatDetail && (
          <Text type="secondary">{t("threatList.noDetailData")}</Text>
        )}

        {!drawerLoading && activeThreatDetail && (
          <Space direction="vertical" size="middle" style={{ width: "100%" }}>
            <Descriptions
              className="tds-detail-descriptions"
              bordered
              column={1}
              size="small"
              labelStyle={{ width: 150 }}
            >
              <Descriptions.Item label={t("common.id")}>
                {activeThreatDetail.id}
              </Descriptions.Item>
              <Descriptions.Item label={t("common.customerId")}>
                {activeThreatDetail.customerId || "-"}
              </Descriptions.Item>
              <Descriptions.Item label={t("threatList.attackMac")}>
                <NetworkIdentifier
                  value={activeThreatDetail.attackMac}
                  kind="mac"
                />
              </Descriptions.Item>
              <Descriptions.Item label={t("threatList.attackIp")}>
                <NetworkIdentifier
                  value={activeThreatDetail.attackIp}
                  kind="ip"
                />
              </Descriptions.Item>
              <Descriptions.Item label={t("threatList.threatLevel")}>
                {renderThreatLevelTag(activeThreatDetail.threatLevel)}
              </Descriptions.Item>
              <Descriptions.Item label={t("threatList.threatScore")}>
                {formatNumber(activeThreatDetail.threatScore)}
              </Descriptions.Item>
              <Descriptions.Item label={t("threatList.attackCount")}>
                {activeThreatDetail.attackCount}
              </Descriptions.Item>
              <Descriptions.Item label={t("threatList.uniqueIps")}>
                {activeThreatDetail.uniqueIps}
              </Descriptions.Item>
              <Descriptions.Item label={t("threatList.uniquePorts")}>
                {activeThreatDetail.uniquePorts}
              </Descriptions.Item>
              <Descriptions.Item label={t("threatList.uniqueDevices")}>
                {activeThreatDetail.uniqueDevices}
              </Descriptions.Item>
              <Descriptions.Item label={t("threatList.portList")}>
                {renderPortTags(activeThreatDetail.portList)}
              </Descriptions.Item>
              <Descriptions.Item label={t("threatList.portRiskScore")}>
                {typeof activeThreatDetail.portRiskScore === "number"
                  ? activeThreatDetail.portRiskScore.toFixed(2)
                  : "-"}
              </Descriptions.Item>
              <Descriptions.Item label={t("threatList.detectionTier")}>
                {typeof activeThreatDetail.detectionTier === "number"
                  ? `Tier ${activeThreatDetail.detectionTier}`
                  : "-"}
              </Descriptions.Item>
              <Descriptions.Item label={t("threatList.assessmentTime")}>
                {formatDateTime(activeThreatDetail.assessmentTime)}
              </Descriptions.Item>
              <Descriptions.Item label={t("common.createdAt")}>
                {formatDateTime(activeThreatDetail.createdAt)}
              </Descriptions.Item>
              <Descriptions.Item
                label={t("threatList.mitigationRecommendations")}
              >
                {renderMitigationList(
                  activeThreatDetail.mitigationRecommendations,
                )}
              </Descriptions.Item>
            </Descriptions>

            {isDevMode && activeThreatDetail?.scoreBreakdown && (
              <Collapse
                size="small"
                style={{ marginTop: 8 }}
                items={[
                  {
                    key: "score-breakdown",
                    label: (
                      <Space>
                        <Tag color="purple">{t("threatList.devMode")}</Tag>
                        <Text strong>
                          {t("threatList.scoreBreakdownTitle")}
                        </Text>
                      </Space>
                    ),
                    children: (
                      <Descriptions
                        className="tds-detail-descriptions"
                        bordered
                        column={1}
                        size="small"
                        labelStyle={{ width: 220 }}
                      >
                        <Descriptions.Item
                          label={t("threatList.breakdownFormula")}
                        >
                          <Text code>
                            {activeThreatDetail.scoreBreakdown.formula}
                          </Text>
                        </Descriptions.Item>
                        <Descriptions.Item
                          label={t("threatList.breakdownBaseScore")}
                        >
                          <Text strong>
                            {activeThreatDetail.scoreBreakdown.baseScore.toLocaleString(
                              undefined,
                              {
                                minimumFractionDigits: 2,
                                maximumFractionDigits: 2,
                              },
                            )}
                          </Text>
                          <Text type="secondary" style={{ marginLeft: 8 }}>
                            ({activeThreatDetail.attackCount} ×{" "}
                            {activeThreatDetail.uniqueIps} ×{" "}
                            {activeThreatDetail.uniquePorts})
                          </Text>
                        </Descriptions.Item>
                        <Descriptions.Item
                          label={`× ${t("threatList.breakdownTimeWeight")}`}
                        >
                          {activeThreatDetail.scoreBreakdown.timeWeight.toFixed(
                            2,
                          )}
                          {activeThreatDetail.scoreBreakdown.timeWeightNote && (
                            <Tag style={{ marginLeft: 8 }}>
                              {activeThreatDetail.scoreBreakdown.timeWeightNote}
                            </Tag>
                          )}
                        </Descriptions.Item>
                        <Descriptions.Item
                          label={`× ${t("threatList.breakdownIpWeight")}`}
                        >
                          {activeThreatDetail.scoreBreakdown.ipWeight.toFixed(
                            2,
                          )}
                          <Text type="secondary" style={{ marginLeft: 8 }}>
                            [{activeThreatDetail.uniqueIps} IPs]
                          </Text>
                        </Descriptions.Item>
                        <Descriptions.Item
                          label={`× ${t("threatList.breakdownPortWeight")}`}
                        >
                          {activeThreatDetail.scoreBreakdown.portWeight.toFixed(
                            2,
                          )}
                          {activeThreatDetail.scoreBreakdown.portWeightNote && (
                            <Tag style={{ marginLeft: 8 }}>
                              {activeThreatDetail.scoreBreakdown.portWeightNote}
                            </Tag>
                          )}
                        </Descriptions.Item>
                        <Descriptions.Item
                          label={`× ${t("threatList.breakdownDeviceWeight")}`}
                        >
                          {activeThreatDetail.scoreBreakdown.deviceWeight.toFixed(
                            2,
                          )}
                          <Text type="secondary" style={{ marginLeft: 8 }}>
                            [{activeThreatDetail.uniqueDevices}{" "}
                            {t("threatList.uniqueDevices").toLowerCase()}]
                          </Text>
                        </Descriptions.Item>
                        <Descriptions.Item
                          label={`× ${t("threatList.breakdownAttackSourceWeight")}`}
                        >
                          {activeThreatDetail.scoreBreakdown.attackSourceWeight.toFixed(
                            2,
                          )}
                        </Descriptions.Item>
                        <Descriptions.Item
                          label={`× ${t("threatList.breakdownHoneypotWeight")}`}
                        >
                          {activeThreatDetail.scoreBreakdown.honeypotSensitivityWeight.toFixed(
                            2,
                          )}
                        </Descriptions.Item>
                        <Descriptions.Item
                          label={`= ${t("threatList.breakdownCombinedSegmentWeight")}`}
                        >
                          <Text strong>
                            {activeThreatDetail.scoreBreakdown.combinedSegmentWeight.toFixed(
                              2,
                            )}
                          </Text>
                        </Descriptions.Item>
                        <Descriptions.Item
                          label={`× ${t("threatList.breakdownAttackRateWeight")}`}
                        >
                          {activeThreatDetail.scoreBreakdown.attackRateWeight.toFixed(
                            2,
                          )}
                          <Text type="secondary" style={{ marginLeft: 8 }}>
                            [
                            {activeThreatDetail.scoreBreakdown.attackRate.toFixed(
                              2,
                            )}
                            /s]
                          </Text>
                        </Descriptions.Item>
                        <Descriptions.Item
                          label={`× ${t("threatList.breakdownMlWeight")}`}
                        >
                          {activeThreatDetail.scoreBreakdown.mlWeight.toFixed(
                            2,
                          )}
                          <Tag
                            icon={
                              activeThreatDetail.scoreBreakdown.mlEnabled ? (
                                <CheckCircleOutlined />
                              ) : (
                                <CloseCircleOutlined />
                              )
                            }
                            color={
                              activeThreatDetail.scoreBreakdown.mlEnabled
                                ? "green"
                                : "default"
                            }
                            style={{ marginLeft: 8 }}
                          >
                            ML
                          </Tag>
                        </Descriptions.Item>
                        <Descriptions.Item
                          label={t("threatList.breakdownRawScore")}
                        >
                          <Text strong>
                            {activeThreatDetail.scoreBreakdown.rawScore.toLocaleString(
                              undefined,
                              {
                                minimumFractionDigits: 2,
                                maximumFractionDigits: 2,
                              },
                            )}
                          </Text>
                        </Descriptions.Item>
                        <Descriptions.Item
                          label={t("threatList.breakdownNormalizedScore")}
                        >
                          <Text strong style={{ fontSize: 16 }}>
                            {activeThreatDetail.scoreBreakdown.normalizedScore.toFixed(
                              2,
                            )}
                          </Text>
                        </Descriptions.Item>
                      </Descriptions>
                    ),
                  },
                ]}
              />
            )}

            <Space style={{ width: "100%", justifyContent: "flex-end" }}>
              <PermissionGate requiredRoles={["SUPER_ADMIN", "TENANT_ADMIN"]}>
                <Button
                  danger
                  aria-label={t("threatList.deleteThreatRecordAria", {
                    id: activeThreatDetail.id,
                  })}
                  icon={<DeleteOutlined />}
                  onClick={() => handleDelete(activeThreatDetail.id)}
                >
                  {t("threatList.deleteThisRecord")}
                </Button>
              </PermissionGate>
            </Space>
          </Space>
        )}
      </Drawer>
    </div>
  );
};

export default ThreatList;
