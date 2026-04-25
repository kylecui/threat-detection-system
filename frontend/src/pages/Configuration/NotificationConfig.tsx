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
import { useTranslation } from 'react-i18next';
import apiClient, { getCustomerId } from '@/services/api';
import type { SmtpConfig, NotificationConfig as NotificationConfigType } from '@/types';

const NotificationConfig = () => {
  const { t } = useTranslation();
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
      message.success(t('notificationConfig.smtpSaved'));
      loadSmtpConfig();
    } catch {
      message.error(t('notificationConfig.smtpSaveFailed'));
    }
  };

  const handleNotifSave = async (values: Record<string, unknown>) => {
    if (!customerId) {
      message.warning(t('notificationConfig.setCustomerIdFirst'));
      return;
    }
    try {
      await apiClient.put(
        `/api/v1/customers/${customerId}/notification-config`,
        values
      );
      message.success(t('notificationConfig.preferenceSaved'));
      loadNotifConfig();
    } catch {
      message.error(t('notificationConfig.preferenceSaveFailed'));
    }
  };

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      {canManageSmtp && (
        <Card bordered={false} loading={smtpLoading}>
          <Alert
            message={t('notificationConfig.smtpAlert')}
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
                  label={t('notificationConfig.smtpHost')}
                  name="host"
                  rules={[{ required: true, message: t('notificationConfig.validationSmtpHost') }]}
                >
                  <Input placeholder="smtp.example.com" />
                </Form.Item>
              </Col>
              <Col span={8}>
                <Form.Item
                  label={t('common.port')}
                  name="port"
                  rules={[{ required: true, message: t('notificationConfig.validationPort') }]}
                >
                  <InputNumber min={1} max={65535} placeholder="587" style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item label={t('common.username')} name="username">
                  <Input placeholder="user@example.com" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label={t('common.password')} name="password">
                  <Input.Password placeholder="••••••" />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item
                  label={t('notificationConfig.fromAddress')}
                  name="fromAddress"
                  rules={[{ required: true, message: t('notificationConfig.validationFromAddress') }]}
                >
                  <Input placeholder="noreply@example.com" />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item label={t('notificationConfig.fromName')} name="fromName">
                  <Input placeholder={t('app.title')} />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item label={t('notificationConfig.encryption')} name="encryption" initialValue="TLS">
              <Select
                options={[
                  { label: 'TLS', value: 'TLS' },
                  { label: 'SSL', value: 'SSL' },
                  { label: t('notificationConfig.noEncryption'), value: 'NONE' },
                ]}
              />
            </Form.Item>

            <Row gutter={16}>
              <Col span={12}>
                <Form.Item label={t('common.enabled')} name="enabled" valuePropName="checked" initialValue={true}>
                  <Switch checkedChildren={t('common.enabled')} unCheckedChildren={t('common.disabled')} />
                </Form.Item>
              </Col>
              <Col span={12}>
                <Form.Item
                  label={t('notificationConfig.setAsDefault')}
                  name="isDefault"
                  valuePropName="checked"
                  initialValue={true}
                >
                  <Switch checkedChildren={t('common.yes')} unCheckedChildren={t('common.no')} />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item>
              <Space>
                <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
                  {t('notificationConfig.saveSmtp')}
                </Button>
                <Button icon={<ReloadOutlined />} onClick={loadSmtpConfig}>
                  {t('common.refresh')}
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
            message={t('notificationConfig.noCustomerSelected')}
            description={t('notificationConfig.noCustomerSelectedDesc')}
            style={{ marginBottom: 24 }}
          />
        ) : (
          <>
            <Alert
              message={t('notificationConfig.currentCustomerNotice', { customerId })}
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
                label={t('notificationConfig.emailNotification')}
                name="emailEnabled"
                valuePropName="checked"
                initialValue={false}
              >
                <Switch checkedChildren={t('common.on')} unCheckedChildren={t('common.off')} />
              </Form.Item>

              <Form.Item
                label={t('notificationConfig.emailRecipients')}
                name="emailRecipients"
                tooltip={t('notificationConfig.emailRecipientsTooltip')}
              >
                <Select
                  mode="tags"
                  placeholder={t('notificationConfig.emailRecipientsPlaceholder')}
                  tokenSeparators={[',', ';']}
                />
              </Form.Item>

              <Divider />

              <Form.Item
                label={t('notificationConfig.smsNotification')}
                name="smsEnabled"
                valuePropName="checked"
                initialValue={false}
              >
                <Switch checkedChildren={t('common.on')} unCheckedChildren={t('common.off')} />
              </Form.Item>

              <Divider />

              <Form.Item
                label={t('notificationConfig.slackNotification')}
                name="slackEnabled"
                valuePropName="checked"
                initialValue={false}
              >
                <Switch checkedChildren={t('common.on')} unCheckedChildren={t('common.off')} />
              </Form.Item>

              <Form.Item label="Slack Webhook URL" name="slackWebhookUrl">
                <Input placeholder="https://hooks.slack.com/services/..." />
              </Form.Item>

              <Divider />

              <Form.Item
                label={t('notificationConfig.webhookNotification')}
                name="webhookEnabled"
                valuePropName="checked"
                initialValue={false}
              >
                <Switch checkedChildren={t('common.on')} unCheckedChildren={t('common.off')} />
              </Form.Item>

              <Form.Item label="Webhook URL" name="webhookUrl">
                <Input placeholder="https://your-webhook-endpoint.com/alerts" />
              </Form.Item>

              <Form.Item>
                <Space>
                  <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
                    {t('notificationConfig.savePreference')}
                  </Button>
                  <Button icon={<ReloadOutlined />} onClick={loadNotifConfig}>
                    {t('common.refresh')}
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
