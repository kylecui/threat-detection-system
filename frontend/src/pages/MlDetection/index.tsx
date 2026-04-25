import { useState, useEffect, useCallback } from 'react';
import {
  Card,
  Row,
  Col,
  Statistic,
  Tag,
  Button,
  Space,
  Descriptions,
  Table,
  Alert,
  Spin,
  message,
  Progress,
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ReloadOutlined,
  ExperimentOutlined,
  DatabaseOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons';
import type {
  MlHealthStatus,
  MlModelInfo,
  MlBufferStats,
  MlDriftStatus,
  MlShadowStats,
  MlTrainingStatus,
  MlDataReadiness,
} from '@/types';
import { useTranslation } from 'react-i18next';
import mlService from '@/services/ml';

/**
 * ML检测监控页面
 */
const MlDetection = () => {
  const { t } = useTranslation();
  const [loading, setLoading] = useState(true);
  const [health, setHealth] = useState<MlHealthStatus | null>(null);
  const [models, setModels] = useState<MlModelInfo[]>([]);
  const [bufferStats, setBufferStats] = useState<MlBufferStats | null>(null);
  const [driftStatus, setDriftStatus] = useState<MlDriftStatus | null>(null);
  const [shadowStats, setShadowStats] = useState<MlShadowStats | null>(null);
  const [reloading, setReloading] = useState(false);
  const [training, setTraining] = useState(false);
  const [trainingStatus, setTrainingStatus] = useState<MlTrainingStatus | null>(null);
  const [dataReadiness, setDataReadiness] = useState<MlDataReadiness | null>(null);

  const loadAll = async () => {
    setLoading(true);
    try {
      const [h, m, b, d, s, ts, dr] = await Promise.all([
        mlService.getHealth().catch(() => null),
        mlService.getModels().catch(() => []),
        mlService.getBufferStats().catch(() => null),
        mlService.getDriftStatus().catch(() => null),
        mlService.getShadowStats().catch(() => null),
        mlService.getTrainingStatus().catch(() => null),
        mlService.getDataReadiness().catch(() => null),
      ]);
      setHealth(h);
      setModels(m);
      setBufferStats(b);
      setDriftStatus(d);
      setShadowStats(s);
      setTrainingStatus(ts);
      setDataReadiness(dr);
      if (ts?.training) {
        setTraining(true);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAll();
    const interval = setInterval(loadAll, 30000);
    return () => clearInterval(interval);
  }, []);

  /** 训练状态轮询 */
  const pollTrainingStatus = useCallback(async () => {
    try {
      const status = await mlService.getTrainingStatus();
      setTrainingStatus(status);
      if (!status.training) {
        setTraining(false);
        if (status.error) {
          message.error(t('mlDetection.trainingFailed', { error: status.error }));
        } else {
          message.success(t('mlDetection.trainingComplete'));
          loadAll();
        }
        return false;
      }
      return true;
    } catch {
      return false;
    }
  }, []);

  /** 触发训练 */
  const handleTrain = async () => {
    setTraining(true);
    try {
      const result = await mlService.triggerTraining();
      if (result.status === 'already_running') {
        message.warning(t('mlDetection.trainingAlreadyRunning'));
      } else if (result.status === 'training_started') {
        message.success(t('mlDetection.trainingStarted', { tiers: result.tiers.join(', ') }));
      } else {
        message.error(t('mlDetection.trainingStartFailed', { status: result.status }));
        setTraining(false);
        return;
      }
      const poll = async () => {
        const shouldContinue = await pollTrainingStatus();
        if (shouldContinue) {
          setTimeout(poll, 4000);
        }
      };
      setTimeout(poll, 4000);
    } catch {
      message.error(t('mlDetection.trainingRequestFailed'));
      setTraining(false);
    }
  };

  /** 重载模型 */
  const handleReload = async () => {
    setReloading(true);
    try {
      const result = await mlService.reloadModels();
      if (result.status === 'ok') {
        const loadedCount = Object.values(result.modelsLoaded || {}).filter(Boolean).length;
        if (loadedCount > 0) {
          message.success(t('mlDetection.reloadSuccess', { count: loadedCount }));
        } else {
          message.warning(t('mlDetection.reloadNoModelFound'));
        }
        loadAll();
      } else {
        message.error(t('mlDetection.reloadFailed', { error: result.error || t('common.unknownError') }));
      }
    } catch {
      message.error(t('mlDetection.reloadRequestFailed'));
    } finally {
      setReloading(false);
    }
  };

  const statusIcon = (ok: boolean) =>
    ok ? (
      <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 18 }} />
    ) : (
      <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 18 }} />
    );

  const modelColumns = [
    {
      title: 'Tier',
      dataIndex: 'tier',
      key: 'tier',
      width: 60,
      render: (tier: number) => <Tag color="blue">Tier {tier}</Tag>,
    },
    {
      title: t('mlDetection.autoencoder'),
      dataIndex: 'available',
      key: 'available',
      width: 100,
      render: (val: boolean) => statusIcon(val),
    },
    {
      title: 'BiGRU',
      dataIndex: 'bigruAvailable',
      key: 'bigruAvailable',
      width: 100,
      render: (val: boolean | undefined) => val !== undefined ? statusIcon(val) : '-',
    },
    {
      title: t('mlDetection.threshold'),
      dataIndex: 'threshold',
      key: 'threshold',
      width: 100,
      render: (val: number) => val?.toFixed(4),
    },
    {
      title: t('mlDetection.optimalAlpha'),
      dataIndex: 'optimalAlpha',
      key: 'optimalAlpha',
      width: 100,
      render: (val: number | undefined) => val?.toFixed(2) || '-',
    },
    {
      title: t('mlDetection.modelPath'),
      dataIndex: 'modelPath',
      key: 'modelPath',
      ellipsis: true,
    },
  ];

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 100 }}>
        <Spin size="large" tip={t('mlDetection.loadingStatus')} />
      </div>
    );
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {/* 服务状态 */}
      <Row gutter={16}>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title={t('mlDetection.serviceStatus')}
              value={health?.status === 'ok' ? t('mlDetection.normal') : t('mlDetection.abnormal')}
              valueStyle={{
                color: health?.status === 'ok' ? '#3f8600' : '#cf1322',
              }}
              prefix={statusIcon(health?.status === 'ok')}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title={t('mlDetection.modelLoaded')}
              value={health?.modelLoaded ? t('mlDetection.loaded') : t('mlDetection.notLoaded')}
              valueStyle={{
                color: health?.modelLoaded ? '#3f8600' : '#cf1322',
              }}
              prefix={<ExperimentOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Statistic
              title={t('mlDetection.kafkaConnection')}
              value={health?.kafkaConnected ? t('mlDetection.connected') : t('mlDetection.notConnected')}
              valueStyle={{
                color: health?.kafkaConnected ? '#3f8600' : '#cf1322',
              }}
              prefix={<DatabaseOutlined />}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card>
            <Space>
              <Button
                type="primary"
                icon={<ThunderboltOutlined />}
                loading={training}
                onClick={handleTrain}
              >
                {t('mlDetection.trainModel')}
              </Button>
              <Button
                icon={<ReloadOutlined />}
                loading={reloading}
                onClick={handleReload}
              >
                {t('mlDetection.reloadModel')}
              </Button>
              <Button icon={<ReloadOutlined />} onClick={loadAll}>
                {t('common.refresh')}
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>

      {!health?.modelLoaded && !training && (
        <Alert
          type="info"
          message={t('mlDetection.noTrainedModelFound')}
          description={t('mlDetection.trainPrompt')}
          showIcon
        />
      )}

      {trainingStatus?.training && (
        <Card title={t('mlDetection.trainingProgress')} bordered={false}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Progress percent={99} status="active" format={() => t('mlDetection.training')} />
            <Descriptions column={2} size="small">
              <Descriptions.Item label={t('mlDetection.trainingTiers')}>
                {trainingStatus.tiers.map((t) => (
                  <Tag key={t} color="processing">Tier {t}</Tag>
                ))}
              </Descriptions.Item>
              <Descriptions.Item label={t('mlDetection.startedAt')}>
                {trainingStatus.startedAt || '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('mlDetection.elapsed')}>
                {trainingStatus.elapsedSeconds != null
                  ? `${Math.round(trainingStatus.elapsedSeconds)}s`
                  : '-'}
              </Descriptions.Item>
            </Descriptions>
          </Space>
        </Card>
      )}

      {trainingStatus && !trainingStatus.training && trainingStatus.completedAt && (
        <Card title={t('mlDetection.lastTrainingResult')} bordered={false}>
          <Descriptions column={2} size="small">
            <Descriptions.Item label={t('common.status')}>
              {trainingStatus.error ? (
                <Tag color="error">{t('common.failed')}</Tag>
              ) : (
                <Tag color="success">{t('common.success')}</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label={t('mlDetection.completedAt')}>
              {trainingStatus.completedAt}
            </Descriptions.Item>
            <Descriptions.Item label={t('mlDetection.elapsed')}>
              {trainingStatus.elapsedSeconds != null
                ? `${Math.round(trainingStatus.elapsedSeconds)}s`
                : '-'}
            </Descriptions.Item>
            {trainingStatus.error && (
              <Descriptions.Item label={t('common.error')} span={2}>
                <Tag color="error">{trainingStatus.error}</Tag>
              </Descriptions.Item>
            )}
          </Descriptions>
        </Card>
      )}

      {dataReadiness && (
        <Card title={t('mlDetection.trainingDataReadiness')} bordered={false} size="small">
          <Descriptions column={3} size="small">
            {dataReadiness.sampleCounts &&
              Object.entries(dataReadiness.sampleCounts).map(([tier, count]) => (
                <Descriptions.Item key={tier} label={t('mlDetection.tierSampleCount', { tier })}>
                  <Tag color={count >= (dataReadiness.minimumRequired?.bigru ?? 20) ? 'green' : 'orange'}>
                    {count}
                  </Tag>
                </Descriptions.Item>
              ))}
            <Descriptions.Item label={t('mlDetection.minRequiredAutoencoder')}>
              {dataReadiness.minimumRequired?.autoencoder ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('mlDetection.minRequiredBigru')}>
              {dataReadiness.minimumRequired?.bigru ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label={t('mlDetection.dataReady')}>
              {dataReadiness.ready ? (
                <Tag color="green">{t('mlDetection.ready')}</Tag>
              ) : (
                <Tag color="orange">{t('mlDetection.insufficient')}</Tag>
              )}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {/* 模型列表 */}
      <Card title={t('mlDetection.modelStatus')} bordered={false}>
        <Table
          columns={modelColumns}
          dataSource={models}
          rowKey="tier"
          pagination={false}
          size="small"
        />
      </Card>

      {/* 运行时统计 */}
      <Row gutter={16}>
        {/* 序列缓冲区 */}
        <Col span={8}>
          <Card title={t('mlDetection.sequenceBuffer')} bordered={false}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label={t('common.enabled')}>
                {bufferStats?.enabled ? <Tag color="green">{t('common.yes')}</Tag> : <Tag>{t('common.no')}</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label={t('mlDetection.totalKeys')}>
                {bufferStats?.totalKeys ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label={t('mlDetection.totalWindows')}>
                {bufferStats?.totalWindows ?? '-'}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        {/* 漂移检测 */}
        <Col span={8}>
          <Card title={t('mlDetection.driftDetection')} bordered={false}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label={t('common.enabled')}>
                {driftStatus?.enabled ? <Tag color="green">{t('common.yes')}</Tag> : <Tag>{t('common.no')}</Tag>}
              </Descriptions.Item>
              {driftStatus?.enabled &&
                Object.entries(driftStatus)
                  .filter(([k]) => k !== 'enabled')
                  .slice(0, 6)
                  .map(([k, v]) => (
                    <Descriptions.Item key={k} label={k}>
                      {typeof v === 'object' ? JSON.stringify(v) : String(v)}
                    </Descriptions.Item>
                  ))}
            </Descriptions>
          </Card>
        </Col>

        {/* 影子评分 */}
        <Col span={8}>
          <Card title={t('mlDetection.shadowScoring')} bordered={false}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label={t('common.enabled')}>
                {shadowStats?.enabled ? <Tag color="green">{t('common.yes')}</Tag> : <Tag>{t('common.no')}</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label={t('mlDetection.challengerLoaded')}>
                {shadowStats?.challengerLoaded ? (
                  <Tag color="green">{t('common.yes')}</Tag>
                ) : (
                  <Tag>{t('common.no')}</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label={t('mlDetection.totalComparisons')}>
                {shadowStats?.totalComparisons ?? 0}
              </Descriptions.Item>
              <Descriptions.Item label={t('mlDetection.challengerDir')}>
                {shadowStats?.challengerDir || '-'}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
      </Row>

      {!health && (
        <Alert
          type="warning"
          message={t('mlDetection.serviceUnavailable')}
          description={t('mlDetection.serviceUnavailableDesc')}
          showIcon
        />
      )}
    </Space>
  );
};

export default MlDetection;
