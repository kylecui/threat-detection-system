import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Row,
  Col,
  Tag,
  Space,
  Button,
  Skeleton,
  Table,
  Alert,
  Tooltip,
  Statistic,
  Grid,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ReloadOutlined,
  ArrowRightOutlined,
  WarningOutlined,
  ClockCircleOutlined,
  ThunderboltOutlined,
  DashboardOutlined,
} from '@ant-design/icons';
import type { PipelineHealthResponse, PipelineServiceStatus } from '@/types';
import systemService from '@/services/system';
import dayjs from 'dayjs';

const SERVICE_LABELS: Record<string, string> = {
  'data-ingestion': '数据摄取',
  'stream-processing': '流处理 (Flink)',
  'threat-assessment': '威胁评估',
  'alert-management': '告警管理',
  'customer-management': '客户管理',
  'threat-intelligence': '威胁情报',
  'ml-detection': 'ML检测',
  kafka: 'Kafka',
  redis: 'Redis',
  postgres: 'PostgreSQL',
};

const PIPELINE_STAGES: { key: string; label: string; services: string[] }[] = [
  { key: 'ingestion', label: '数据摄取', services: ['data-ingestion', 'kafka'] },
  { key: 'processing', label: '实时处理', services: ['stream-processing', 'ml-detection'] },
  { key: 'storage', label: '存储与评估', services: ['postgres', 'threat-assessment', 'alert-management'] },
];

const STATUS_MAP = {
  healthy: { color: '#52c41a', text: '健康', tagColor: 'success' },
  degraded: { color: '#faad14', text: '降级', tagColor: 'warning' },
  unhealthy: { color: '#ff4d4f', text: '异常', tagColor: 'error' },
} as const;

const AUTO_REFRESH_MS = 10_000;
const EVENT_STALE_THRESHOLD_MS = 5 * 60 * 1000;

