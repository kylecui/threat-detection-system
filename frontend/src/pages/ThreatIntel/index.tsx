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
import { useTranslation } from 'react-i18next';
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
  const { t } = useTranslation();
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
      message.error(t('threatIntel.messageLoadFeedsFailed'));
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
      message.error(t('threatIntel.messageLookupFailed'));
    } finally {
      setLookupLoading(false);
    }
  };

  /** 手动触发Feed轮询 */
  const handlePollFeed = async (id: number) => {
    try {
      const result = await intelService.pollFeed(id);
      message.success(t('threatIntel.messagePollComplete', { count: result.polledCount || 0 }));
      loadFeeds();
    } catch {
      message.error(t('threatIntel.messagePollFailed'));
    }
  };

  /** 删除指标 */
  const handleDeleteIndicator = async (id: number) => {
    try {
      await intelService.deleteIndicator(id);
      message.success(t('common.deleteSuccess'));
      actionRef.current?.reload();
    } catch {
      message.error(t('common.deleteFailed'));
    }
  };

  /** 添加目击记录 */
  const handleSighting = async (id: number) => {
    try {
      await intelService.addSighting(id);
      message.success(t('threatIntel.messageSightingAdded'));
      actionRef.current?.reload();
    } catch {
      message.error(t('common.operationFailed'));
    }
  };

  const indicatorColumns: ProColumns<ThreatIndicator>[] = [
    { title: t('common.id'), dataIndex: 'id', width: 60, search: false },
    {
      title: t('threatIntel.iocValue'),
      dataIndex: 'iocValue',
      copyable: true,
      width: 200,
    },
    {
      title: t('threatIntel.iocType'),
      dataIndex: 'iocType',
      width: 100,
      valueType: 'select',
      valueEnum: {
        IPV4: { text: 'IPv4' },
        IPV6: { text: 'IPv6' },
        DOMAIN: { text: t('threatIntel.typeDomain') },
        URL: { text: 'URL' },
        MD5: { text: 'MD5' },
        SHA256: { text: 'SHA256' },
      },
    },
    {
      title: t('common.severity'),
      dataIndex: 'severity',
      width: 90,
      valueType: 'select',
      valueEnum: {
         CRITICAL: { text: t('common.severityCritical') },
         HIGH: { text: t('common.severityHigh') },
         MEDIUM: { text: t('common.severityMedium') },
         LOW: { text: t('common.severityLow') },
         INFO: { text: t('common.severityInfo') },
      },
      render: (_, record) => (
        <Tag color={severityColorMap[record.severity]}>{record.severity}</Tag>
      ),
    },
    {
      title: t('threatIntel.confidence'),
      dataIndex: 'confidence',
      width: 80,
      search: false,
      render: (val) => `${val}%`,
    },
    {
      title: t('common.source'),
      dataIndex: 'sourceName',
      width: 120,
      search: false,
    },
    {
      title: t('threatIntel.sightingCount'),
      dataIndex: 'sightingCount',
      width: 90,
      search: false,
    },
    {
      title: t('threatIntel.lastSeen'),
      dataIndex: 'lastSeenAt',
      width: 150,
      search: false,
      render: (_, record) =>
        record.lastSeenAt ? dayjs(record.lastSeenAt).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: t('common.actions'),
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
            {t('threatIntel.sighting')}
          </Button>
          <Popconfirm
            title={t('common.confirmDelete')}
            onConfirm={() => handleDeleteIndicator(record.id)}
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const feedColumns = [
    { title: t('common.id'), dataIndex: 'id', key: 'id', width: 60 },
    { title: t('common.name'), dataIndex: 'name', key: 'name', width: 150 },
    { title: 'URL', dataIndex: 'url', key: 'url', ellipsis: true },
    { title: t('common.type'), dataIndex: 'feedType', key: 'feedType', width: 100 },
    {
      title: t('common.enabled'),
      dataIndex: 'enabled',
      key: 'enabled',
      width: 80,
      render: (enabled: boolean) => <Switch checked={enabled} disabled />,
    },
    {
      title: t('threatIntel.indicatorCount'),
      dataIndex: 'indicatorCount',
      key: 'indicatorCount',
      width: 80,
    },
    {
      title: t('threatIntel.lastPolled'),
      dataIndex: 'lastPolledAt',
      key: 'lastPolledAt',
      width: 150,
      render: (val: string) => val ? dayjs(val).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: t('common.actions'),
      key: 'actions',
      width: 100,
      render: (_: unknown, record: ThreatFeed) => (
        <Button
          type="link"
          size="small"
          icon={<SyncOutlined />}
          onClick={() => handlePollFeed(record.id)}
        >
          {t('threatIntel.poll')}
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
            <Statistic title={t('threatIntel.totalIndicators')} value={statistics?.totalIndicators || 0} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
               title={t('threatIntel.activeIndicators')}
              value={statistics?.activeIndicators || 0}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic title={t('threatIntel.feedCount')} value={statistics?.totalFeeds || 0} />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
               title={t('threatIntel.activeFeeds')}
              value={statistics?.activeFeeds || 0}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
      </Row>

      {/* IP查询 */}
      <Card title={t('threatIntel.ipLookup')} size="small">
        <Space>
          <Input
            placeholder={t('threatIntel.inputIpAddress')}
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
            {t('common.search')}
          </Button>
        </Space>
        {lookupResult && (
          <Descriptions bordered size="small" style={{ marginTop: 16 }}>
            <Descriptions.Item label="IP">{lookupResult.ip}</Descriptions.Item>
            <Descriptions.Item label={t('threatIntel.found')}>
              {lookupResult.found ? (
                <Tag color="red">{t('threatIntel.hit')}</Tag>
              ) : (
                <Tag color="green">{t('threatIntel.notHit')}</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label={t('threatIntel.reputation')}>
              {lookupResult.reputation ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('threatIntel.matchedIndicators')} span={3}>
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
            label: t('threatIntel.threatIndicators'),
            children: (
              <ProTable<ThreatIndicator>
                headerTitle={t('threatIntel.threatIndicatorManagement')}
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
                    {t('common.refresh')}
                  </Button>,
                ]}
              />
            ),
          },
          {
            key: 'feeds',
            label: t('threatIntel.feedManagement'),
            children: (
              <Card>
                <Space style={{ marginBottom: 16 }}>
                  <Button icon={<ReloadOutlined />} onClick={loadFeeds}>
                    {t('common.refresh')}
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
