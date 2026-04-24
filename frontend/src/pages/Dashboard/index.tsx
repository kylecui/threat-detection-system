import { useEffect, useState, useCallback } from 'react';
import { Card, Row, Col, Statistic, Table, Tag, Space, Spin, message, Select } from 'antd';
import {
  WarningOutlined,
  FireOutlined,
  AlertOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { Line, Pie } from '@ant-design/charts';
import type { Statistics, ThreatAssessment, ChartDataPoint, Customer } from '@/types';
import { ThreatLevel } from '@/types';
import threatService from '@/services/threat';
import { getCustomerId } from '@/services/api';
import dayjs from 'dayjs';

// 在文件顶部 import 之后添加（或放在组件内也可）
type PortDatum = { category: string; value: number };


/**
 * 仪表盘页面
 * 
 * 功能:
 * - 威胁统计卡片
 * - 24小时威胁趋势图
 * - 最新威胁列表
 * - 端口分布图
 */
const Dashboard = () => {
  const [loading, setLoading] = useState(true);
  const [statistics, setStatistics] = useState<Statistics | null>(null);
  const [recentThreats, setRecentThreats] = useState<ThreatAssessment[]>([]);
  const [trendData, setTrendData] = useState<ChartDataPoint[]>([]);
  const [portData, setPortData] = useState<PortDatum[]>([]);
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

  /**
   * 加载仪表盘数据
   */
  const loadDashboardData = useCallback(async () => {
    try {
      setLoading(true);

      const useTenantAll = isTenantAdmin && selectedCustomer === '__all__';
      const allCustomerIds = tenantCustomers.map((c) => c.customerId).filter(Boolean);

      if (useTenantAll && allCustomerIds.length > 0) {
        const [stats, threats, trend, ports] = await Promise.all([
          threatService.getTenantStatistics(allCustomerIds),
          threatService.getTenantThreatList(allCustomerIds, {
            page: 0,
            page_size: 10,
          }),
          threatService.getTenantTrend(allCustomerIds),
          threatService.getTenantPortDistribution(allCustomerIds),
        ]);

        setStatistics(stats);
        setRecentThreats(threats.content || []);

        const formattedTrend = trend.map((item: any) => ({
          time: dayjs(item.timestamp).format('HH:mm'),
          value: item.count,
          averageScore: item.averageScore,
        }));
        setTrendData(formattedTrend);

        const formattedPorts = ports.map((item: any) => ({
          category: item.port ? `Port ${item.port}` : item.portName || 'Unknown',
          value: item.count,
        }));
        setPortData(formattedPorts);
        return;
      }

      const targetCustomerId = isTenantAdmin ? selectedCustomer : customerId;
      if (!targetCustomerId || targetCustomerId === '__all__') {
        setStatistics(null);
        setRecentThreats([]);
        setTrendData([]);
        setPortData([]);
        return;
      }

      // 并行加载所有数据
      const [stats, threats, trend, ports] = await Promise.all([
        threatService.getStatistics(targetCustomerId),
        threatService.getThreatList({
          customer_id: targetCustomerId,
          page: 0,
          page_size: 10,
          sort_by: 'assessment_time',
          sort_order: 'desc',
        }),
        threatService.getThreatTrend(targetCustomerId),
        threatService.getPortDistribution(targetCustomerId),
      ]);

      setStatistics(stats);
      setRecentThreats(threats.content || []);
      
      // 转换趋势数据格式: timestamp -> time, count -> value
      const formattedTrend = trend.map((item: any) => ({
        time: dayjs(item.timestamp).format('HH:mm'),
        value: item.count,
        averageScore: item.averageScore,
      }));
      setTrendData(formattedTrend);
      
      // 转换端口分布数据格式
      const formattedPorts = ports.map((item: any) => ({
        category: item.port ? `Port ${item.port}` : item.portName || 'Unknown',
        value: item.count,
      }));
      setPortData(formattedPorts);
    } catch (error) {
      console.error('Failed to load dashboard data:', error);
      message.error('加载仪表盘数据失败');
    } finally {
      setLoading(false);
    }
  }, [customerId, isTenantAdmin, selectedCustomer, tenantCustomers]);

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
    loadDashboardData();

    // 自动刷新 (每30秒)
    const interval = setInterval(loadDashboardData, 30000);
    return () => clearInterval(interval);
  }, [loadDashboardData]);

  /**
   * 威胁等级标签
   */
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

  /**
   * 威胁列表表格列定义
   */
  const columns = [
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

  /**
   * 趋势图配置
   */
  const trendConfig = {
    data: trendData,
    xField: 'time',
    yField: 'value',
    smooth: true,
    point: {
      size: 3,
      shape: 'circle',
    },
    tooltip: {
      customContent: (title: string, items: any[]) => {
        if (!items || items.length === 0) return '';
        const item = items[0];
        return `<div style="padding: 8px;">
          <div>${title}</div>
          <div>威胁数量: ${item.value}</div>
        </div>`;
      },
    },
  };

  /**
   * 端口分布饼图配置
   */
  const portConfig = {
    data: portData.slice(0, 10), // 只显示Top 10
    angleField: 'value',
    colorField: 'category',
    radius: 0.8,
    label: {
      text: (item: any) => `${item.category}: ${item.value}`,
    },
    legend: {
      position: 'bottom' as const,
    },
  };

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: '100px' }}>
        <Spin size="large" tip="加载中..." />
      </div>
    );
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {isTenantAdmin && (
        <Card size="small" style={{ marginBottom: 16 }}>
          <Space>
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
          </Space>
        </Card>
      )}

      {/* 统计卡片 */}
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
              title="平均分数"
              value={statistics?.averageThreatScore?.toFixed(2) || '0.00'}
              prefix={<InfoCircleOutlined />}
            />
          </Card>
        </Col>
      </Row>

      {/* 威胁趋势图 */}
      <Card title="威胁趋势 (24小时)" bordered={false}>
        <Line {...trendConfig} height={300} />
      </Card>

      {/* 最新威胁 + 端口分布 */}
      <Row gutter={16}>
        <Col span={16}>
          <Card title="最新威胁" bordered={false}>
            <Table
              columns={columns}
              dataSource={recentThreats}
              rowKey="id"
              pagination={false}
              size="small"
            />
          </Card>
        </Col>
        <Col span={8}>
          <Card title="端口分布 (Top 10)" bordered={false}>
            <Pie {...portConfig} height={400} />
          </Card>
        </Col>
      </Row>
    </Space>
  );
};

export default Dashboard;