const PipelineHealth = () => {
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.sm;
  const [loading, setLoading] = useState(true);
  const [health, setHealth] = useState<PipelineHealthResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [lastRefresh, setLastRefresh] = useState<string>('');

  const loadHealth = useCallback(async () => {
    try {
      const result = await systemService.getHealth();
      setHealth(result);
      setError(null);
      setLastRefresh(new Date().toLocaleTimeString('zh-CN'));
    } catch {
      setError('无法连接到系统健康接口');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadHealth();
    const interval = setInterval(loadHealth, AUTO_REFRESH_MS);
    return () => clearInterval(interval);
  }, [loadHealth]);

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
        void loadHealth();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [loadHealth]);

  const serviceEntries: { name: string; label: string; data: PipelineServiceStatus }[] =
    health
      ? Object.entries(health.services).map(([name, data]) => ({
          name,
          label: SERVICE_LABELS[name] || name,
          data,
        }))
      : [];

  const downServices = serviceEntries.filter((s) => s.data.status === 'DOWN');

  // ──────── 告警信息 ────────
  const warnings: string[] = [];
  if (downServices.length > 0) {
    warnings.push(`${downServices.map((s) => s.label).join('、')} 服务异常`);
  }

  const pipeline = health?.pipeline;
  const lastEventTime = pipeline?.lastEventReceived ? dayjs(pipeline.lastEventReceived) : null;
  const eventStale = lastEventTime
    ? Date.now() - lastEventTime.valueOf() > EVENT_STALE_THRESHOLD_MS
    : false;

  if (eventStale) {
    const ago = lastEventTime
      ? `${Math.max(1, Math.floor((Date.now() - lastEventTime.valueOf()) / 60000))} 分钟前`
      : '未知';
    warnings.push(`超过5分钟未接收到新事件 (最后: ${ago})，请检查Logstash连接`);
  }
  if (pipeline?.kafkaLag !== null && pipeline?.kafkaLag !== undefined && pipeline.kafkaLag > 100) {
    warnings.push(`Kafka消费者延迟较高: ${pipeline.kafkaLag} 条消息`);
  }
  if (pipeline?.flinkRunning === false) {
    warnings.push('Flink作业未运行，实时处理已停止');
  }

  const getStageStatus = (services: string[]): 'UP' | 'DOWN' | 'PARTIAL' => {
    if (!health) return 'DOWN';
    const statuses = services.map((s) => health.services[s]?.status).filter(Boolean);
    if (statuses.length === 0) return 'DOWN';
    if (statuses.every((s) => s === 'UP')) return 'UP';
    if (statuses.every((s) => s === 'DOWN')) return 'DOWN';
    return 'PARTIAL';
  };

  const stageColor = (status: 'UP' | 'DOWN' | 'PARTIAL') => {
    if (status === 'UP') return '#52c41a';
    if (status === 'PARTIAL') return '#faad14';
    return '#ff4d4f';
  };

  const columns = [
    {
      title: '服务名称',
      dataIndex: 'label',
      key: 'label',
      width: 200,
    },
    {
      title: '状态',
      dataIndex: ['data', 'status'],
      key: 'status',
      width: 100,
      render: (status: string) => (
        <Tag
          icon={status === 'UP' ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
          color={status === 'UP' ? 'success' : 'error'}
        >
          {status}
        </Tag>
      ),
    },
    {
      title: '延迟',
      dataIndex: ['data', 'latencyMs'],
      key: 'latencyMs',
      width: 120,
      render: (ms: number | undefined) => {
        if (ms === undefined || ms === null) return '-';
        const color = ms < 200 ? '#52c41a' : ms < 1000 ? '#faad14' : '#ff4d4f';
        return <span style={{ color }}>{ms}ms</span>;
      },
    },
    {
      title: '标识',
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => <code style={{ fontSize: 12 }}>{name}</code>,
    },
  ];

  if (loading && !health) {
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
        <Card>
          <Skeleton active paragraph={{ rows: 6 }} />
        </Card>
      </Space>
    );
  }

  const statusInfo = health ? STATUS_MAP[health.status] : STATUS_MAP.unhealthy;

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {/* Overall Status Banner */}
      <Card>
        <Row justify="space-between" align="middle">
          <Col>
            <Space size="large">
              <span style={{ fontWeight: 600, fontSize: 16 }}>管道健康监控</span>
              <Tag
                color={statusInfo.tagColor}
                style={{ fontSize: 14, padding: '4px 16px' }}
              >
                {statusInfo.text}
              </Tag>
              {lastRefresh && (
                <span style={{ color: '#999', fontSize: 12 }}>
                  最后刷新: {lastRefresh} (每10秒自动刷新)
                </span>
              )}
            </Space>
          </Col>
          <Col>
            <Button
              aria-label="刷新管道健康状态"
              icon={<ReloadOutlined />}
              onClick={loadHealth}
              loading={loading}
            >
              刷新
            </Button>
          </Col>
        </Row>
      </Card>

      {error && (
        <Alert type="error" showIcon message={error} />
      )}

      {warnings.map((w, i) => (
        <Alert
          key={i}
          type="warning"
          showIcon
          icon={<WarningOutlined />}
          message={w}
        />
      ))}

      {/* Pipeline Metrics */}
      {pipeline && (
        <Row gutter={16}>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="最后事件接收"
                value={
                  lastEventTime
                    ? lastEventTime.format('HH:mm:ss')
                    : '无数据'
                }
                prefix={<ClockCircleOutlined />}
                valueStyle={eventStale ? { color: '#ff4d4f' } : undefined}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="近1小时事件数"
                value={pipeline.eventsLastHour ?? '-'}
                prefix={<ThunderboltOutlined />}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="Kafka消费延迟"
                value={pipeline.kafkaLag ?? '-'}
                suffix="条"
                prefix={<DashboardOutlined />}
                valueStyle={
                  pipeline.kafkaLag !== null && pipeline.kafkaLag !== undefined && pipeline.kafkaLag > 100
                    ? { color: '#ff4d4f' }
                    : undefined
                }
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title="Flink状态"
                value={pipeline.flinkRunning === true ? '运行中' : pipeline.flinkRunning === false ? '已停止' : '未知'}
                prefix={pipeline.flinkRunning ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
                valueStyle={
                  pipeline.flinkRunning === false ? { color: '#ff4d4f' } : pipeline.flinkRunning === true ? { color: '#52c41a' } : undefined
                }
              />
            </Card>
          </Col>
        </Row>
      )}

      {/* Pipeline Flow */}
      <Row gutter={[16, 16]} align="middle">
        {PIPELINE_STAGES.map((stage, idx) => {
          const status = getStageStatus(stage.services);
          return (
            <Col
              key={stage.key}
              xs={24}
              sm={8}
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexDirection: isMobile ? 'column' : 'row',
                gap: 8,
              }}
            >
              <Card
                size="small"
                style={{
                  width: '100%',
                  maxWidth: 260,
                  borderTop: `3px solid ${stageColor(status)}`,
                  textAlign: 'center',
                }}
              >
                <div style={{ fontWeight: 600, marginBottom: 4 }}>{stage.label}</div>
                <div>
                  {stage.services.map((svc) => {
                    const svcStatus = health?.services[svc]?.status;
                    return (
                      <Tooltip key={svc} title={SERVICE_LABELS[svc] || svc}>
                        <Tag
                          color={svcStatus === 'UP' ? 'green' : svcStatus === 'DOWN' ? 'red' : 'default'}
                          style={{ margin: 2 }}
                        >
                          {SERVICE_LABELS[svc] || svc}
                        </Tag>
                      </Tooltip>
                    );
                  })}
                </div>
              </Card>
              {idx < PIPELINE_STAGES.length - 1 && (
                <ArrowRightOutlined
                  style={{
                    fontSize: 20,
                    color: '#999',
                    margin: isMobile ? '8px 0 0' : '0 8px',
                    transform: isMobile ? 'rotate(90deg)' : 'none',
                  }}
                  className="pipeline-stage-arrow"
                />
              )}
            </Col>
          );
        })}
      </Row>

      {/* Service Status Table */}
      <Card title="服务状态详情" variant="borderless">
        <Table
          columns={columns}
          dataSource={serviceEntries}
          rowKey="name"
          pagination={false}
          size="middle"
        />
      </Card>
    </Space>
  );
};

export default PipelineHealth;
