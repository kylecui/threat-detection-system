import { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Table, Tag, Space, Spin, message } from 'antd';
import {
  WarningOutlined,
  FireOutlined,
  AlertOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import { Line, Pie } from '@ant-design/charts';
import type { Statistics, ThreatAssessment, ChartDataPoint } from '@/types';
import { ThreatLevel } from '@/types';
import threatService from '@/services/threat';
import dayjs from 'dayjs';

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
  const [portData, setPortData] = useState<ChartDataPoint[]>([]);
  const customerId = localStorage.getItem('customer_id') || 'demo-customer';

  /**
   * 加载仪表盘数据
   */
  const loadDashboardData = async () => {
    try {
      setLoading(true);

      // 并行加载所有数据
      const [stats, threats, trend, ports] = await Promise.all([
        threatService.getStatistics(customerId),
        threatService.getThreatList({
          page: 1,
          page_size: 10,
          sort_by: 'assessment_time',
          sort_order: 'desc',
        }),
        threatService.getThreatTrend(customerId),
        threatService.getPortDistribution(customerId),
      ]);

      setStatistics(stats);
      setRecentThreats(threats.items);
      setTrendData(trend);
      setPortData(ports);
    } catch (error) {
      console.error('Failed to load dashboard data:', error);
      message.error('加载仪表盘数据失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadDashboardData();

    // 自动刷新 (每30秒)
    const interval = setInterval(loadDashboardData, 30000);
    return () => clearInterval(interval);
  }, []);

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
      title: '时间',
      dataIndex: 'assessment_time',
      key: 'assessment_time',
      render: (time: string) => dayjs(time).format('YYYY-MM-DD HH:mm:ss'),
      width: 180,
    },
    {
      title: '攻击者MAC',
      dataIndex: 'attack_mac',
      key: 'attack_mac',
      render: (mac: string) => <code>{mac}</code>,
    },
    {
      title: '威胁等级',
      dataIndex: 'threat_level',
      key: 'threat_level',
      render: (level: ThreatLevel) => getThreatLevelTag(level),
      width: 100,
    },
    {
      title: '威胁分数',
      dataIndex: 'threat_score',
      key: 'threat_score',
      render: (score: number) => score.toFixed(2),
      width: 100,
    },
    {
      title: '攻击次数',
      dataIndex: 'attack_count',
      key: 'attack_count',
      width: 100,
    },
    {
      title: '诱饵IP数',
      dataIndex: 'unique_ips',
      key: 'unique_ips',
      width: 100,
    },
    {
      title: '端口种类',
      dataIndex: 'unique_ports',
      key: 'unique_ports',
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
      formatter: (datum: any) => ({
        name: '威胁数量',
        value: datum.value,
      }),
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
      type: 'outer',
      content: '{name} {percentage}',
    },
    interactions: [{ type: 'element-active' }],
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
      {/* 统计卡片 */}
      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic
              title="总威胁数"
              value={statistics?.total_threats || 0}
              prefix={<WarningOutlined />}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="严重威胁"
              value={statistics?.critical_threats || 0}
              prefix={<FireOutlined />}
              valueStyle={{ color: '#cf1322' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="高危威胁"
              value={statistics?.high_threats || 0}
              prefix={<AlertOutlined />}
              valueStyle={{ color: '#fa8c16' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="平均威胁分数"
              value={statistics?.avg_threat_score || 0}
              precision={2}
              prefix={<InfoCircleOutlined />}
              valueStyle={{ color: '#52c41a' }}
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
