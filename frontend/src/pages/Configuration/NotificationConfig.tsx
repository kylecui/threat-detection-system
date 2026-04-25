import { useState, useEffect } from 'react';
import {
  Card,
  Form,
  Input,
  InputNumber,
  Button,
  Switch,
  message,
  Alert,
  Row,
  Col,
  Select,
  Space,
  Divider,
} from 'antd';
import { SaveOutlined, ReloadOutlined } from '@ant-design/icons';
import apiClient, { getCustomerId } from '@/services/api';
import type { SmtpConfig, NotificationConfig as NotificationConfigType } from '@/types';

const NotificationConfig = () => {
  const [smtpForm] = Form.useForm();
  const [notifForm] = Form.useForm();
  const [smtpConfig, setSmtpConfig] = useState<SmtpConfig | null>(null);
  const [, setNotifConfig] = useState<NotificationConfigType | null>(null);
  const [smtpLoading, setSmtpLoading] = useState(false);
  const [notifLoading, setNotifLoading] = useState(false);

  const customerId = getCustomerId();
  const userRoles: string[] = (() => {
    try {
      const user = localStorage.getItem('user');
      return user ? JSON.parse(user).roles || [] : [];
    } catch { return []; }
  })();
  const isSuperAdmin = userRoles.includes('SUPER_ADMIN');
  const isTenantAdmin = userRoles.includes('TENANT_ADMIN');
  const canManageSmtp = isSuperAdmin || isTenantAdmin;

  const loadSmtpConfig = async () => {
    if (!canManageSmtp) return;
    try {
      setSmtpLoading(true);
      const resp = await apiClient.get<SmtpConfig>('/api/notification-config/smtp/default');
      setSmtpConfig(resp.data);
      smtpForm.setFieldsValue(resp.data);
    } catch {
      console.log('No SMTP config found');
    } finally {
      setSmtpLoading(false);
    }
  };

  const loadNotifConfig = async () => {
    if (!customerId) return;
    try {
      setNotifLoading(true);
      const resp = await apiClient.get<NotificationConfigType>(
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

  const handleNotifSave = async (values: Record<string, unknown>) => {
    if (!customerId) {
      message.warning('请先设置客户ID');
      return;
    }
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

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {canManageSmtp && (
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
      )}

      <Card bordered={false} loading={notifLoading}>
        {!customerId ? (
          <Alert
            type="warning"
            showIcon
            message="未选择客户"
            description="通知偏好为客户级别配置。请先在「基础设置」标签页中设置客户ID，或在「客户管理」页面选择客户。"
            style={{ marginBottom: 24 }}
          />
        ) : (
          <>
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
          </>
        )}
      </Card>
    </Space>
  );
};

export default NotificationConfig;
