import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert,
  Button,
  Grid,
  Popconfirm,
  Progress,
  Skeleton,
  Table,
  message,
} from 'antd';
import type { TableColumnsType } from 'antd';
import {
  ReloadOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import SemanticStatusTag from '@/components/SemanticStatusTag';
import TableCellText from '@/components/TableCellText';
import mlService from '@/services/ml';
import { formatDateTime } from '@/utils/formatDateTime';
import type {
  MlBufferStats,
  MlDataReadiness,
  MlDriftStatus,
  MlHealthStatus,
  MlModelInfo,
  MlShadowStats,
  MlTrainingStatus,
} from '@/types';
import '../operations.css';

const MlDetection = () => {
  const { t } = useTranslation();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [loading, setLoading] = useState(true);
  const [health, setHealth] = useState<MlHealthStatus | null>(null);
  const [models, setModels] = useState<MlModelInfo[]>([]);
  const [bufferStats, setBufferStats] = useState<MlBufferStats | null>(null);
  const [driftStatus, setDriftStatus] = useState<MlDriftStatus | null>(null);
  const [shadowStats, setShadowStats] = useState<MlShadowStats | null>(null);
  const [reloading, setReloading] = useState(false);
  const [training, setTraining] = useState(false);
  const [trainingStatus, setTrainingStatus] = useState<MlTrainingStatus | null>(
    null,
  );
  const [dataReadiness, setDataReadiness] = useState<MlDataReadiness | null>(
    null,
  );
  const [readinessLoading, setReadinessLoading] = useState(true);
  const mountedRef = useRef(true);
  const refreshInFlightRef = useRef(false);
  const trainingPollTimerRef = useRef<number | undefined>(undefined);

  const loadPrimary = useCallback(async (showPageLoading: boolean) => {
    if (showPageLoading) setLoading(true);
    try {
      const [nextHealth, nextModels] = await Promise.all([
        mlService.getHealth().catch(() => null),
        mlService.getModels().catch(() => []),
      ]);
      if (!mountedRef.current) return;
      setHealth(nextHealth);
      setModels(nextModels);
    } finally {
      if (mountedRef.current && showPageLoading) setLoading(false);
    }
  }, []);

  const loadSecondary = useCallback(async () => {
    if (mountedRef.current) setReadinessLoading(true);
    const [buffer, drift, shadow, nextTraining, readiness] =
      await Promise.all([
        mlService.getBufferStats().catch(() => null),
        mlService.getDriftStatus().catch(() => null),
        mlService.getShadowStats().catch(() => null),
        mlService.getTrainingStatus().catch(() => null),
        mlService.getDataReadiness().catch(() => null),
      ]);
    if (!mountedRef.current) return;
    setBufferStats(buffer);
    setDriftStatus(drift);
    setShadowStats(shadow);
    setTrainingStatus(nextTraining);
    setDataReadiness(readiness);
    if (nextTraining) setTraining(nextTraining.training);
    setReadinessLoading(false);
  }, []);

  const refreshAll = useCallback(
    async (showPageLoading = false) => {
      if (refreshInFlightRef.current) return;
      refreshInFlightRef.current = true;
      try {
        await Promise.allSettled([
          loadPrimary(showPageLoading),
          loadSecondary(),
        ]);
      } finally {
        refreshInFlightRef.current = false;
      }
    },
    [loadPrimary, loadSecondary],
  );

  useEffect(() => {
    mountedRef.current = true;
    let timer: number | undefined;
    let firstLoad = true;

    const poll = async () => {
      if (document.visibilityState !== 'hidden') {
        await refreshAll(firstLoad);
        firstLoad = false;
      }
      if (mountedRef.current) {
        timer = window.setTimeout(poll, 30_000);
      }
    };

    void poll();
    return () => {
      mountedRef.current = false;
      if (timer !== undefined) window.clearTimeout(timer);
      if (trainingPollTimerRef.current !== undefined) {
        window.clearTimeout(trainingPollTimerRef.current);
      }
    };
  }, [refreshAll]);

  const pollTrainingStatus = useCallback(async () => {
    try {
      const status = await mlService.getTrainingStatus();
      if (!mountedRef.current) return false;
      setTrainingStatus(status);
      if (!status.training) {
        setTraining(false);
        if (status.error) {
          message.error(
            t('mlDetection.trainingFailed', { error: status.error }),
          );
        } else {
          message.success(t('mlDetection.trainingComplete'));
          void refreshAll(false);
        }
        return false;
      }
      return true;
    } catch {
      return false;
    }
  }, [refreshAll, t]);

  const handleTrain = async () => {
    setTraining(true);
    try {
      const result = await mlService.triggerTraining();
      if (!mountedRef.current) return;
      if (result.status === 'already_running') {
        message.warning(t('mlDetection.trainingAlreadyRunning'));
      } else if (result.status === 'training_started') {
        message.success(
          t('mlDetection.trainingStarted', { tiers: result.tiers.join(', ') }),
        );
      } else {
        message.error(
          t('mlDetection.trainingStartFailed', { status: result.status }),
        );
        setTraining(false);
        return;
      }

      const poll = async () => {
        const shouldContinue = await pollTrainingStatus();
        if (shouldContinue && mountedRef.current) {
          trainingPollTimerRef.current = window.setTimeout(poll, 4_000);
        }
      };
      if (trainingPollTimerRef.current !== undefined) {
        window.clearTimeout(trainingPollTimerRef.current);
      }
      trainingPollTimerRef.current = window.setTimeout(poll, 4_000);
    } catch {
      if (!mountedRef.current) return;
      message.error(t('mlDetection.trainingRequestFailed'));
      setTraining(false);
    }
  };

  const handleReload = async () => {
    setReloading(true);
    try {
      const result = await mlService.reloadModels();
      if (result.status === 'ok') {
        const loadedCount = Object.values(result.modelsLoaded || {}).filter(
          Boolean,
        ).length;
        if (loadedCount > 0) {
          message.success(
            t('mlDetection.reloadSuccess', { count: loadedCount }),
          );
        } else {
          message.warning(t('mlDetection.reloadNoModelFound'));
        }
        void refreshAll(false);
      } else {
        message.error(
          t('mlDetection.reloadFailed', {
            error: result.error || t('common.unknownError'),
          }),
        );
      }
    } catch {
      message.error(t('mlDetection.reloadRequestFailed'));
    } finally {
      setReloading(false);
    }
  };

  const driftKnownKeys = new Set(['enabled', 'state', 'score', 'lastCheck']);
  const driftDiagnostics = driftStatus
    ? Object.entries(driftStatus).filter(([key]) => !driftKnownKeys.has(key))
    : [];
  const driftState = driftStatus?.state;
  const driftScore = driftStatus?.score;
  const driftLastCheck = driftStatus?.lastCheck;

  const availabilityTag = (available: boolean | undefined) => {
    if (available === undefined) {
      return <span className="tds-operate-missing">—</span>;
    }
    return (
      <SemanticStatusTag tone={available ? 'success' : 'danger'}>
        {available ? t('common.yes') : t('common.no')}
      </SemanticStatusTag>
    );
  };

  const modelColumns: TableColumnsType<MlModelInfo> = [
    {
      title: 'Tier',
      dataIndex: 'tier',
      key: 'tier',
      width: 88,
      render: (tier: number) => (
        <SemanticStatusTag tone="info">Tier {tier}</SemanticStatusTag>
      ),
    },
    {
      title: t('mlDetection.autoencoder'),
      dataIndex: 'available',
      key: 'available',
      width: 128,
      render: availabilityTag,
    },
    {
      title: 'BiGRU',
      dataIndex: 'bigruAvailable',
      key: 'bigruAvailable',
      width: 112,
      render: availabilityTag,
    },
    {
      title: t('mlDetection.threshold'),
      dataIndex: 'threshold',
      key: 'threshold',
      width: 112,
      align: 'center',
      render: (value: number) => (
        <span className="tds-operate-number">{value?.toFixed(4) ?? '—'}</span>
      ),
    },
    {
      title: t('mlDetection.optimalAlpha'),
      dataIndex: 'optimalAlpha',
      key: 'optimalAlpha',
      width: 120,
      align: 'center',
      render: (value: number | undefined) => (
        <span className="tds-operate-number">
          {value === undefined ? '—' : value.toFixed(2)}
        </span>
      ),
    },
    {
      title: t('mlDetection.modelPath'),
      dataIndex: 'modelPath',
      key: 'modelPath',
      width: 320,
      render: (path: string) => (
        <TableCellText
          value={path}
          fallback="—"
          className="tds-operate-code"
        />
      ),
    },
  ];

  if (loading) {
    return (
      <div
        className="tds-operate-page tds-ml-page"
        aria-busy="true"
        aria-label={t('mlDetection.loadingStatus')}
      >
        <div className="tds-ml-command-grid">
          <div className="tds-operate-metric-strip tds-ml-status-strip">
            {[1, 2, 3].map((item) => (
              <div className="tds-operate-metric" key={item}>
                <Skeleton.Input active size="small" />
                <Skeleton.Input active />
              </div>
            ))}
          </div>
          <div className="tds-ml-actions">
            <Skeleton.Button active />
            <Skeleton.Button active />
          </div>
        </div>
        <section className="tds-operate-panel tds-operate-loading-panel">
          <Skeleton active paragraph={{ rows: 6 }} />
        </section>
      </div>
    );
  }

  const healthOk = health?.status === 'ok';

  return (
    <div className="tds-operate-page tds-ml-page">
      <div className="tds-ml-command-grid">
        <section
          className="tds-operate-metric-strip tds-ml-status-strip"
          aria-label={t('mlDetection.serviceStatus')}
        >
          <div className="tds-operate-metric" data-tone={healthOk ? 'success' : 'danger'}>
            <span>{t('mlDetection.serviceStatus')}</span>
            <strong>
              {healthOk
                ? t('mlDetection.normal')
                : t('mlDetection.abnormal')}
            </strong>
          </div>
          <div
            className="tds-operate-metric"
            data-tone={health?.modelLoaded ? 'success' : 'danger'}
          >
            <span>{t('mlDetection.modelLoaded')}</span>
            <strong>
              {health?.modelLoaded
                ? t('mlDetection.loaded')
                : t('mlDetection.notLoaded')}
            </strong>
          </div>
          <div
            className="tds-operate-metric"
            data-tone={health?.kafkaConnected ? 'success' : 'danger'}
          >
            <span>{t('mlDetection.kafkaConnection')}</span>
            <strong>
              {health?.kafkaConnected
                ? t('mlDetection.connected')
                : t('mlDetection.notConnected')}
            </strong>
          </div>
        </section>

        <div className="tds-ml-actions">
          <Popconfirm
            title={t('mlDetection.confirmTrainTitle')}
            description={t('mlDetection.confirmTrainDescription')}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
            onConfirm={() => void handleTrain()}
            disabled={training}
          >
            <Button
              type="primary"
              icon={<ThunderboltOutlined />}
              loading={training}
            >
              {t('mlDetection.trainModel')}
            </Button>
          </Popconfirm>
          <Popconfirm
            title={t('mlDetection.confirmReloadTitle')}
            description={t('mlDetection.confirmReloadDescription')}
            okText={t('common.confirm')}
            cancelText={t('common.cancel')}
            onConfirm={() => void handleReload()}
            disabled={reloading}
          >
            <Button icon={<ReloadOutlined />} loading={reloading}>
              {t('mlDetection.reloadModel')}
            </Button>
          </Popconfirm>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => void refreshAll(false)}
          >
            {t('common.refresh')}
          </Button>
        </div>
      </div>

      {!health ? (
        <Alert
          className="tds-operate-inline-state"
          type="warning"
          message={t('mlDetection.serviceUnavailable')}
          description={t('mlDetection.serviceUnavailableDesc')}
          showIcon
          action={
            <Button size="small" onClick={() => void refreshAll(false)}>
              {t('common.refresh')}
            </Button>
          }
        />
      ) : null}

      {!health?.modelLoaded && !training ? (
        <Alert
          className="tds-operate-inline-state"
          type="info"
          message={t('mlDetection.noTrainedModelFound')}
          description={t('mlDetection.trainPrompt')}
          showIcon
        />
      ) : null}

      {trainingStatus?.training ? (
        <section className="tds-operate-panel">
          <div className="tds-operate-panel__header">
            <h2>{t('mlDetection.trainingProgress')}</h2>
            <SemanticStatusTag tone="info">
              {t('mlDetection.training')}
            </SemanticStatusTag>
          </div>
          <div className="tds-operate-panel__body tds-ml-training-progress">
            <Progress
              percent={Math.min(
                95,
                Math.round(
                  (((trainingStatus.completedTiers || 0) +
                    (trainingStatus.currentTier ? 0.5 : 0)) /
                    Math.max(1, trainingStatus.tiers.length)) *
                    100,
                ),
              )}
              status="active"
              format={(percent) => `${percent}%`}
            />
            <dl className="tds-operate-definition-grid">
              <div>
                <dt>{t('mlDetection.trainingTiers')}</dt>
                <dd>
                  {trainingStatus.tiers.map((tier) => (
                    <SemanticStatusTag key={tier} tone="info">
                      Tier {tier}
                    </SemanticStatusTag>
                  ))}
                </dd>
              </div>
              <div>
                <dt>{t('mlDetection.startedAt')}</dt>
                <dd>{trainingStatus.startedAt || '—'}</dd>
              </div>
              <div>
                <dt>{t('mlDetection.elapsed')}</dt>
                <dd>
                  {trainingStatus.elapsedSeconds == null
                    ? '—'
                    : `${Math.round(trainingStatus.elapsedSeconds)}s`}
                </dd>
              </div>
            </dl>
          </div>
        </section>
      ) : null}

      {trainingStatus &&
      !trainingStatus.training &&
      trainingStatus.completedAt ? (
        <section className="tds-operate-panel">
          <div className="tds-operate-panel__header">
            <h2>{t('mlDetection.lastTrainingResult')}</h2>
            <SemanticStatusTag
              tone={trainingStatus.error ? 'danger' : 'success'}
            >
              {trainingStatus.error
                ? t('common.failed')
                : t('common.success')}
            </SemanticStatusTag>
          </div>
          <div className="tds-operate-panel__body">
            <dl className="tds-operate-definition-grid">
              <div>
                <dt>{t('mlDetection.completedAt')}</dt>
                <dd>{trainingStatus.completedAt}</dd>
              </div>
              <div>
                <dt>{t('mlDetection.elapsed')}</dt>
                <dd>
                  {trainingStatus.elapsedSeconds == null
                    ? '—'
                    : `${Math.round(trainingStatus.elapsedSeconds)}s`}
                </dd>
              </div>
              {trainingStatus.error ? (
                <div className="tds-operate-definition-grid__wide">
                  <dt>{t('common.error')}</dt>
                  <dd className="tds-operate-value--danger">
                    {trainingStatus.error}
                  </dd>
                </div>
              ) : null}
            </dl>
          </div>
        </section>
      ) : null}

      {dataReadiness || readinessLoading ? (
        <section className="tds-operate-panel">
          <div className="tds-operate-panel__header">
            <h2>{t('mlDetection.trainingDataReadiness')}</h2>
            {dataReadiness ? (
              <SemanticStatusTag
                tone={dataReadiness.ready ? 'success' : 'warning'}
              >
                {dataReadiness.ready
                  ? t('mlDetection.ready')
                  : t('mlDetection.insufficient')}
              </SemanticStatusTag>
            ) : null}
          </div>
          <div className="tds-operate-panel__body">
            {readinessLoading && !dataReadiness ? (
              <Skeleton active paragraph={{ rows: 2 }} />
            ) : null}
            {dataReadiness ? (
              <>
                {dataReadiness.error ? (
                  <Alert
                    className="tds-operate-inline-state"
                    type="warning"
                    showIcon
                    message={dataReadiness.error}
                  />
                ) : null}
                <div className="tds-ml-readiness-grid">
                  {Object.entries(dataReadiness.sampleCounts || {}).map(
                    ([tier, count]) => {
                      const enough =
                        count >= (dataReadiness.minimumRequired?.bigru ?? 20);
                      return (
                        <div
                          className="tds-ml-readiness-item"
                          data-ready={enough}
                          key={tier}
                        >
                          <span>
                            {t('mlDetection.tierSampleCount', { tier })}
                          </span>
                          <strong>{count.toLocaleString()}</strong>
                        </div>
                      );
                    },
                  )}
                  <div className="tds-ml-readiness-item">
                    <span>{t('mlDetection.minRequiredAutoencoder')}</span>
                    <strong>
                      {dataReadiness.minimumRequired?.autoencoder?.toLocaleString() ??
                        '—'}
                    </strong>
                  </div>
                  <div className="tds-ml-readiness-item">
                    <span>{t('mlDetection.minRequiredBigru')}</span>
                    <strong>
                      {dataReadiness.minimumRequired?.bigru?.toLocaleString() ??
                        '—'}
                    </strong>
                  </div>
                  <div
                    className="tds-ml-readiness-item"
                    data-ready={dataReadiness.ready}
                  >
                    <span>{t('mlDetection.dataReady')}</span>
                    <strong>
                      {dataReadiness.ready
                        ? t('mlDetection.ready')
                        : t('mlDetection.insufficient')}
                    </strong>
                  </div>
                </div>
              </>
            ) : null}
          </div>
        </section>
      ) : null}

      <section className="tds-operate-panel tds-operate-panel--table">
        <div className="tds-operate-panel__header">
          <h2>{t('mlDetection.modelStatus')}</h2>
          <span>{models.length}</span>
        </div>
        {isMobile ? (
          <div className="tds-operate-mobile-records">
            {models.map((model) => (
              <article className="tds-operate-mobile-record" key={model.tier}>
                <div className="tds-operate-mobile-record__lead">
                  <strong>Tier {model.tier}</strong>
                  {availabilityTag(model.available)}
                </div>
                <dl>
                  <div>
                    <dt>{t('mlDetection.autoencoder')}</dt>
                    <dd>{model.available ? t('common.yes') : t('common.no')}</dd>
                  </div>
                  <div>
                    <dt>BiGRU</dt>
                    <dd>
                      {model.bigruAvailable === undefined
                        ? '—'
                        : model.bigruAvailable
                          ? t('common.yes')
                          : t('common.no')}
                    </dd>
                  </div>
                  <div>
                    <dt>{t('mlDetection.threshold')}</dt>
                    <dd>{model.threshold?.toFixed(4) ?? '—'}</dd>
                  </div>
                  <div>
                    <dt>{t('mlDetection.optimalAlpha')}</dt>
                    <dd>{model.optimalAlpha?.toFixed(2) ?? '—'}</dd>
                  </div>
                  <div className="tds-operate-mobile-record__wide">
                    <dt>{t('mlDetection.modelPath')}</dt>
                    <dd>
                      <code className="tds-operate-code">
                        {model.modelPath || '—'}
                      </code>
                    </dd>
                  </div>
                </dl>
              </article>
            ))}
          </div>
        ) : (
          <Table<MlModelInfo>
            className="tds-operate-table"
            columns={modelColumns}
            dataSource={models}
            rowKey="tier"
            pagination={false}
            size="small"
            tableLayout="fixed"
            scroll={{ x: 880 }}
          />
        )}
      </section>

      <div className="tds-ml-runtime-grid">
        <section className="tds-operate-panel">
          <div className="tds-operate-panel__header">
            <h2>{t('mlDetection.sequenceBuffer')}</h2>
            <SemanticStatusTag
              tone={bufferStats?.enabled ? 'success' : 'neutral'}
            >
              {bufferStats?.enabled ? t('common.enabled') : t('common.disabled')}
            </SemanticStatusTag>
          </div>
          <div className="tds-operate-panel__body">
            <dl className="tds-operate-definition-list">
              <div>
                <dt>{t('mlDetection.totalKeys')}</dt>
                <dd>{bufferStats?.totalKeys ?? '—'}</dd>
              </div>
              <div>
                <dt>{t('mlDetection.totalWindows')}</dt>
                <dd>{bufferStats?.totalWindows ?? '—'}</dd>
              </div>
            </dl>
          </div>
        </section>

        <section className="tds-operate-panel">
          <div className="tds-operate-panel__header">
            <h2>{t('mlDetection.driftDetection')}</h2>
            <SemanticStatusTag
              tone={driftStatus?.enabled ? 'success' : 'neutral'}
            >
              {driftStatus?.enabled ? t('common.enabled') : t('common.disabled')}
            </SemanticStatusTag>
          </div>
          <div className="tds-operate-panel__body">
            <dl className="tds-operate-definition-list">
              {driftStatus?.enabled && driftState !== undefined ? (
                <div>
                  <dt>{t('mlDetection.driftState')}</dt>
                  <dd>
                    {typeof driftState === 'string'
                      ? t(`mlDetection.driftStates.${driftState}`, {
                          defaultValue: driftState,
                        })
                      : String(driftState)}
                  </dd>
                </div>
              ) : null}
              {driftStatus?.enabled && driftScore !== undefined ? (
                <div>
                  <dt>{t('mlDetection.driftScore')}</dt>
                  <dd>
                    {typeof driftScore === 'number'
                      ? `${(driftScore * 100).toFixed(1)}%`
                      : String(driftScore)}
                  </dd>
                </div>
              ) : null}
              {driftStatus?.enabled && driftLastCheck !== undefined ? (
                <div>
                  <dt>{t('mlDetection.driftLastCheck')}</dt>
                  <dd>
                    {typeof driftLastCheck === 'string'
                      ? formatDateTime(driftLastCheck)
                      : String(driftLastCheck)}
                  </dd>
                </div>
              ) : null}
            </dl>
            {driftStatus?.enabled && driftDiagnostics.length > 0 ? (
              <details className="tds-ml-diagnostics">
                <summary>
                  {t('mlDetection.additionalDiagnostics', {
                    count: driftDiagnostics.length,
                  })}
                </summary>
                <dl className="tds-operate-definition-list">
                  {driftDiagnostics.map(([key, value]) => (
                    <div key={key}>
                      <dt>{key}</dt>
                      <dd>
                        {typeof value === 'object'
                          ? JSON.stringify(value)
                          : String(value)}
                      </dd>
                    </div>
                  ))}
                </dl>
              </details>
            ) : null}
          </div>
        </section>

        <section className="tds-operate-panel">
          <div className="tds-operate-panel__header">
            <h2>{t('mlDetection.shadowScoring')}</h2>
            <SemanticStatusTag
              tone={shadowStats?.enabled ? 'success' : 'neutral'}
            >
              {shadowStats?.enabled ? t('common.enabled') : t('common.disabled')}
            </SemanticStatusTag>
          </div>
          <div className="tds-operate-panel__body">
            <dl className="tds-operate-definition-list">
              <div>
                <dt>{t('mlDetection.challengerLoaded')}</dt>
                <dd>
                  {shadowStats?.challengerLoaded
                    ? t('common.yes')
                    : t('common.no')}
                </dd>
              </div>
              <div>
                <dt>{t('mlDetection.totalComparisons')}</dt>
                <dd>{shadowStats?.totalComparisons ?? 0}</dd>
              </div>
              <div>
                <dt>{t('mlDetection.challengerDir')}</dt>
                <dd>
                  <code className="tds-operate-code">
                    {shadowStats?.challengerDir || '—'}
                  </code>
                </dd>
              </div>
            </dl>
          </div>
        </section>
      </div>
    </div>
  );
};

export default MlDetection;
