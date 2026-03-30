import { useEffect, useState, useCallback } from 'react';
import {
  Card,
  Row,
  Col,
  Statistic,
  Table,
  Tag,
  Space,
  Spin,
  message,
  Button,
  Segmented,
  Select,
} from 'antd';
import {
  ReloadOutlined,
  RiseOutlined,
  FallOutlined,
  FireOutlined,
  AlertOutlined,
} from '@ant-design/icons';
import { Line, Pie, Column } from '@ant-design/charts';
import type { Statistics, ChartDataPoint, ThreatAssessment, Customer } from '@/types';
import threatService from '@/services/threat';
import dayjs from 'dayjs';

type TopAttacker = {
  attackMac: string;
  attackIp?: string;
  attackCount: number;
  threatScore: number;
  uniquePorts: number;
  uniqueIps: number;
};

/** Derive top attackers from assessments data (client-side aggregation) */
function deriveTopAttackers(assessments: ThreatAssessment[], limit: number): TopAttacker[] {
  const map = new Map<string, TopAttacker>();
  for (const a of assessments) {
    const key = a.attackMac;
    const existing = map.get(key);
    if (existing) {
      existing.attackCount += a.attackCount;
      existing.threatScore = Math.max(existing.threatScore, a.threatScore);
      existing.uniquePorts = Math.max(existing.uniquePorts, a.uniquePorts);
      existing.uniqueIps = Math.max(existing.uniqueIps, a.uniqueIps);
      if (!existing.attackIp && a.attackIp) existing.attackIp = a.attackIp;
    } else {
      map.set(key, {
        attackMac: a.attackMac,
        attackIp: a.attackIp,
        attackCount: a.attackCount,
        threatScore: a.threatScore,
        uniquePorts: a.uniquePorts,
        uniqueIps: a.uniqueIps,
      });
    }
  }
  return Array.from(map.values())
    .sort((a, b) => b.threatScore - a.threatScore)
    .slice(0, limit);
}

type PortDatum = { port: string; count: number };

/**
 * 数据分析页面
 *
 * 功能:
 * - 威胁统计概览
 * - 威胁趋势折线图 (24h / 7d / 30d)
 * - 威胁等级分布饼图
 * - 端口攻击分布柱状图
 * - Top 攻击者排行表
 */
