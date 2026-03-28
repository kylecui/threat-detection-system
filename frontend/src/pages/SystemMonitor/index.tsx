import { useState, useEffect } from 'react';
import { Card, Row, Col, Tag, Space, Button, Spin, Descriptions } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import type { ServiceHealth } from '@/types';
import systemService from '@/services/system';

/**
 * 系统监控页面
 */
const SystemMonitor = () => {
  const [loading, setLoading] = useState(true);
  const [services, setServices] = useState<ServiceHealth[]>([]);

  const loadHealth = async () => {
    setLoading(true);
    try {
      const result = await systemService.getAllServiceHealth();
      setServices(result);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadHealth();
    const interval = setInterval(loadHealth, 30000);
    return () => clearInterval(interval);
  }, []);

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'UP':
        return '#52c41a';
      case 'DOWN':
        return '#ff4d4f';
      default:
        return '#d9d9d9';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'UP':
        return <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 24 }} />;
      case 'DOWN':
        return <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 24 }} />;
      default:
        return <CloseCircleOutlined style={{ color: '#d9d9d9', fontSize: 24 }} />;
    }
  };

  const upCount = services.filter((s) => s.status === 'UP').length;
  const downCount = services.filter((s) => s.status === 'DOWN').length;

  if (loading && services.length === 0) {
    return (
      <div style={{ textAlign: 'center', padding: 100 }}>
        <Spin size="large" tip="检查服务状态..." />
      </div>
    );
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      {/* 概览 */}
      <Card>
        <Row justify="space-between" align="middle">
          <Col>
            <Space size="large">
              <span>
                <Tag color="green" style={{ fontSize: 14, padding: '4px 12px' }}>
                  {upCount} 正常
                </Tag>
              </span>
              <span>
                <Tag color="red" style={{ fontSize: 14, padding: '4px 12px' }}>
                  {downCount} 异常
                </Tag>
              </span>
              <span>
                <Tag style={{ fontSize: 14, padding: '4px 12px' }}>
                  {services.length} 总计
                </Tag>
              </span>
            </Space>
          </Col>
          <Col>
            <Button icon={<ReloadOutlined />} onClick={loadHealth} loading={loading}>
              刷新
            </Button>
          </Col>
        </Row>
      </Card>

      {/* 服务卡片网格 */}
      <Row gutter={[16, 16]}>
        {services.map((svc) => (
          <Col xs={24} sm={12} lg={8} key={svc.name}>
            <Card
              hoverable
              style={{
                borderLeft: `4px solid ${getStatusColor(svc.status)}`,
              }}
            >
              <Row align="middle" gutter={16}>
                <Col>{getStatusIcon(svc.status)}</Col>
                <Col flex="auto">
                  <div style={{ fontSize: 16, fontWeight: 500 }}>{svc.name}</div>
                  <div style={{ color: '#888', fontSize: 12 }}>
                    端口: {svc.port}
                  </div>
                </Col>
                <Col>
                  <Tag color={svc.status === 'UP' ? 'green' : 'red'}>
                    {svc.status}
                  </Tag>
                </Col>
              </Row>
              <Descriptions
                size="small"
                column={2}
                style={{ marginTop: 12 }}
              >
                <Descriptions.Item label="响应时间">
                  {svc.responseTime !== undefined ? `${svc.responseTime}ms` : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="检查时间">
                  {svc.lastChecked
                    ? new Date(svc.lastChecked).toLocaleTimeString('zh-CN')
                    : '-'}
                </Descriptions.Item>
              </Descriptions>
            </Card>
          </Col>
        ))}
      </Row>
    </Space>
  );
};

export default SystemMonitor;
