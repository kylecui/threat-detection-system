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
  Select,
  Button,
  Segmented,
} from 'antd';
import {
  WarningOutlined,
  FireOutlined,
  AlertOutlined,
  InfoCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { Line, Pie, Column } from '@ant-design/charts';
import type { Statistics, ThreatAssessment, ChartDataPoint, Customer, TopAttacker } from '@/types';
import { ThreatLevel } from '@/types';
import threatService from '@/services/threat';
import { getCustomerId } from '@/services/api';
import dayjs from 'dayjs';

type PortDatum = { port: string; count: number };

const Overview = () => {
  const [loading, setLoading] = useState(true);
  const [statistics, setStatistics] = useState<Statistics | null>(null);
  const [trendData, setTrendData] = useState<ChartDataPoint[]>([]);
  const [portData, setPortData] = useState<PortDatum[]>([]);
  const [topAttackers, setTopAttackers] = useState<TopAttacker[]>([]);
  const [recentThreats, setRecentThreats] = useState<ThreatAssessment[]>([]);
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
  const customerId = getCustomerId();

  const hoursMap: Record<string, number> = {
    '24h': 24,
    '7d': 168,
    '30d': 720,
  };

  const loadData = useCallback(async () => {
    try {
      setLoading(true);

      const useTenantAll = isTenantAdmin && selectedCustomer === '__all__';
      const allCustomerIds = tenantCustomers.map((c) => c.customerId).filter(Boolean);

      let stats: Statistics;
      let trend: ChartDataPoint[];
      let ports: ChartDataPoint[];
      let attackers: TopAttacker[];
      let recent: ThreatAssessment[];

      const hours = hoursMap[trendRange];

      if (useTenantAll && allCustomerIds.length > 0) {
        const results = await Promise.all([
          threatService.getTenantStatistics(allCustomerIds),
          threatService.getTenantTrend(allCustomerIds, hours).catch(() => []),
          threatService.getTenantPortDistribution(allCustomerIds, hours).catch(() => []),
          threatService.getTopAttackers(allCustomerIds[0], 10, hours).catch(() => []),
          threatService
            .getTenantThreatList(allCustomerIds, { page: 0, page_size: 10 })
            .then((res) => res.content || [])
            .catch(() => []),
        ]);
        [stats, trend, ports, attackers, recent] = results;
      } else {
        const targetCustomerId = isTenantAdmin ? selectedCustomer : customerId;
        if (!targetCustomerId || targetCustomerId === '__all__') {
          setStatistics(null);
          setTrendData([]);
          setPortData([]);
          setTopAttackers([]);
          setRecentThreats([]);
          return;
        }

        const results = await Promise.all([
          threatService.getStatistics(targetCustomerId),
          threatService.getThreatTrend(targetCustomerId, hours).catch(() => []),
          threatService.getPortDistribution(targetCustomerId, hours).catch(() => []),
          threatService.getTopAttackers(targetCustomerId, 10, hours).catch(() => []),
          threatService
            .getThreatList({
              customer_id: targetCustomerId,
              page: 0,
              page_size: 10,
              sort_by: 'assessment_time',
              sort_order: 'desc',
            })
            .then((res) => res.content || [])
            .catch(() => []),
        ]);
        [stats, trend, ports, attackers, recent] = results;
      }

      setStatistics(stats);
      setRecentThreats(recent);

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

      setTopAttackers(attackers || []);
    } catch (error) {
      console.error('Failed to load overview data:', error);
      message.error('加载总览数据失败');
    } finally {
      setLoading(false);
    }
  }, [customerId, isTenantAdmin, selectedCustomer, tenantCustomers, trendRange]);

  const loadTenantCustomers = useCallback(async () => {
    if (!isTenantAdmin || !tenantId) return;
    try {
      const customers = await threatService.getCustomersByTenant(tenantId);
      setTenantCustomers(customers || []);
      setSelectedCustomer('__all__');
    } catch (error) {
      console.error('Failed to load tenant customers:', error);
      message.error('加载租户客户列表失败');
    }
  }, [isTenantAdmin, tenantId]);

  useEffect(() => {
    loadTenantCustomers();
  }, [loadTenantCustomers]);

  useEffect(() => {
    loadData();
    const interval = setInterval(loadData, 30000);
    return () => clearInterval(interval);
  }, [loadData]);

  // ──────── 威胁等级标签 ────────
  const getThreatLevelTag = (level: ThreatLevel) => {
    const colorMap = {
      [ThreatLevel.CRITICAL]: 'red',
      [ThreatLevel.HIGH]: 'orange',
      [ThreatLevel.MEDIUM]: 'gold',
      [ThreatLevel.LOW]: 'blue',
      [ThreatLevel.INFO]: 'default',
    };
    return <Tag color={colorMap[level]}>{level}</Tag>;
  };

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
    label: { position: 'top' as const },
    xAxis: { label: { autoRotate: true, autoHide: false } },
    yAxis: { title: { text: '攻击次数' } },
  };

  // ──────── 最新威胁列表列定义 ────────
  const recentColumns = [
    {
      title: '评估时间',
      dataIndex: 'assessmentTime',
      key: 'assessmentTime',
      render: (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm:ss'),
      width: 160,
    },
    {
      title: '攻击者MAC',
      dataIndex: 'attackMac',
      key: 'attackMac',
      render: (mac: string) => <code>{mac}</code>,
    },
    {
      title: '威胁等级',
      dataIndex: 'threatLevel',
      key: 'threatLevel',
      render: (level: ThreatLevel) => getThreatLevelTag(level),
      width: 100,
    },
    {
      title: '威胁分数',
      dataIndex: 'threatScore',
      key: 'threatScore',
      render: (score: number) => score?.toFixed(2) || 'N/A',
      width: 100,
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
  ];

  // ──────── Top攻击者列表列定义 ────────
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
      dataIndex: 'totalCount',
      key: 'totalCount',
      sorter: (a: TopAttacker, b: TopAttacker) => a.totalCount - b.totalCount,
    },
    {
      title: '最高威胁分数',
      dataIndex: 'maxThreatScore',
      key: 'maxThreatScore',
      render: (score: number) => (
        <span style={{ color: score > 200 ? '#cf1322' : score > 100 ? '#fa8c16' : '#1890ff' }}>
          {score?.toFixed(2)}
        </span>
      ),
      sorter: (a: TopAttacker, b: TopAttacker) => a.maxThreatScore - b.maxThreatScore,
    },
    {
      title: '最高威胁等级',
      dataIndex: 'maxThreatLevel',
      key: 'maxThreatLevel',
      width: 120,
      render: (level: string) => {
        const colorMap: Record<string, string> = {
          CRITICAL: 'red',
          HIGH: 'orange',
          MEDIUM: 'gold',
          LOW: 'blue',
          INFO: 'default',
        };
        return <Tag color={colorMap[level] || 'default'}>{level}</Tag>;
      },
    },
  ];

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '100px' }}>
        <Spin size="large" tip="加载总览数据..." />
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
              <span style={{ fontWeight: 600 }}>威胁总览</span>
              {isTenantAdmin && (
                <>
                  <span>客户筛选:</span>
                  <Select
                    style={{ width: 300 }}
                    value={selectedCustomer}
                    onChange={(v) => setSelectedCustomer(v)}
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

      {/* ── 统计卡片: 总威胁数 / 严重 / 高危 / 中危 ── */}
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
              title="严重威胁"
              value={statistics?.criticalCount || 0}
              valueStyle={{ color: '#cf1322' }}
              prefix={<FireOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="高危威胁"
              value={statistics?.highCount || 0}
              valueStyle={{ color: '#fa8c16' }}
              prefix={<WarningOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="中危威胁"
              value={statistics?.mediumCount || 0}
              valueStyle={{ color: '#faad14' }}
              prefix={<InfoCircleOutlined />}
            />
          </Card>
        </Col>
      </Row>

      {/* ── 趋势图 + 等级分布 ── */}
      <Row gutter={16}>
        <Col xs={24} lg={14}>
          <Card
            title={`威胁趋势 (${trendRange === '24h' ? '24小时' : trendRange === '7d' ? '7天' : '30天'})`}
            bordered={false}
          >
            {trendData.length > 0 ? (
              <Line {...trendConfig} height={320} />
            ) : (
              <div style={{ textAlign: 'center', padding: 60, color: '#999' }}>暂无趋势数据</div>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title="威胁等级分布" bordered={false}>
            {levelDistData.length > 0 ? (
              <Pie {...levelPieConfig} height={320} />
            ) : (
              <div style={{ textAlign: 'center', padding: 60, color: '#999' }}>暂无数据</div>
            )}
          </Card>
        </Col>
      </Row>

      {/* ── 端口分布 + Top攻击者 ── */}
      <Row gutter={16}>
        <Col xs={24} lg={12}>
          <Card title="端口攻击分布 (Top 15)" bordered={false}>
            {portData.length > 0 ? (
              <Column {...portBarConfig} height={350} />
            ) : (
              <div style={{ textAlign: 'center', padding: 60, color: '#999' }}>暂无数据</div>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="Top 攻击者排行" bordered={false}>
            <Table
              columns={attackerColumns}
              dataSource={topAttackers}
              rowKey="attackMac"
              pagination={false}
              size="small"
            />
          </Card>
        </Col>
      </Row>

      {/* ── 最新威胁 ── */}
      <Card
        title="最新威胁"
        bordered={false}
        extra={
          <Button type="link" onClick={() => { window.location.href = '/threats'; }}>
            查看全部 &rarr;
          </Button>
        }
      >
        <Table
          columns={recentColumns}
          dataSource={recentThreats}
          rowKey="id"
          pagination={false}
          size="small"
        />
      </Card>
    </Space>
  );
};

export default Overview;
