import { useState, useEffect } from 'react';
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
} from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ReloadOutlined,
  ExperimentOutlined,
  DatabaseOutlined,
} from '@ant-design/icons';
import type {
  MlHealthStatus,
  MlModelInfo,
  MlBufferStats,
  MlDriftStatus,
  MlShadowStats,
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

  const loadAll = async () => {
    setLoading(true);
    try {
      const [h, m, b, d, s] = await Promise.all([
        mlService.getHealth().catch(() => null),
        mlService.getModels().catch(() => []),
        mlService.getBufferStats().catch(() => null),
        mlService.getDriftStatus().catch(() => null),
        mlService.getShadowStats().catch(() => null),
      ]);
      setHealth(h);
      setModels(m);
      setBufferStats(b);
      setDriftStatus(d);
      setShadowStats(s);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAll();
    const interval = setInterval(loadAll, 30000);
    return () => clearInterval(interval);
  }, []);

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
