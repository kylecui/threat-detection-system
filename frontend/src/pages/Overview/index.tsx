import { useEffect, useState, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Card,
  Row,
  Col,
  Statistic,
  Table,
  Tag,
  Space,
  Skeleton,
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
import { useTranslation } from 'react-i18next';
import type { Statistics, ThreatAssessment, ChartDataPoint, Customer, TopAttacker } from '@/types';
import { ThreatLevel } from '@/types';
import threatService from '@/services/threat';
import { useScope } from '@/contexts/ScopeContext';
import { usePermission } from '@/hooks/usePermission';
import { useAuth } from '@/contexts/AuthContext';
import EmptyState from '@/components/EmptyState';
import dayjs from 'dayjs';

type PortDatum = { port: string; count: number };

const Overview = () => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const { effectiveCustomerId: customerId, tenantId: scopeTenantId, initialized } = useScope();
  const [loading, setLoading] = useState(true);
  const [statistics, setStatistics] = useState<Statistics | null>(null);
  const [trendData, setTrendData] = useState<ChartDataPoint[]>([]);
  const [portData, setPortData] = useState<PortDatum[]>([]);
  const [topAttackers, setTopAttackers] = useState<TopAttacker[]>([]);
  const [recentThreats, setRecentThreats] = useState<ThreatAssessment[]>([]);
  const [trendRange, setTrendRange] = useState<string>('24h');
  const [tenantCustomers, setTenantCustomers] = useState<Customer[]>([]);
  const [selectedCustomer, setSelectedCustomer] = useState<string>('__all__');

  const { user } = useAuth();
  const { isTenantAdmin, isSuperAdmin } = usePermission();
  const tenantId = user?.tenantId;

  const hoursMap: Record<string, number> = {
    '24h': 24,
    '7d': 168,
    '30d': 720,
  };

  const loadData = useCallback(async () => {
    try {
      setLoading(true);

      const useTenantAll = (isTenantAdmin || isSuperAdmin) && selectedCustomer === '__all__';
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
        const targetCustomerId = (isTenantAdmin || isSuperAdmin) ? selectedCustomer : customerId;
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
        category: t('overview.threatCount'),
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
      message.error(t('overview.messageLoadFailed'));
    } finally {
      setLoading(false);
    }
  }, [customerId, isTenantAdmin, isSuperAdmin, selectedCustomer, tenantCustomers, trendRange]);

  const loadTenantCustomers = useCallback(async () => {
    if (!isTenantAdmin && !isSuperAdmin) return;
    const tid = isSuperAdmin ? scopeTenantId : tenantId;
    if (!tid) return;
    try {
      const customers = await threatService.getCustomersByTenant(tid);
      setTenantCustomers(customers || []);
      setSelectedCustomer('__all__');
    } catch (error) {
      console.error('Failed to load tenant customers:', error);
      message.error(t('overview.messageLoadTenantCustomersFailed'));
    }
  }, [isTenantAdmin, isSuperAdmin, tenantId, scopeTenantId, t]);

  useEffect(() => {
    loadTenantCustomers();
  }, [loadTenantCustomers]);

  useEffect(() => {
    if (!initialized) return;
    loadData();
    const interval = setInterval(loadData, 30000);
    return () => clearInterval(interval);
  }, [initialized, loadData]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key.toLowerCase() === 'r' && !e.ctrlKey && !e.metaKey && !e.altKey) {
        const target = e.target as HTMLElement;
        if (
          target.tagName === 'INPUT'
          || target.tagName === 'TEXTAREA'
          || target.isContentEditable
        ) {
          return;
        }
        void loadData();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
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
    yAxis: { title: { text: t('overview.threatCount') } },
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
      title: { content: t('common.total') },
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
    yAxis: { title: { text: t('overview.attackCount') } },
  };

  // ──────── 最新威胁列表列定义 ────────
  const recentColumns = [
    {
      title: t('overview.assessmentTime'),
      dataIndex: 'assessmentTime',
      key: 'assessmentTime',
      render: (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm:ss'),
      width: 160,
    },
    {
      title: t('overview.attackMac'),
      dataIndex: 'attackMac',
      key: 'attackMac',
      render: (mac: string) => <code>{mac}</code>,
    },
    {
      title: t('overview.threatLevel'),
      dataIndex: 'threatLevel',
      key: 'threatLevel',
      render: (level: ThreatLevel) => getThreatLevelTag(level),
      width: 100,
    },
    {
      title: t('overview.threatScore'),
      dataIndex: 'threatScore',
      key: 'threatScore',
      render: (score: number) => score?.toFixed(2) || 'N/A',
      width: 100,
    },
    {
      title: t('overview.attackCount'),
      dataIndex: 'attackCount',
      key: 'attackCount',
      width: 100,
    },
    {
      title: t('overview.uniqueIps'),
      dataIndex: 'uniqueIps',
      key: 'uniqueIps',
      width: 100,
    },
    {
      title: t('overview.uniquePorts'),
      dataIndex: 'uniquePorts',
      key: 'uniquePorts',
      width: 100,
    },
  ];

  // ──────── Top攻击者列表列定义 ────────
  const attackerColumns = [
    {
      title: t('overview.rank'),
      key: 'rank',
      width: 60,
      render: (_: unknown, __: unknown, index: number) => (
        <Tag color={index < 3 ? 'red' : index < 5 ? 'orange' : 'default'}>
          #{index + 1}
        </Tag>
      ),
    },
    {
      title: t('overview.attackMac'),
      dataIndex: 'attackMac',
      key: 'attackMac',
      render: (mac: string) => <code>{mac}</code>,
    },
    {
      title: t('overview.attackIp'),
      dataIndex: 'attackIp',
      key: 'attackIp',
      render: (ip: string) => ip || '-',
    },
    {
      title: t('overview.attackCount'),
      dataIndex: 'totalCount',
      key: 'totalCount',
      sorter: (a: TopAttacker, b: TopAttacker) => a.totalCount - b.totalCount,
    },
    {
      title: t('overview.maxThreatScore'),
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
      title: t('overview.maxThreatLevel'),
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
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card>
          <Skeleton active paragraph={{ rows: 1 }} />
        </Card>
        <Row gutter={16}>
          {[1, 2, 3, 4].map((i) => (
            <Col xs={24} sm={12} lg={6} key={i}>
              <Card>
                <Skeleton active paragraph={{ rows: 1 }} />
              </Card>
            </Col>
          ))}
        </Row>
        <Row gutter={16}>
          <Col xs={24} lg={14}>
            <Card>
              <Skeleton active paragraph={{ rows: 8 }} />
            </Card>
          </Col>
          <Col xs={24} lg={10}>
            <Card>
              <Skeleton active paragraph={{ rows: 8 }} />
            </Card>
          </Col>
        </Row>
      </Space>
    );
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {/* ── 操作栏 ── */}
      <Card size="small">
        <Row gutter={[12, 12]} justify="space-between" align="middle">
          <Col xs={24} md={18}>
            <Space wrap>
              <span style={{ fontWeight: 600 }}>{t('overview.title')}</span>
              {(isTenantAdmin || isSuperAdmin) && (
                <>
                  <span>{t('overview.customerFilter')}:</span>
                  <Select
                    style={{ width: '100%', minWidth: 200, maxWidth: 300 }}
                    value={selectedCustomer}
                    onChange={(v) => setSelectedCustomer(v)}
                    options={[
                      { label: t('overview.allCustomers'), value: '__all__' },
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
                  { label: t('overview.range24h'), value: '24h' },
                  { label: t('overview.range7d'), value: '7d' },
                  { label: t('overview.range30d'), value: '30d' },
                ]}
              />
            </Space>
          </Col>
          <Col xs={24} md={6} style={{ textAlign: 'right' }}>
            <Button aria-label={t('overview.refreshAria')} icon={<ReloadOutlined />} onClick={loadData}>
              {t('common.refresh')}
            </Button>
          </Col>
        </Row>
      </Card>

      {/* ── 统计卡片: 总威胁数 / 严重 / 高危 / 中危 ── */}
      <Row gutter={16}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title={t('overview.totalThreats')}
              value={statistics?.totalCount || 0}
              prefix={<AlertOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title={t('overview.criticalThreats')}
              value={statistics?.criticalCount || 0}
              valueStyle={{ color: '#cf1322' }}
              prefix={<FireOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title={t('overview.highThreats')}
              value={statistics?.highCount || 0}
              valueStyle={{ color: '#fa8c16' }}
              prefix={<WarningOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title={t('overview.mediumThreats')}
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
            title={`${t('overview.threatTrend')} (${trendRange === '24h' ? t('overview.range24h') : trendRange === '7d' ? t('overview.range7d') : t('overview.range30d')})`}
            variant="borderless"
          >
            {trendData.length > 0 ? (
              <div role="img" aria-label={t('overview.trendChartAria')}>
                <Line {...trendConfig} height={320} />
              </div>
            ) : (
              <EmptyState description={t('overview.noTrendData')} image="simple" />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={10}>
          <Card title={t('overview.threatLevelDistribution')} variant="borderless">
            {levelDistData.length > 0 ? (
              <div role="img" aria-label={t('overview.levelChartAria')}>
                <Pie {...levelPieConfig} height={320} />
              </div>
            ) : (
              <EmptyState image="simple" />
            )}
          </Card>
        </Col>
      </Row>

      {/* ── 端口分布 + Top攻击者 ── */}
      <Row gutter={16}>
        <Col xs={24} lg={12}>
          <Card title={t('overview.portDistributionTop15')} variant="borderless">
            {portData.length > 0 ? (
              <div role="img" aria-label={t('overview.portChartAria')}>
                <Column {...portBarConfig} height={350} />
              </div>
            ) : (
              <EmptyState image="simple" />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title={t('overview.topAttackers')} variant="borderless">
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
        title={t('overview.latestThreats')}
        variant="borderless"
        extra={
          <Button
            type="link"
            aria-label={t('overview.viewAllAria')}
            onClick={() => navigate('/investigate/threats')}
          >
            {t('overview.viewAll')} &rarr;
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
