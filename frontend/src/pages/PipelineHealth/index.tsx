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
import { useTranslation } from 'react-i18next';

const AUTO_REFRESH_MS = 10_000;
const EVENT_STALE_THRESHOLD_MS = 5 * 60 * 1000;

const PipelineHealth = () => {
  const { t } = useTranslation();
  const SERVICE_LABELS: Record<string, string> = {
    'data-ingestion': t('pipelineHealth.service.dataIngestion'),
    'stream-processing': t('pipelineHealth.service.streamProcessing'),
    'threat-assessment': t('pipelineHealth.service.threatAssessment'),
    'alert-management': t('pipelineHealth.service.alertManagement'),
    'customer-management': t('pipelineHealth.service.customerManagement'),
    'threat-intelligence': t('pipelineHealth.service.threatIntelligence'),
    'ml-detection': t('pipelineHealth.service.mlDetection'),
    kafka: 'Kafka',
    redis: 'Redis',
    postgres: 'PostgreSQL',
  };

  const PIPELINE_STAGES: { key: string; label: string; services: string[] }[] = [
    { key: 'ingestion', label: t('pipelineHealth.stage.ingestion'), services: ['data-ingestion', 'kafka'] },
    { key: 'processing', label: t('pipelineHealth.stage.processing'), services: ['stream-processing', 'ml-detection'] },
    { key: 'storage', label: t('pipelineHealth.stage.storage'), services: ['postgres', 'threat-assessment', 'alert-management'] },
  ];

  const STATUS_MAP = {
    healthy: { color: '#52c41a', text: t('pipelineHealth.status.healthy'), tagColor: 'success' },
    degraded: { color: '#faad14', text: t('pipelineHealth.status.degraded'), tagColor: 'warning' },
    unhealthy: { color: '#ff4d4f', text: t('pipelineHealth.status.unhealthy'), tagColor: 'error' },
  } as const;

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
      setError(t('pipelineHealth.errorCannotConnect'));
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
    warnings.push(t('pipelineHealth.warningServicesAbnormal', { services: downServices.map((s) => s.label).join('、') }));
  }

  const pipeline = health?.pipeline;
  const lastEventTime = pipeline?.lastEventReceived ? dayjs(pipeline.lastEventReceived) : null;
  const eventStale = lastEventTime
    ? Date.now() - lastEventTime.valueOf() > EVENT_STALE_THRESHOLD_MS
    : false;

  if (eventStale) {
    const ago = lastEventTime
      ? t('pipelineHealth.minutesAgo', { count: Math.max(1, Math.floor((Date.now() - lastEventTime.valueOf()) / 60000)) })
      : t('common.unknown');
    warnings.push(t('pipelineHealth.warningNoNewEvent', { ago }));
  }
  if (pipeline?.kafkaLag !== null && pipeline?.kafkaLag !== undefined && pipeline.kafkaLag > 100) {
    warnings.push(t('pipelineHealth.warningKafkaLag', { lag: pipeline.kafkaLag }));
  }
  if (pipeline?.flinkRunning === false) {
    warnings.push(t('pipelineHealth.warningFlinkStopped'));
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
      title: t('pipelineHealth.serviceName'),
      dataIndex: 'label',
      key: 'label',
      width: 200,
    },
    {
      title: t('common.status'),
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
      title: t('pipelineHealth.latency'),
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
      title: t('pipelineHealth.identifier'),
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
              <span style={{ fontWeight: 600, fontSize: 16 }}>{t('pipelineHealth.title')}</span>
              <Tag
                color={statusInfo.tagColor}
                style={{ fontSize: 14, padding: '4px 16px' }}
              >
                {statusInfo.text}
              </Tag>
              {lastRefresh && (
                <span style={{ color: '#999', fontSize: 12 }}>
                  {t('pipelineHealth.lastRefresh')}: {lastRefresh} ({t('pipelineHealth.autoRefreshEvery10s')})
                </span>
              )}
            </Space>
          </Col>
          <Col>
            <Button
              aria-label={t('pipelineHealth.refreshAria')}
              icon={<ReloadOutlined />}
              onClick={loadHealth}
              loading={loading}
            >
              {t('common.refresh')}
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
                title={t('pipelineHealth.lastEventReceived')}
                value={
                  lastEventTime
                    ? lastEventTime.format('HH:mm:ss')
                    : t('common.noData')
                }
                prefix={<ClockCircleOutlined />}
                valueStyle={eventStale ? { color: '#ff4d4f' } : undefined}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title={t('pipelineHealth.eventsLastHour')}
                value={pipeline.eventsLastHour ?? '-'}
                prefix={<ThunderboltOutlined />}
              />
            </Card>
          </Col>
          <Col xs={24} sm={12} lg={6}>
            <Card>
              <Statistic
                title={t('pipelineHealth.kafkaLag')}
                value={pipeline.kafkaLag ?? '-'}
                suffix={t('pipelineHealth.messagesUnit')}
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
                title={t('pipelineHealth.flinkStatus')}
                value={pipeline.flinkRunning === true ? t('pipelineHealth.running') : pipeline.flinkRunning === false ? t('pipelineHealth.stopped') : t('common.unknown')}
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
      <Card title={t('pipelineHealth.serviceStatusDetails')} variant="borderless">
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
