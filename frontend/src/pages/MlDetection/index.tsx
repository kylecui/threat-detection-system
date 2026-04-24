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
import mlService from '@/services/ml';

/**
 * ML检测监控页面
 */
const MlDetection = () => {
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
          message.error(`训练失败: ${status.error}`);
        } else {
          message.success('模型训练完成');
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
        message.warning('训练已在进行中');
      } else if (result.status === 'training_started') {
        message.success(`训练已启动 (Tier ${result.tiers.join(', ')})`);
      } else {
        message.error(`训练启动失败: ${result.status}`);
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
      message.error('训练请求失败');
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
          message.success(`模型重载成功，已加载 ${loadedCount} 个模型`);
        } else {
          message.warning('模型重载完成，但未找到可用模型文件');
        }
        loadAll();
      } else {
        message.error(`重载失败: ${result.error || '未知错误'}`);
      }
    } catch {
      message.error('模型重载失败');
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
      title: '自编码器',
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
      title: '阈值',
      dataIndex: 'threshold',
      key: 'threshold',
      width: 100,
      render: (val: number) => val?.toFixed(4),
    },
    {
      title: '最优Alpha',
      dataIndex: 'optimalAlpha',
      key: 'optimalAlpha',
      width: 100,
      render: (val: number | undefined) => val?.toFixed(2) || '-',
    },
    {
      title: '模型路径',
      dataIndex: 'modelPath',
      key: 'modelPath',
      ellipsis: true,
    },
  ];

  if (loading) {
    return (
      <div style={{ textAlign: 'center', padding: 100 }}>
        <Spin size="large" tip="加载ML检测状态..." />
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
              title="服务状态"
              value={health?.status === 'ok' ? '正常' : '异常'}
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
              title="模型加载"
              value={health?.modelLoaded ? '已加载' : '未加载'}
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
              title="Kafka连接"
              value={health?.kafkaConnected ? '已连接' : '未连接'}
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
                训练模型
              </Button>
              <Button
                icon={<ReloadOutlined />}
                loading={reloading}
                onClick={handleReload}
              >
                重载模型
              </Button>
              <Button icon={<ReloadOutlined />} onClick={loadAll}>
                刷新
              </Button>
            </Space>
          </Card>
        </Col>
      </Row>

      {!health?.modelLoaded && !training && (
        <Alert
          type="info"
          message="未找到已训练的模型"
          description="请点击「训练模型」按钮开始训练（需要系统中有足够的攻击数据）。训练完成后模型将自动加载。"
          showIcon
        />
      )}

      {trainingStatus?.training && (
        <Card title="训练进度" bordered={false}>
          <Space direction="vertical" style={{ width: '100%' }}>
            <Progress percent={99} status="active" format={() => '训练中...'} />
            <Descriptions column={2} size="small">
              <Descriptions.Item label="训练层级">
                {trainingStatus.tiers.map((t) => (
                  <Tag key={t} color="processing">Tier {t}</Tag>
                ))}
              </Descriptions.Item>
              <Descriptions.Item label="启动时间">
                {trainingStatus.startedAt || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="已耗时">
                {trainingStatus.elapsedSeconds != null
                  ? `${Math.round(trainingStatus.elapsedSeconds)}s`
                  : '-'}
              </Descriptions.Item>
            </Descriptions>
          </Space>
        </Card>
      )}

      {trainingStatus && !trainingStatus.training && trainingStatus.completedAt && (
        <Card title="上次训练结果" bordered={false}>
          <Descriptions column={2} size="small">
            <Descriptions.Item label="状态">
              {trainingStatus.error ? (
                <Tag color="error">失败</Tag>
              ) : (
                <Tag color="success">成功</Tag>
              )}
            </Descriptions.Item>
            <Descriptions.Item label="完成时间">
              {trainingStatus.completedAt}
            </Descriptions.Item>
            <Descriptions.Item label="耗时">
              {trainingStatus.elapsedSeconds != null
                ? `${Math.round(trainingStatus.elapsedSeconds)}s`
                : '-'}
            </Descriptions.Item>
            {trainingStatus.error && (
              <Descriptions.Item label="错误" span={2}>
                <Tag color="error">{trainingStatus.error}</Tag>
              </Descriptions.Item>
            )}
          </Descriptions>
        </Card>
      )}

      {dataReadiness && (
        <Card title="训练数据就绪状态" bordered={false} size="small">
          <Descriptions column={3} size="small">
            {dataReadiness.sampleCounts &&
              Object.entries(dataReadiness.sampleCounts).map(([tier, count]) => (
                <Descriptions.Item key={tier} label={`Tier ${tier} 样本数`}>
                  <Tag color={count >= (dataReadiness.minimumRequired?.bigru ?? 20) ? 'green' : 'orange'}>
                    {count}
                  </Tag>
                </Descriptions.Item>
              ))}
            <Descriptions.Item label="最低要求 (自编码器)">
              {dataReadiness.minimumRequired?.autoencoder ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="最低要求 (BiGRU)">
              {dataReadiness.minimumRequired?.bigru ?? '-'}
            </Descriptions.Item>
            <Descriptions.Item label="数据就绪">
              {dataReadiness.ready ? (
                <Tag color="green">就绪</Tag>
              ) : (
                <Tag color="orange">不足</Tag>
              )}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {/* 模型列表 */}
      <Card title="模型状态" bordered={false}>
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
          <Card title="序列缓冲区" bordered={false}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="启用">
                {bufferStats?.enabled ? <Tag color="green">是</Tag> : <Tag>否</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="总Key数">
                {bufferStats?.totalKeys ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="总窗口数">
                {bufferStats?.totalWindows ?? '-'}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>

        {/* 漂移检测 */}
        <Col span={8}>
          <Card title="漂移检测" bordered={false}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="启用">
                {driftStatus?.enabled ? <Tag color="green">是</Tag> : <Tag>否</Tag>}
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
          <Card title="影子评分" bordered={false}>
            <Descriptions column={1} size="small">
              <Descriptions.Item label="启用">
                {shadowStats?.enabled ? <Tag color="green">是</Tag> : <Tag>否</Tag>}
              </Descriptions.Item>
              <Descriptions.Item label="Challenger加载">
                {shadowStats?.challengerLoaded ? (
                  <Tag color="green">是</Tag>
                ) : (
                  <Tag>否</Tag>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="总比较次数">
                {shadowStats?.totalComparisons ?? 0}
              </Descriptions.Item>
              <Descriptions.Item label="Challenger目录">
                {shadowStats?.challengerDir || '-'}
              </Descriptions.Item>
            </Descriptions>
          </Card>
        </Col>
      </Row>

      {!health && (
        <Alert
          type="warning"
          message="ML检测服务不可达"
          description="无法连接到ML检测服务 (端口8086)，请检查服务是否启动。"
          showIcon
        />
      )}
    </Space>
  );
};

export default MlDetection;
