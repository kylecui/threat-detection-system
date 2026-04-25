import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  DatePicker,
  Descriptions,
  Divider,
  Drawer,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import {
  DeleteOutlined,
  DownloadOutlined,
  ExclamationCircleOutlined,
  EyeOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { TableColumnsType, TableProps } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import threatService from '@/services/threat';
import type { ThreatAssessment, ThreatQueryFilter } from '@/types';
import { ThreatLevel } from '@/types';
import { getCustomerId } from '@/services/api';

const { Search } = Input;
const { RangePicker } = DatePicker;
const { Text } = Typography;

type ThreatLevelFilterValue = ThreatLevel | 'ALL';

type TimeRangeValue = [Dayjs | null, Dayjs | null] | null;

const DEFAULT_PAGE = 1;
const DEFAULT_PAGE_SIZE = 20;
const SEARCH_DEBOUNCE_MS = 450;

const THREAT_LEVEL_OPTIONS: Array<{ label: string; value: ThreatLevelFilterValue }> = [
  { label: '全部', value: 'ALL' },
  { label: ThreatLevel.CRITICAL, value: ThreatLevel.CRITICAL },
  { label: ThreatLevel.HIGH, value: ThreatLevel.HIGH },
  { label: ThreatLevel.MEDIUM, value: ThreatLevel.MEDIUM },
  { label: ThreatLevel.LOW, value: ThreatLevel.LOW },
  { label: ThreatLevel.INFO, value: ThreatLevel.INFO },
];

const THREAT_LEVEL_COLOR_MAP: Record<ThreatLevel, string> = {
  [ThreatLevel.CRITICAL]: 'red',
  [ThreatLevel.HIGH]: 'orange',
  [ThreatLevel.MEDIUM]: 'gold',
  [ThreatLevel.LOW]: 'blue',
  [ThreatLevel.INFO]: 'default',
};

function formatDateTime(value?: string): string {
  if (!value) return '-';
  const date = dayjs(value);
  return date.isValid() ? date.format('YYYY-MM-DD HH:mm:ss') : '-';
}

function formatNumber(value?: number): string {
  if (typeof value !== 'number' || Number.isNaN(value)) return 'N/A';
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

function triggerBlobDownload(content: string, fileName: string, mimeType: string) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
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
  // ─────────────────────────────────────────────────────────────
  // 基础列表状态
  // ─────────────────────────────────────────────────────────────
  const [loading, setLoading] = useState(false);
  const [threats, setThreats] = useState<ThreatAssessment[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(DEFAULT_PAGE);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  // ─────────────────────────────────────────────────────────────
  // 筛选状态
  // ─────────────────────────────────────────────────────────────
  const [threatLevelFilter, setThreatLevelFilter] = useState<ThreatLevelFilterValue>('ALL');
  const [timeRangeFilter, setTimeRangeFilter] = useState<TimeRangeValue>(null);
  const [attackMacInput, setAttackMacInput] = useState('');
  const [debouncedAttackMac, setDebouncedAttackMac] = useState('');

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
  const [activeThreatDetail, setActiveThreatDetail] = useState<ThreatAssessment | null>(null);

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
    const customerId = getCustomerId();

    const filter: ThreatQueryFilter = {
      customer_id: customerId,
      page: page - 1,
      page_size: pageSize,
      sort_by: 'assessmentTime',
      sort_order: 'desc',
    };

    if (threatLevelFilter !== 'ALL') {
      filter.threat_level = threatLevelFilter;
    }

    if (
      timeRangeFilter
      && timeRangeFilter.length === 2
      && timeRangeFilter[0]
      && timeRangeFilter[1]
    ) {
      filter.start_time = timeRangeFilter[0].startOf('day').toISOString();
      filter.end_time = timeRangeFilter[1].endOf('day').toISOString();
    }

    if (debouncedAttackMac) {
      filter.attack_mac = debouncedAttackMac;
    }

    return filter;
  }, [page, pageSize, threatLevelFilter, timeRangeFilter, debouncedAttackMac]);

  // ─────────────────────────────────────────────────────────────
  // 数据加载
  // ─────────────────────────────────────────────────────────────
  const loadThreats = useCallback(async () => {
    try {
      setLoading(true);
      const response = await threatService.getThreatList(queryFilter);
      setThreats(response.content || []);
      setTotal(response.totalElements || 0);
    } catch {
      message.error('加载威胁列表失败');
    } finally {
      setLoading(false);
    }
  }, [queryFilter]);

  useEffect(() => {
    loadThreats();
  }, [loadThreats]);

  // 筛选变更时回到第一页
  useEffect(() => {
    setPage(DEFAULT_PAGE);
  }, [threatLevelFilter, timeRangeFilter, debouncedAttackMac]);

  // ─────────────────────────────────────────────────────────────
  // 详情抽屉逻辑
  // ─────────────────────────────────────────────────────────────
  const loadThreatDetail = useCallback(async (id: number) => {
    try {
      setDrawerLoading(true);
      const detail = await threatService.getThreatDetail(id);
      setActiveThreatDetail(detail);
    } catch {
      message.error('加载威胁详情失败');
      setActiveThreatDetail(null);
    } finally {
      setDrawerLoading(false);
    }
  }, []);

  const openDetailDrawer = useCallback((id: number) => {
    setActiveThreatId(id);
    setDrawerOpen(true);
    void loadThreatDetail(id);
  }, [loadThreatDetail]);

  const closeDetailDrawer = useCallback(() => {
    setDrawerOpen(false);
    setActiveThreatId(null);
    setActiveThreatDetail(null);
  }, []);

  // ─────────────────────────────────────────────────────────────
  // 单条删除与批量删除
  // ─────────────────────────────────────────────────────────────
  const handleDelete = useCallback((id: number) => {
    Modal.confirm({
      title: '确认删除',
      icon: <ExclamationCircleOutlined />,
      content: `确定要删除威胁记录 #${id} 吗？`,
      okText: '删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await threatService.deleteThreat(id);
          message.success('删除成功');

          if (activeThreatId === id) {
            closeDetailDrawer();
          }

          setSelectedRowKeys((prev) => prev.filter((key) => key !== id));
          await loadThreats();
        } catch {
          message.error('删除失败');
        }
      },
    });
  }, [activeThreatId, closeDetailDrawer, loadThreats]);

  const handleBatchDelete = useCallback(() => {
    const ids = selectedRowKeys
      .map((key) => Number(key))
      .filter((id) => Number.isFinite(id));

    if (ids.length === 0) {
      message.warning('请先选择要删除的记录');
      return;
    }

    Modal.confirm({
      title: '确认批量删除',
      icon: <ExclamationCircleOutlined />,
      content: `确定要删除已选择的 ${ids.length} 条记录吗？`,
      okText: '批量删除',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await threatService.batchDeleteThreats(ids);
          message.success(`已删除 ${ids.length} 条记录`);
          setSelectedRowKeys([]);

          if (activeThreatId && ids.includes(activeThreatId)) {
            closeDetailDrawer();
          }

          await loadThreats();
        } catch {
          message.error('批量删除失败');
        }
      },
    });
  }, [selectedRowKeys, activeThreatId, closeDetailDrawer, loadThreats]);

  // ─────────────────────────────────────────────────────────────
  // 导出逻辑（当前页）
  // ─────────────────────────────────────────────────────────────
  const exportCsv = useCallback(() => {
    if (threats.length === 0) {
      message.warning('当前页无可导出数据');
      return;
    }

    const csv = threatService.exportThreatsToCsv(threats);
    const fileName = `threat-list-${dayjs().format('YYYYMMDD-HHmmss')}.csv`;
    triggerBlobDownload(csv, fileName, 'text/csv;charset=utf-8;');
    message.success('CSV 导出成功');
  }, [threats]);

  const exportJson = useCallback(() => {
    if (threats.length === 0) {
      message.warning('当前页无可导出数据');
      return;
    }

    const json = threatService.exportThreatsToJson(threats);
    const fileName = `threat-list-${dayjs().format('YYYYMMDD-HHmmss')}.json`;
    triggerBlobDownload(json, fileName, 'application/json;charset=utf-8;');
    message.success('JSON 导出成功');
  }, [threats]);

  // ─────────────────────────────────────────────────────────────
  // 筛选重置
  // ─────────────────────────────────────────────────────────────
  const resetFilters = useCallback(() => {
    setThreatLevelFilter('ALL');
    setTimeRangeFilter(null);
    setAttackMacInput('');
    setDebouncedAttackMac('');
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

  const renderMitigationList = useCallback((recommendations?: string[]) => {
    const list = normalizeMitigationRecommendations(recommendations);
    if (list.length === 0) {
      return <Text type="secondary">暂无建议</Text>;
    }

    return (
      <ul style={{ margin: 0, paddingInlineStart: 18 }}>
        {list.map((item, index) => (
          <li key={`${item}-${index}`}>{item}</li>
        ))}
      </ul>
    );
  }, []);

  // ─────────────────────────────────────────────────────────────
  // 表格列定义
  // ─────────────────────────────────────────────────────────────
  const columns: TableColumnsType<ThreatAssessment> = useMemo(
    () => [
      {
        title: 'ID',
        dataIndex: 'id',
        key: 'id',
        width: 80,
      },
      {
        title: '评估时间',
        dataIndex: 'assessmentTime',
        key: 'assessmentTime',
        width: 180,
        render: (time: string) => formatDateTime(time),
      },
      {
        title: '攻击者MAC',
        dataIndex: 'attackMac',
        key: 'attackMac',
        width: 180,
        render: (mac: string) => <code>{mac}</code>,
      },
      {
        title: '攻击者IP',
        dataIndex: 'attackIp',
        key: 'attackIp',
        width: 150,
        render: (ip?: string) => ip || '-',
      },
      {
        title: '威胁等级',
        dataIndex: 'threatLevel',
        key: 'threatLevel',
        width: 120,
        render: (level: ThreatLevel) => renderThreatLevelTag(level),
      },
      {
        title: '威胁分数',
        dataIndex: 'threatScore',
        key: 'threatScore',
        width: 110,
        render: (score: number) => formatNumber(score),
      },
      {
        title: '攻击次数',
        dataIndex: 'attackCount',
        key: 'attackCount',
        width: 100,
      },
      {
        title: '诱饵IP数',
        dataIndex: 'uniqueIps',
        key: 'uniqueIps',
        width: 100,
      },
      {
        title: '端口种类',
        dataIndex: 'uniquePorts',
        key: 'uniquePorts',
        width: 100,
      },
      {
        title: '设备数',
        dataIndex: 'uniqueDevices',
        key: 'uniqueDevices',
        width: 80,
      },
      {
        title: '操作',
        key: 'action',
        fixed: 'right',
        width: 120,
        render: (_, record) => (
          <Button
            type="link"
            danger
            icon={<DeleteOutlined />}
            onClick={(event) => {
              event.stopPropagation();
              handleDelete(record.id);
            }}
          >
            删除
          </Button>
        ),
      },
    ],
    [handleDelete, renderThreatLevelTag],
  );

  const rowSelection = useMemo<TableProps<ThreatAssessment>['rowSelection']>(
    () => ({
      selectedRowKeys,
      onChange: (keys: React.Key[]) => setSelectedRowKeys(keys),
    }),
    [selectedRowKeys],
  );

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {/* ────────────────────────── 顶部过滤与导出栏 ────────────────────────── */}
      <Card bordered={false} title="威胁列表">
        <Space
          direction="vertical"
          size="middle"
          style={{ width: '100%' }}
        >
          <Space wrap size="middle" style={{ width: '100%' }}>
            <Select<ThreatLevelFilterValue>
              style={{ width: 160 }}
              value={threatLevelFilter}
              options={THREAT_LEVEL_OPTIONS}
              onChange={(value) => setThreatLevelFilter(value)}
              placeholder="威胁等级"
            />

            <RangePicker
              value={timeRangeFilter}
              onChange={(value) => setTimeRangeFilter(value)}
              placeholder={['开始日期', '结束日期']}
              allowClear
            />

            <Search
              style={{ width: 260 }}
              value={attackMacInput}
              onChange={(event) => setAttackMacInput(event.target.value)}
              onSearch={(value) => {
                setAttackMacInput(value);
                setDebouncedAttackMac(value.trim());
              }}
              placeholder="搜索攻击者MAC"
              allowClear
            />

            <Button onClick={resetFilters}>重置筛选</Button>

            <Button icon={<ReloadOutlined />} onClick={loadThreats}>
              刷新
            </Button>
          </Space>

          <Divider style={{ margin: '4px 0' }} />

          <Space
            wrap
            style={{
              width: '100%',
              justifyContent: 'space-between',
            }}
          >
            <Space>
              <Button icon={<DownloadOutlined />} onClick={exportCsv}>
                导出 CSV
              </Button>
              <Button icon={<DownloadOutlined />} onClick={exportJson}>
                导出 JSON
              </Button>
            </Space>

            <Text type="secondary">共 {total} 条</Text>
          </Space>

          {selectedRowKeys.length > 0 && (
            <Card
              size="small"
              style={{ background: '#fafafa', border: '1px solid #f0f0f0' }}
            >
              <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                <Text>
                  已选 <Text strong>{selectedRowKeys.length}</Text> 项
                </Text>
                <Space>
                  <Button
                    danger
                    icon={<DeleteOutlined />}
                    onClick={handleBatchDelete}
                  >
                    批量删除
                  </Button>
                  <Button onClick={() => setSelectedRowKeys([])}>清空选择</Button>
                </Space>
              </Space>
            </Card>
          )}

          <Table<ThreatAssessment>
            columns={columns}
            dataSource={threats}
            rowKey="id"
            loading={loading}
            rowSelection={rowSelection}
            scroll={{ x: 1350 }}
            onRow={(record) => ({
              onClick: () => openDetailDrawer(record.id),
              style: { cursor: 'pointer' },
            })}
            pagination={{
              current: page,
              pageSize,
              total,
              showSizeChanger: true,
              showQuickJumper: true,
              showTotal: (all) => `共 ${all} 条`,
              onChange: (newPage, newPageSize) => {
                setPage(newPage);
                setPageSize(newPageSize);
              },
            }}
          />
        </Space>
      </Card>

      {/* ────────────────────────── 详情抽屉 ────────────────────────── */}
      <Drawer
        title={
          <Space>
            <EyeOutlined />
            <span>威胁详情</span>
            {activeThreatId ? <Text type="secondary">#{activeThreatId}</Text> : null}
          </Space>
        }
        placement="right"
        width={600}
        open={drawerOpen}
        onClose={closeDetailDrawer}
        destroyOnClose
      >
        {drawerLoading && (
          <div style={{ textAlign: 'center', padding: 48 }}>
            <Spin tip="加载详情中..." />
          </div>
        )}

        {!drawerLoading && !activeThreatDetail && (
          <Text type="secondary">暂无详情数据</Text>
        )}

        {!drawerLoading && activeThreatDetail && (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions
              bordered
              column={1}
              size="small"
              labelStyle={{ width: 150 }}
            >
              <Descriptions.Item label="ID">{activeThreatDetail.id}</Descriptions.Item>
              <Descriptions.Item label="客户ID">
                {activeThreatDetail.customerId || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="攻击者MAC">
                <code>{activeThreatDetail.attackMac}</code>
              </Descriptions.Item>
              <Descriptions.Item label="攻击者IP">
                {activeThreatDetail.attackIp || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="威胁等级">
                {renderThreatLevelTag(activeThreatDetail.threatLevel)}
              </Descriptions.Item>
              <Descriptions.Item label="威胁分数">
                {formatNumber(activeThreatDetail.threatScore)}
              </Descriptions.Item>
              <Descriptions.Item label="攻击次数">
                {activeThreatDetail.attackCount}
              </Descriptions.Item>
              <Descriptions.Item label="诱饵IP数">
                {activeThreatDetail.uniqueIps}
              </Descriptions.Item>
              <Descriptions.Item label="端口种类">
                {activeThreatDetail.uniquePorts}
              </Descriptions.Item>
              <Descriptions.Item label="设备数">
                {activeThreatDetail.uniqueDevices}
              </Descriptions.Item>
              <Descriptions.Item label="端口列表">
                {renderPortTags(activeThreatDetail.portList)}
              </Descriptions.Item>
              <Descriptions.Item label="端口风险评分">
                {typeof activeThreatDetail.portRiskScore === 'number'
                  ? activeThreatDetail.portRiskScore.toFixed(2)
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="检测层级">
                {typeof activeThreatDetail.detectionTier === 'number'
                  ? `Tier ${activeThreatDetail.detectionTier}`
                  : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="评估时间">
                {formatDateTime(activeThreatDetail.assessmentTime)}
              </Descriptions.Item>
              <Descriptions.Item label="创建时间">
                {formatDateTime(activeThreatDetail.createdAt)}
              </Descriptions.Item>
              <Descriptions.Item label="缓解建议">
                {renderMitigationList(activeThreatDetail.mitigationRecommendations)}
              </Descriptions.Item>
            </Descriptions>

            <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
              <Button
                danger
                icon={<DeleteOutlined />}
                onClick={() => handleDelete(activeThreatDetail.id)}
              >
                删除该记录
              </Button>
            </Space>
          </Space>
        )}
      </Drawer>
    </Space>
  );
};

export default ThreatList;
