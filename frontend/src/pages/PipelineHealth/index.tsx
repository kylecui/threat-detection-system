import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Grid, Skeleton, Table } from 'antd';
import type { TableColumnsType } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useTranslation } from 'react-i18next';
import SemanticStatusTag, {
  type SemanticStatusTone,
} from '@/components/SemanticStatusTag';
import TableCellText from '@/components/TableCellText';
import systemService from '@/services/system';
import type { PipelineHealthResponse, PipelineServiceStatus } from '@/types';
import '../operations.css';

const AUTO_REFRESH_MS = 10_000;
const EVENT_STALE_THRESHOLD_MS = 5 * 60 * 1000;

type ServiceEntry = {
  name: string;
  label: string;
  data: PipelineServiceStatus;
};

type StageStatus = 'UP' | 'DOWN' | 'PARTIAL';

const PipelineHealth = () => {
  const { t } = useTranslation();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.sm;
  const [loading, setLoading] = useState(true);
  const [health, setHealth] = useState<PipelineHealthResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [lastRefresh, setLastRefresh] = useState('');

  const serviceLabels: Record<string, string> = {
    'data-ingestion': t('pipelineHealth.service.dataIngestion'),
    'stream-processing': t('pipelineHealth.service.streamProcessing'),
    'threat-assessment': t('pipelineHealth.service.threatAssessment'),
    'alert-management': t('pipelineHealth.service.alertManagement'),
    'customer-management': t('pipelineHealth.service.customerManagement'),
    'threat-intelligence': t('pipelineHealth.service.threatIntelligence'),
    'ml-detection': t('pipelineHealth.service.mlDetection'),
    tire: t('pipelineHealth.service.tire'),
    kafka: 'Kafka',
    redis: 'Redis',
    postgres: 'PostgreSQL',
  };

  const pipelineStages: { key: string; label: string; services: string[] }[] = [
    {
      key: 'ingestion',
      label: t('pipelineHealth.stage.ingestion'),
      services: ['data-ingestion', 'kafka'],
    },
    {
      key: 'processing',
      label: t('pipelineHealth.stage.processing'),
      services: ['stream-processing', 'ml-detection'],
    },
    {
      key: 'storage',
      label: t('pipelineHealth.stage.storage'),
      services: ['postgres', 'threat-assessment', 'alert-management', 'tire'],
    },
  ];

  const statusMap: Record<
    PipelineHealthResponse['status'],
    { text: string; tone: SemanticStatusTone }
  > = {
    healthy: {
      text: t('pipelineHealth.status.healthy'),
      tone: 'success',
    },
    degraded: {
      text: t('pipelineHealth.status.degraded'),
      tone: 'warning',
    },
    unhealthy: {
      text: t('pipelineHealth.status.unhealthy'),
      tone: 'danger',
    },
  };

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
  }, [t]);

  useEffect(() => {
    void loadHealth();
    const interval = window.setInterval(loadHealth, AUTO_REFRESH_MS);
    return () => window.clearInterval(interval);
  }, [loadHealth]);

  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (
        event.key.toLowerCase() !== 'r' ||
        event.ctrlKey ||
        event.metaKey ||
        event.altKey
      ) {
        return;
      }

      const target = event.target as HTMLElement;
      if (
        target.tagName === 'INPUT' ||
        target.tagName === 'TEXTAREA' ||
        target.isContentEditable
      ) {
        return;
      }
      void loadHealth();
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [loadHealth]);

  const serviceEntries: ServiceEntry[] = health
    ? Object.entries(health.services).map(([name, data]) => ({
        name,
        label: serviceLabels[name] || name,
        data,
      }))
    : [];

  const downServices = serviceEntries.filter(
    (service) => service.data.status === 'DOWN',
  );
  const pipeline = health?.pipeline;
  const lastEventTime = pipeline?.lastEventReceived
    ? dayjs(pipeline.lastEventReceived)
    : null;
  const eventStale = lastEventTime
    ? Date.now() - lastEventTime.valueOf() > EVENT_STALE_THRESHOLD_MS
    : false;
  const warnings: string[] = [];

  if (downServices.length > 0) {
    warnings.push(
      t('pipelineHealth.warningServicesAbnormal', {
        count: downServices.length,
        services: downServices.map((service) => service.label).join('、'),
      }),
    );
  }
  if (eventStale) {
    const ago = lastEventTime
      ? t('pipelineHealth.minutesAgo', {
          count: Math.max(
            1,
            Math.floor((Date.now() - lastEventTime.valueOf()) / 60_000),
          ),
        })
      : t('common.unknown');
    warnings.push(t('pipelineHealth.warningNoNewEvent', { ago }));
  }
  if (
    pipeline?.kafkaLag !== null &&
    pipeline?.kafkaLag !== undefined &&
    pipeline.kafkaLag > 100
  ) {
    warnings.push(
      t('pipelineHealth.warningKafkaLag', { count: pipeline.kafkaLag }),
    );
  }
  if (pipeline?.flinkRunning === false) {
    warnings.push(t('pipelineHealth.warningFlinkStopped'));
  }

  const getStageStatus = (services: string[]): StageStatus => {
    if (!health) return 'DOWN';
    const statuses = services
      .map((service) => health.services[service]?.status)
      .filter(Boolean);
    if (statuses.length === 0) return 'DOWN';
    if (statuses.every((status) => status === 'UP')) return 'UP';
    if (statuses.every((status) => status === 'DOWN')) return 'DOWN';
    return 'PARTIAL';
  };

  const stageTone = (status: StageStatus): SemanticStatusTone => {
    if (status === 'UP') return 'success';
    if (status === 'PARTIAL') return 'warning';
    return 'danger';
  };

  const columns: TableColumnsType<ServiceEntry> = [
    {
      title: t('pipelineHealth.serviceName'),
      dataIndex: 'label',
      key: 'label',
      width: 220,
      render: (label: string) => <TableCellText value={label} />,
    },
    {
      title: t('common.status'),
      dataIndex: ['data', 'status'],
      key: 'status',
      width: 120,
      render: (status: PipelineServiceStatus['status']) => (
        <SemanticStatusTag tone={status === 'UP' ? 'success' : 'danger'}>
          {status}
        </SemanticStatusTag>
      ),
    },
    {
      title: t('pipelineHealth.latency'),
      dataIndex: ['data', 'latencyMs'],
      key: 'latencyMs',
      width: 140,
      align: 'center',
      render: (latencyMs: number | undefined) => {
        if (latencyMs === undefined || latencyMs === null) {
          return (
            <span className="tds-operate-missing">
              {t('pipelineHealth.notCollected')}
            </span>
          );
        }
        const tone =
          latencyMs < 200 ? 'positive' : latencyMs < 1000 ? 'warning' : 'danger';
        return (
          <span className={`tds-operate-number tds-operate-number--${tone}`}>
            {latencyMs}
            <small>ms</small>
          </span>
        );
      },
    },
    {
      title: t('pipelineHealth.identifier'),
      dataIndex: 'name',
      key: 'name',
      width: 260,
      render: (name: string) => (
        <code className="tds-operate-code">{name}</code>
      ),
    },
  ];

  if (loading && !health) {
    return (
      <div
        className="tds-operate-page tds-pipeline-page"
        aria-busy="true"
        aria-label={t('pipelineHealth.title')}
      >
        <div className="tds-operate-statusbar">
          <Skeleton.Input active size="small" />
          <Skeleton.Button active />
        </div>
        <div className="tds-operate-metric-strip">
          {[1, 2, 3, 4].map((item) => (
            <div className="tds-operate-metric" key={item}>
              <Skeleton.Input active size="small" />
              <Skeleton.Input active />
            </div>
          ))}
        </div>
        <section className="tds-operate-panel tds-operate-loading-panel">
          <Skeleton active paragraph={{ rows: 7 }} />
        </section>
      </div>
    );
  }

  const statusInfo = health
    ? statusMap[health.status]
    : statusMap.unhealthy;

  return (
    <div className="tds-operate-page tds-pipeline-page">
      <div className="tds-operate-statusbar">
        <div className="tds-operate-statusbar__summary">
          <span>{t('pipelineHealth.title')}</span>
          <SemanticStatusTag tone={statusInfo.tone}>
            {statusInfo.text}
          </SemanticStatusTag>
          {warnings.length > 0 ? (
            <span className="tds-operate-statusbar__attention">
              {warnings.length}
            </span>
          ) : null}
        </div>
        <div className="tds-operate-statusbar__actions">
          {lastRefresh ? (
            <span className="tds-operate-freshness">
              {t('pipelineHealth.lastRefresh')}: {lastRefresh} ·{' '}
              {t('pipelineHealth.autoRefreshEvery10s')}
            </span>
          ) : null}
          <Button
            aria-label={t('pipelineHealth.refreshAria')}
            icon={<ReloadOutlined />}
            onClick={() => void loadHealth()}
            loading={loading}
          >
            {t('common.refresh')}
          </Button>
        </div>
      </div>

      {error ? (
        <Alert
          className="tds-operate-inline-state"
          type="error"
          showIcon
          message={error}
          action={
            <Button size="small" onClick={() => void loadHealth()}>
              {t('common.refresh')}
            </Button>
          }
        />
      ) : null}

      {warnings.length > 0 ? (
        <div className="tds-operate-alert-stack" aria-live="polite">
          {warnings.map((warning) => (
            <Alert key={warning} type="warning" showIcon message={warning} />
          ))}
        </div>
      ) : null}

      {pipeline ? (
        <section
          className="tds-operate-metric-strip"
          aria-label={t('pipelineHealth.title')}
        >
          <div className="tds-operate-metric">
            <span>{t('pipelineHealth.lastEventReceived')}</span>
            {lastEventTime ? (
              <strong className={eventStale ? 'tds-operate-value--danger' : ''}>
                {lastEventTime.format('HH:mm:ss')}
              </strong>
            ) : (
              <em>—</em>
            )}
          </div>
          <div className="tds-operate-metric">
            <span>{t('pipelineHealth.eventsLastHour')}</span>
            {pipeline.eventsLastHour !== null ? (
              <strong>{pipeline.eventsLastHour.toLocaleString()}</strong>
            ) : (
              <em>—</em>
            )}
          </div>
          <div className="tds-operate-metric">
            <span>{t('pipelineHealth.kafkaLag')}</span>
            {pipeline.kafkaLag !== null ? (
              <strong
                className={
                  pipeline.kafkaLag > 100 ? 'tds-operate-value--danger' : ''
                }
              >
                {pipeline.kafkaLag.toLocaleString()}
                <small>{t('pipelineHealth.messagesUnit')}</small>
              </strong>
            ) : (
              <em>—</em>
            )}
          </div>
          <div className="tds-operate-metric">
            <span>{t('pipelineHealth.flinkStatus')}</span>
            {pipeline.flinkRunning !== null ? (
              <strong
                className={
                  pipeline.flinkRunning ? '' : 'tds-operate-value--danger'
                }
              >
                {pipeline.flinkRunning
                  ? t('pipelineHealth.running')
                  : t('pipelineHealth.stopped')}
              </strong>
            ) : (
              <em>—</em>
            )}
          </div>
        </section>
      ) : null}

      <section className="tds-operate-panel">
        <div className="tds-operate-panel__header">
          <h2>{t('pipelineHealth.title')}</h2>
          <span>{serviceEntries.length}</span>
        </div>
        <div className="tds-pipeline-flow">
          {pipelineStages.map((stage, index) => {
            const status = getStageStatus(stage.services);
            return (
              <div className="tds-pipeline-flow__step" key={stage.key}>
                <article
                  className="tds-pipeline-stage"
                  data-status={status.toLowerCase()}
                >
                  <header>
                    <h3>{stage.label}</h3>
                    <SemanticStatusTag tone={stageTone(status)}>
                      {status}
                    </SemanticStatusTag>
                  </header>
                  <ul>
                    {stage.services.map((serviceName) => {
                      const serviceStatus = health?.services[serviceName]?.status;
                      return (
                        <li key={serviceName} data-status={serviceStatus?.toLowerCase()}>
                          <i aria-hidden="true" />
                          <span>{serviceLabels[serviceName] || serviceName}</span>
                        </li>
                      );
                    })}
                  </ul>
                </article>
                {index < pipelineStages.length - 1 ? (
                  <span className="tds-pipeline-flow__connector" aria-hidden="true" />
                ) : null}
              </div>
            );
          })}
        </div>
      </section>

      <section className="tds-operate-panel tds-operate-panel--table">
        <div className="tds-operate-panel__header">
          <h2>{t('pipelineHealth.serviceStatusDetails')}</h2>
          <span>{serviceEntries.length}</span>
        </div>
        {isMobile ? (
          <div className="tds-operate-mobile-records">
            {serviceEntries.map((service) => (
              <article className="tds-operate-mobile-record" key={service.name}>
                <div className="tds-operate-mobile-record__lead">
                  <strong>{service.label}</strong>
                  <SemanticStatusTag
                    tone={service.data.status === 'UP' ? 'success' : 'danger'}
                  >
                    {service.data.status}
                  </SemanticStatusTag>
                </div>
                <code className="tds-operate-code">{service.name}</code>
                <dl>
                  <div>
                    <dt>{t('pipelineHealth.latency')}</dt>
                    <dd>
                      {service.data.latencyMs == null
                        ? t('pipelineHealth.notCollected')
                        : `${service.data.latencyMs}ms`}
                    </dd>
                  </div>
                </dl>
              </article>
            ))}
          </div>
        ) : (
          <Table<ServiceEntry>
            className="tds-operate-table"
            columns={columns}
            dataSource={serviceEntries}
            rowKey="name"
            pagination={false}
            size="middle"
            tableLayout="fixed"
            scroll={{ x: 740 }}
          />
        )}
      </section>
    </div>
  );
};

export default PipelineHealth;
