import { useState, useEffect } from 'react';
import {
  Card,
  Form,
  Input,
  InputNumber,
  Button,
  Switch,
  message,
  Tabs,
  Space,
  Divider,
  Select,
  Tag,
  Descriptions,
  Alert,
  Row,
  Col,
} from 'antd';
import {
  UserOutlined,
  MailOutlined,
  SettingOutlined,
  BellOutlined,
  SaveOutlined,
  ReloadOutlined,
  GlobalOutlined,
} from '@ant-design/icons';
import apiClient, { switchRegion } from '@/services/api';
import type { SmtpConfig, NotificationConfig, RegionId } from '@/types';
import { REGION_ENDPOINTS } from '@/types';

/**
 * 系统设置页面
 *
 * Tab 1: 基础设置 — 客户ID、自动刷新
 * Tab 2: SMTP邮件 — SMTP服务器配置
 * Tab 3: 通知偏好 — 邮件/SMS/Slack/Webhook开关
 */
const Settings = () => {
  // ──────── 基础设置 ────────
  const [basicForm] = Form.useForm();
  const [smtpForm] = Form.useForm();
  const [notifForm] = Form.useForm();

  const [smtpConfig, setSmtpConfig] = useState<SmtpConfig | null>(null);
  const [, setNotifConfig] = useState<NotificationConfig | null>(null);
  const [smtpLoading, setSmtpLoading] = useState(false);
  const [notifLoading, setNotifLoading] = useState(false);

  const customerId = localStorage.getItem('customer_id') || 'demo-customer';

  // ──────── 加载SMTP配置 ────────
  const loadSmtpConfig = async () => {
    try {
      setSmtpLoading(true);
      const resp = await apiClient.get<SmtpConfig>('/api/notification-config/smtp/default');
      setSmtpConfig(resp.data);
      smtpForm.setFieldsValue(resp.data);
    } catch {
      // 没有SMTP配置是正常的
      console.log('No SMTP config found');
    } finally {
      setSmtpLoading(false);
    }
  };

  // ──────── 加载通知配置 ────────
  const loadNotifConfig = async () => {
    try {
      setNotifLoading(true);
      const resp = await apiClient.get<NotificationConfig>(
        `/api/v1/customers/${customerId}/notification-config`
      );
      setNotifConfig(resp.data);
      notifForm.setFieldsValue(resp.data);
    } catch {
      console.log('No notification config found');
    } finally {
      setNotifLoading(false);
    }
  };

  useEffect(() => {
    loadSmtpConfig();
    loadNotifConfig();
  }, []);

  // ──────── 保存基础设置 ────────
  const handleBasicSave = (values: {
    customer_id: string;
    auto_refresh: boolean;
    refresh_interval: number;
    region: RegionId;
  }) => {
    localStorage.setItem('customer_id', values.customer_id);
    localStorage.setItem('auto_refresh', String(values.auto_refresh));
    localStorage.setItem('refresh_interval', String(values.refresh_interval));
    switchRegion(values.region);
    message.success('基础设置已保存');
  };

  // ──────── 保存SMTP ────────
  const handleSmtpSave = async (values: Record<string, unknown>) => {
    try {
      if (smtpConfig?.id) {
        await apiClient.put(`/api/notification-config/smtp/${smtpConfig.id}`, values);
      } else {
        await apiClient.post('/api/notification-config/smtp', values);
      }
      message.success('SMTP配置已保存');
      loadSmtpConfig();
    } catch {
      message.error('SMTP配置保存失败');
    }
  };

  // ──────── 保存通知偏好 ────────
  const handleNotifSave = async (values: Record<string, unknown>) => {
    try {
      await apiClient.put(
        `/api/v1/customers/${customerId}/notification-config`,
        values
      );
      message.success('通知偏好已保存');
      loadNotifConfig();
    } catch {
      message.error('通知偏好保存失败');
    }
  };

  // ──────── Tab项 ────────
  const tabItems = [
    {
      key: 'basic',
      label: (
        <span>
          <SettingOutlined /> 基础设置
        </span>
      ),
      children: (
        <Card bordered={false}>
          <Alert
            message="客户ID用于多租户数据隔离，修改后所有API请求将使用新的客户ID"
            type="info"
            showIcon
            style={{ marginBottom: 24 }}
          />
          <Form
            form={basicForm}
            layout="vertical"
            onFinish={handleBasicSave}
            initialValues={{
              customer_id: customerId,
              auto_refresh: localStorage.getItem('auto_refresh') !== 'false',
              refresh_interval: Number(localStorage.getItem('refresh_interval')) || 30,
              region: (localStorage.getItem('region') || 'auto') as RegionId,
            }}
            style={{ maxWidth: 500 }}
          >
            <Form.Item
              label="客户ID (Customer ID)"
              name="customer_id"
              rules={[{ required: true, message: '请输入客户ID' }]}
              tooltip="多租户隔离标识，用于API请求"
            >
              <Input prefix={<UserOutlined />} placeholder="demo-customer" />
            </Form.Item>

            <Form.Item
              label="部署区域 (Region)"
              name="region"
              tooltip="选择API服务器区域，影响所有请求的目标端点"
            >
              <Select>
                {Object.values(REGION_ENDPOINTS).map((r) => (
                  <Select.Option key={r.id} value={r.id}>
                    <Space>
                      <GlobalOutlined />
                      {r.label}
                      <Tag color={r.id === 'auto' ? 'default' : r.id === 'cn' ? 'red' : 'blue'}>
                        {r.description}
                      </Tag>
                    </Space>
                  </Select.Option>
                ))}
              </Select>
            </Form.Item>

            <Form.Item
              label="自动刷新"
              name="auto_refresh"
              valuePropName="checked"
              tooltip="启用后仪表盘和监控页面将自动刷新数据"
            >
              <Switch checkedChildren="开" unCheckedChildren="关" />
            </Form.Item>

            <Form.Item
              label="刷新间隔 (秒)"
              name="refresh_interval"
              rules={[{ required: true, message: '请输入刷新间隔' }]}
              tooltip="自动刷新的时间间隔，建议10-300秒"
            >
              <InputNumber min={10} max={300} step={5} style={{ width: '100%' }} />
            </Form.Item>

            <Form.Item>
              <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
                保存基础设置
              </Button>
            </Form.Item>
          </Form>

          <Divider />
          <Descriptions title="当前环境信息" bordered column={1} size="small">
            <Descriptions.Item label="API基础地址">
              {import.meta.env.VITE_API_BASE_URL || '/api'}
            </Descriptions.Item>
            <Descriptions.Item label="当前客户ID">
              <Tag color="blue">{customerId}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="当前区域">
              <Tag color="green">
                {REGION_ENDPOINTS[(localStorage.getItem('region') || 'auto') as RegionId]?.label || 'Auto'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="应用版本">v1.0.0</Descriptions.Item>
          </Descriptions>
        </Card>
      ),
    },
    {
      key: 'smtp',
      label: (
        <span>
          <MailOutlined /> SMTP邮件
        </span>
      ),
      children: (
        <Card bordered={false} loading={smtpLoading}>
          <Alert
            message="SMTP配置用于告警邮件通知，请确保SMTP服务器可达"
            type="info"
            showIcon
            style={{ marginBottom: 24 }}
          />
          <Form
            form={smtpForm}
            layout="vertical"
            onFinish={handleSmtpSave}
            style={{ maxWidth: 600 }}
          >
            <Row gutter={16}>
              <Col span={16}>
                <Form.Item
                  label="SMTP主机"
                  name="host"
                  rules={[{ required: true, message: '请输入SMTP主机' }]}
                >
                  <Input placeholder="smtp.example.com" />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item
                  label="端口"
                  name="port"
                  rules={[{ required: true, message: '请输入端口' }]}
                >
                  <InputNumber min={1} max={65535} placeholder="587" style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item label="用户名" name="username">
                  <Input placeholder="user@example.com" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="密码" name="password">
                  <Input.Password placeholder="••••••" />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  label="发件人地址"
                  name="fromAddress"
                  rules={[{ required: true, message: '请输入发件人地址' }]}
                >
                  <Input placeholder="noreply@example.com" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label="发件人名称" name="fromName">
                  <Input placeholder="威胁检测系统" />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item label="加密方式" name="encryption" initialValue="TLS">
              <Select
                options={[
                  { label: 'TLS', value: 'TLS' },
                  { label: 'SSL', value: 'SSL' },
                  { label: '无加密', value: 'NONE' },
                ]}
              />
            </Form.Item>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item label="启用" name="enabled" valuePropName="checked" initialValue={true}>
                  <Switch checkedChildren="启用" unCheckedChildren="禁用" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  label="设为默认"
                  name="isDefault"
                  valuePropName="checked"
                  initialValue={true}
                >
                  <Switch checkedChildren="是" unCheckedChildren="否" />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
                  保存SMTP配置
                </Button>
                <Button icon={<ReloadOutlined />} onClick={loadSmtpConfig}>
                  刷新
                </Button>
              </Space>
            </Form.Item>
          </Form>
        </Card>
      ),
    },
    {
      key: 'notification',
      label: (
        <span>
          <BellOutlined /> 通知偏好
        </span>
      ),
      children: (
        <Card bordered={false} loading={notifLoading}>
          <Alert
            message={`当前客户: ${customerId} — 通知配置将应用于该客户的所有告警`}
            type="info"
            showIcon
            style={{ marginBottom: 24 }}
          />
          <Form
            form={notifForm}
            layout="vertical"
            onFinish={handleNotifSave}
            style={{ maxWidth: 600 }}
          >
            <Form.Item
              label="邮件通知"
              name="emailEnabled"
              valuePropName="checked"
              initialValue={false}
            >
              <Switch checkedChildren="开" unCheckedChildren="关" />
            </Form.Item>

            <Form.Item
              label="邮件接收人"
              name="emailRecipients"
              tooltip="多个邮箱地址用回车分隔"
            >
              <Select
                mode="tags"
                placeholder="输入邮箱地址后按回车"
                tokenSeparators={[',', ';']}
              />
            </Form.Item>

            <Divider />

            <Form.Item
              label="SMS通知"
              name="smsEnabled"
              valuePropName="checked"
              initialValue={false}
            >
              <Switch checkedChildren="开" unCheckedChildren="关" />
            </Form.Item>

            <Divider />

            <Form.Item
              label="Slack通知"
              name="slackEnabled"
              valuePropName="checked"
              initialValue={false}
            >
              <Switch checkedChildren="开" unCheckedChildren="关" />
            </Form.Item>

            <Form.Item label="Slack Webhook URL" name="slackWebhookUrl">
              <Input placeholder="https://hooks.slack.com/services/..." />
            </Form.Item>

            <Divider />

            <Form.Item
              label="Webhook通知"
              name="webhookEnabled"
              valuePropName="checked"
              initialValue={false}
            >
              <Switch checkedChildren="开" unCheckedChildren="关" />
            </Form.Item>

            <Form.Item label="Webhook URL" name="webhookUrl">
              <Input placeholder="https://your-webhook-endpoint.com/alerts" />
            </Form.Item>

            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
                  保存通知偏好
                </Button>
                <Button icon={<ReloadOutlined />} onClick={loadNotifConfig}>
                  刷新
                </Button>
              </Space>
            </Form.Item>
          </Form>
        </Card>
      ),
    },
  ];

  return (
    <Card title="系统设置" bordered={false}>
      <Tabs defaultActiveKey="basic" items={tabItems} />
    </Card>
  );
};

export default Settings;
