import { useState, useEffect, useRef } from 'react';
import {
  Card,
  Row,
  Col,
  Statistic,
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
} from 'antd';
import {
  SearchOutlined,
  ReloadOutlined,
  DeleteOutlined,
  SyncOutlined,
  EyeOutlined,
} from '@ant-design/icons';
import { ProTable } from '@ant-design/pro-components';
import type { ActionType, ProColumns } from '@ant-design/pro-components';
import type { ThreatIndicator, ThreatFeed, IpLookupResult, IntelStatistics } from '@/types';
import { IntelSeverity } from '@/types';
import intelService from '@/services/intel';
import dayjs from 'dayjs';

const severityColorMap: Record<string, string> = {
  [IntelSeverity.CRITICAL]: 'red',
  [IntelSeverity.HIGH]: 'orange',
  [IntelSeverity.MEDIUM]: 'gold',
  [IntelSeverity.LOW]: 'blue',
  [IntelSeverity.INFO]: 'default',
};

/**
 * 威胁情报页面
 */
const ThreatIntel = () => {
  const actionRef = useRef<ActionType>();
  const [statistics, setStatistics] = useState<IntelStatistics | null>(null);
  const [lookupIp, setLookupIp] = useState('');
  const [lookupResult, setLookupResult] = useState<IpLookupResult | null>(null);
  const [lookupLoading, setLookupLoading] = useState(false);
  const [feeds, setFeeds] = useState<ThreatFeed[]>([]);
  const [feedsLoading, setFeedsLoading] = useState(false);

  useEffect(() => {
    intelService.getStatistics().then(setStatistics).catch(() => {});
    loadFeeds();
  }, []);

  const loadFeeds = async () => {
    setFeedsLoading(true);
    try {
      const data = await intelService.getFeeds();
      setFeeds(data);
    } catch {
      message.error('加载Feed列表失败');
    } finally {
      setFeedsLoading(false);
    }
  };

  /** IP查询 */
  const handleLookup = async () => {
    if (!lookupIp.trim()) return;
    setLookupLoading(true);
    try {
      const result = await intelService.lookup(lookupIp.trim());
      setLookupResult(result);
    } catch {
      message.error('查询失败');
    } finally {
      setLookupLoading(false);
    }
  };

  /** 手动触发Feed轮询 */
  const handlePollFeed = async (id: number) => {
    try {
      const result = await intelService.pollFeed(id);
      message.success(`轮询完成，获取 ${result.polledCount || 0} 条指标`);
      loadFeeds();
    } catch {
      message.error('轮询失败');
    }
  };

  /** 删除指标 */
  const handleDeleteIndicator = async (id: number) => {
    try {
      await intelService.deleteIndicator(id);
      message.success('删除成功');
      actionRef.current?.reload();
    } catch {
      message.error('删除失败');
    }
  };

  /** 添加目击记录 */
  const handleSighting = async (id: number) => {
    try {
      await intelService.addSighting(id);
      message.success('目击记录已添加');
      actionRef.current?.reload();
    } catch {
      message.error('操作失败');
    }
  };

  const indicatorColumns: ProColumns<ThreatIndicator>[] = [
    { title: 'ID', dataIndex: 'id', width: 60, search: false },
    {
      title: 'IOC值',
      dataIndex: 'iocValue',
      copyable: true,
      width: 200,
    },
    {
      title: 'IOC类型',
      dataIndex: 'iocType',
      width: 100,
      valueType: 'select',
      valueEnum: {
        IPV4: { text: 'IPv4' },
        IPV6: { text: 'IPv6' },
        DOMAIN: { text: '域名' },
        URL: { text: 'URL' },
        MD5: { text: 'MD5' },
        SHA256: { text: 'SHA256' },
      },
    },
    {
      title: '严重性',
      dataIndex: 'severity',
      width: 90,
      valueType: 'select',
      valueEnum: {
        CRITICAL: { text: '严重' },
        HIGH: { text: '高' },
        MEDIUM: { text: '中' },
        LOW: { text: '低' },
        INFO: { text: '信息' },
      },
      render: (_, record) => (
        <Tag color={severityColorMap[record.severity]}>{record.severity}</Tag>
      ),
    },
    {
      title: '置信度',
      dataIndex: 'confidence',
      width: 80,
      search: false,
      render: (val) => `${val}%`,
    },
    {
      title: '来源',
      dataIndex: 'sourceName',
      width: 120,
      search: false,
    },
    {
      title: '目击次数',
      dataIndex: 'sightingCount',
      width: 90,
      search: false,
    },
    {
      title: '最后发现',
      dataIndex: 'lastSeenAt',
      width: 150,
      search: false,
      render: (_, record) =>
        record.lastSeenAt ? dayjs(record.lastSeenAt).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作',
      width: 140,
      key: 'actions',
      search: false,
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleSighting(record.id)}
          >
            目击
          </Button>
          <Popconfirm
            title="确定删除?"
            onConfirm={() => handleDeleteIndicator(record.id)}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const feedColumns = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: '名称', dataIndex: 'name', key: 'name', width: 150 },
    { title: 'URL', dataIndex: 'url', key: 'url', ellipsis: true },
    { title: '类型', dataIndex: 'feedType', key: 'feedType', width: 100 },
    {
      title: '启用',
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (enabled: boolean) => <Switch checked={enabled} disabled />,
    },
    {
      title: '指标数',
      dataIndex: 'indicatorCount',
      key: 'indicatorCount',
      width: 80,
    },
    {
      title: '最后轮询',
      dataIndex: 'lastPolledAt',
      key: 'lastPolledAt',
      width: 150,
      render: (val: string) => val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_: unknown, record: ThreatFeed) => (
        <Button
          type="link"
          size="small"
          icon={<SyncOutlined />}
          onClick={() => handlePollFeed(record.id)}
        >
          轮询
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {/* 统计卡片 */}
      <Row gutter={16}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="总指标数" value={statistics?.totalIndicators || 0} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="活跃指标"
              value={statistics?.activeIndicators || 0}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title="Feed数量" value={statistics?.totalFeeds || 0} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title="活跃Feed"
              value={statistics?.activeFeeds || 0}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
      </Row>

      {/* IP查询 */}
      <Card title="IP信誉查询" size="small">
        <Space>
          <Input
            placeholder="输入IP地址"
            value={lookupIp}
            onChange={(e) => setLookupIp(e.target.value)}
            onPressEnter={handleLookup}
            style={{ width: 300 }}
            prefix={<SearchOutlined />}
          />
          <Button
            type="primary"
            loading={lookupLoading}
            onClick={handleLookup}
          >
            查询
          </Button>
        </Space>
        {lookupResult && (
          <Descriptions bordered size="small" style={{ marginTop: 16 }}>
            <Descriptions.Item label="IP">{lookupResult.ip}</Descriptions.Item>
            <Descriptions.Item label="发现">
              {lookupResult.found ? (
                <Tag color="red">命中</Tag>
              ) : (
                <Tag color="green">未命中</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="信誉分">
              {lookupResult.reputation ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="匹配指标数" span={3}>
              {lookupResult.indicators?.length || 0}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Card>

      <Tabs
        defaultActiveKey="indicators"
        items={[
          {
            key: 'indicators',
            label: '威胁指标',
            children: (
              <ProTable<ThreatIndicator>
                headerTitle="威胁指标管理"
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
                search={{ labelWidth: 'auto' }}
                toolBarRender={() => [
                  <Button key="refresh" icon={<ReloadOutlined />} onClick={() => actionRef.current?.reload()}>
                    刷新
                  </Button>,
                ]}
              />
            ),
          },
          {
            key: 'feeds',
            label: 'Feed管理',
            children: (
              <Card>
                <Space style={{ marginBottom: 16 }}>
                  <Button icon={<ReloadOutlined />} onClick={loadFeeds}>
                    刷新
                  </Button>
                </Space>
                <Table
                  columns={feedColumns}
                  dataSource={feeds}
                  rowKey="id"
                  loading={feedsLoading}
                  size="small"
                />
              </Card>
            ),
          },
        ]}
      />
    </Space>
  );
};

export default ThreatIntel;