const Analytics = () => {
  const [loading, setLoading] = useState(true);
  const [statistics, setStatistics] = useState<Statistics | null>(null);
  const [trendData, setTrendData] = useState<ChartDataPoint[]>([]);
  const [portData, setPortData] = useState<PortDatum[]>([]);
  const [topAttackers, setTopAttackers] = useState<TopAttacker[]>([]);
  const [trendRange, setTrendRange] = useState<string>('24h');
  const [tenantCustomers, setTenantCustomers] = useState<Customer[]>([]);
  const [selectedCustomer, setSelectedCustomer] = useState<string>('__all__');

  const userRaw = localStorage.getItem('user');
  const user = userRaw
    ? (JSON.parse(userRaw) as {
        roles?: string[];
        customerId?: string;
        tenantId?: number;
      })
    : null;
  const isTenantAdmin = !!user?.roles?.includes('TENANT_ADMIN');
  const tenantId = user?.tenantId;

  const customerId = localStorage.getItem('customer_id') || user?.customerId || 'demo-customer';

  /** 加载所有分析数据 */
  const loadData = useCallback(async () => {
    try {
      setLoading(true);

      const hoursMap: Record<string, number> = {
        '24h': 24,
        '7d': 168,
        '30d': 720,
      };

      const useTenantAll = isTenantAdmin && selectedCustomer === '__all__';
      const allCustomerIds = tenantCustomers.map((c) => c.customerId).filter(Boolean);

      let stats: Statistics;
      let trend: ChartDataPoint[];
      let ports: ChartDataPoint[];
      let assessments: ThreatAssessment[];

      if (useTenantAll && allCustomerIds.length > 0) {
        const tenantResults = await Promise.all([
          threatService.getTenantStatistics(allCustomerIds),
          threatService.getTenantTrend(allCustomerIds).catch(() => []),
          threatService.getTenantPortDistribution(allCustomerIds).catch(() => []),
          threatService
            .getTenantThreatList(allCustomerIds, { page: 0, page_size: 200 })
            .then((res) => res.content || [])
            .catch(() => []),
        ]);
        [stats, trend, ports, assessments] = tenantResults;
      } else {
        const targetCustomerId = isTenantAdmin ? selectedCustomer : customerId;
        if (!targetCustomerId || targetCustomerId === '__all__') {
          setStatistics(null);
          setTrendData([]);
          setPortData([]);
          setTopAttackers([]);
          return;
        }

        const customerResults = await Promise.all([
          threatService.getStatistics(targetCustomerId),
          threatService.getThreatTrend(targetCustomerId).catch(() => []),
          threatService.getPortDistribution(targetCustomerId).catch(() => []),
          threatService
            .getThreatList({ customer_id: targetCustomerId, page: 0, page_size: 200 })
            .then((res) => res.content || [])
            .catch(() => []),
        ]);
        [stats, trend, ports, assessments] = customerResults;
      }

      setStatistics(stats);

      const formattedTrend = (trend || []).map((item: any) => ({
        time:
          hoursMap[trendRange] > 48
            ? dayjs(item.timestamp).format('MM-DD')
            : dayjs(item.timestamp).format('HH:mm'),
        value: item.count ?? item.value ?? 0,
        category: '威胁数量',
      }));
      setTrendData(formattedTrend);

      const formattedPorts = (ports || []).map((item: any) => ({
        port: item.port ? `Port ${item.port}` : item.portName || 'Unknown',
        count: item.count ?? item.value ?? 0,
      }));
      setPortData(formattedPorts.slice(0, 15));

      setTopAttackers(deriveTopAttackers(assessments, 20));
    } catch (error) {
      console.error('Failed to load analytics data:', error);
      message.error('加载分析数据失败');
    } finally {
      setLoading(false);
    }
  }, [customerId, isTenantAdmin, selectedCustomer, tenantCustomers, trendRange]);

  const loadTenantCustomers = useCallback(async () => {
    if (!isTenantAdmin || !tenantId) {
      return;
    }

    try {
      const customers = await threatService.getCustomersByTenant(tenantId);
      setTenantCustomers(customers || []);
      setSelectedCustomer('__all__');
    } catch (error) {
      console.error('Failed to load tenant customers:', error);
      message.error('加载租户客户列表失败');
    }
  }, [isTenantAdmin, tenantId]);

  const handleCustomerChange = (value: string) => {
    setSelectedCustomer(value);
  };

  useEffect(() => {
    loadTenantCustomers();
  }, [loadTenantCustomers]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // ──────── 威胁等级分布数据 ────────
  const levelDistData = statistics
    ? [
        { level: 'CRITICAL', count: statistics.criticalCount, color: '#cf1322' },
        { level: 'HIGH', count: statistics.highCount, color: '#fa8c16' },
        { level: 'MEDIUM', count: statistics.mediumCount, color: '#faad14' },
        { level: 'LOW', count: statistics.lowCount, color: '#1890ff' },
        { level: 'INFO', count: statistics.infoCount, color: '#8c8c8c' },
      ].filter((d) => d.count > 0)
    : [];

  // ──────── 图表配置 ────────

  const trendConfig = {
    data: trendData,
    xField: 'time',
    yField: 'value',
    smooth: true,
    point: { size: 3, shape: 'circle' as const },
    color: '#1890ff',
    area: { style: { fillOpacity: 0.15 } },
    yAxis: { title: { text: '威胁数量' } },
  };

  const levelPieConfig = {
    data: levelDistData,
    angleField: 'count',
    colorField: 'level',
    radius: 0.85,
    innerRadius: 0.55,
    label: {
      text: (d: { level: string; count: number }) => `${d.level}\n${d.count}`,
    },
    legend: { position: 'bottom' as const },
    statistic: {
      title: { content: '总计' },
      content: {
        content: String(statistics?.totalCount || 0),
      },
    },
  };

  const portBarConfig = {
    data: portData,
    xField: 'port',
    yField: 'count',
    color: '#5B8FF9',
    label: {
      position: 'top' as const,
    },
    xAxis: {
      label: { autoRotate: true, autoHide: false },
    },
    yAxis: { title: { text: '攻击次数' } },
  };

  // ──────── Top攻击者表格 ────────
  const attackerColumns = [
    {
      title: '排名',
      key: 'rank',
      width: 60,
      render: (_: unknown, __: unknown, index: number) => (
        <Tag color={index < 3 ? 'red' : index < 5 ? 'orange' : 'default'}>
          #{index + 1}
        </Tag>
      ),
    },
    {
      title: '攻击者MAC',
      dataIndex: 'attackMac',
      key: 'attackMac',
      render: (mac: string) => <code>{mac}</code>,
    },
    {
      title: '攻击IP',
      dataIndex: 'attackIp',
      key: 'attackIp',
      render: (ip: string) => ip || '-',
    },
    {
      title: '攻击次数',
      dataIndex: 'attackCount',
      key: 'attackCount',
      sorter: (a: TopAttacker, b: TopAttacker) => a.attackCount - b.attackCount,
    },
    {
      title: '威胁分数',
      dataIndex: 'threatScore',
      key: 'threatScore',
      render: (score: number) => (
        <span style={{ color: score > 200 ? '#cf1322' : score > 100 ? '#fa8c16' : '#1890ff' }}>
          {score?.toFixed(2)}
        </span>
      ),
      sorter: (a: TopAttacker, b: TopAttacker) => a.threatScore - b.threatScore,
    },
    {
      title: '端口种类',
      dataIndex: 'uniquePorts',
      key: 'uniquePorts',
    },
    {
      title: '诱饵IP数',
      dataIndex: 'uniqueIps',
      key: 'uniqueIps',
    },
  ];

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '100px' }}>
        <Spin size="large" tip="加载分析数据..." />
      </div>
    );
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {/* ── 操作栏 ── */}
      <Card size="small">
        <Row justify="space-between" align="middle">
          <Col>
            <Space>
              <span style={{ fontWeight: 600 }}>数据分析</span>
              {isTenantAdmin && (
                <>
                  <span>客户筛选:</span>
                  <Select
                    style={{ width: 300 }}
                    value={selectedCustomer}
                    onChange={handleCustomerChange}
                    options={[
                      { label: '全部客户 (All)', value: '__all__' },
                      ...tenantCustomers.map((c) => ({
                        label: `${c.name} (${c.customerId})`,
                        value: c.customerId,
                      })),
                    ]}
                  />
                </>
              )}
              <Segmented
                value={trendRange}
                onChange={(v) => setTrendRange(v as string)}
                options={[
                  { label: '24小时', value: '24h' },
                  { label: '7天', value: '7d' },
                  { label: '30天', value: '30d' },
                ]}
              />
            </Space>
          </Col>
          <Col>
            <Button icon={<ReloadOutlined />} onClick={loadData}>
              刷新
            </Button>
          </Col>
        </Row>
      </Card>

      {/* ── 统计概览 ── */}
      <Row gutter={16}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="总威胁数"
              value={statistics?.totalCount || 0}
              prefix={<AlertOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="严重 + 高危"
              value={(statistics?.criticalCount || 0) + (statistics?.highCount || 0)}
              valueStyle={{ color: '#cf1322' }}
              prefix={<FireOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="最高分数"
              value={statistics?.maxThreatScore?.toFixed(2) || '0.00'}
              valueStyle={{ color: '#fa8c16' }}
              prefix={<RiseOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="平均分数"
              value={statistics?.averageThreatScore?.toFixed(2) || '0.00'}
              prefix={<FallOutlined />}
            />
          </Card>
        </Col>
      </Row>

      {/* ── 趋势图 ── */}
      <Card title={`威胁趋势 (${trendRange === '24h' ? '24小时' : trendRange === '7d' ? '7天' : '30天'})`} bordered={false}>
        {trendData.length > 0 ? (
          <Line {...trendConfig} height={320} />
        ) : (
          <div style={{ textAlign: 'center', padding: 60, color: '#999' }}>暂无趋势数据</div>
        )}
      </Card>

      {/* ── 等级分布 + 端口分布 ── */}
      <Row gutter={16}>
        <Col xs={24} lg={10}>
          <Card title="威胁等级分布" bordered={false}>
            {levelDistData.length > 0 ? (
              <Pie {...levelPieConfig} height={350} />
            ) : (
              <div style={{ textAlign: 'center', padding: 60, color: '#999' }}>暂无数据</div>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={14}>
          <Card title="端口攻击分布 (Top 15)" bordered={false}>
            {portData.length > 0 ? (
              <Column {...portBarConfig} height={350} />
            ) : (
              <div style={{ textAlign: 'center', padding: 60, color: '#999' }}>暂无数据</div>
            )}
          </Card>
        </Col>
      </Row>

      {/* ── Top 攻击者 ── */}
      <Card title="Top 攻击者排行" bordered={false}>
        <Table
          columns={attackerColumns}
          dataSource={topAttackers}
          rowKey="attackMac"
          pagination={{ pageSize: 10 }}
          size="middle"
        />
      </Card>
    </Space>
  );
};

export default Analytics;
