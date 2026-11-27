import { useState, useEffect, useCallback } from 'react';
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
  Modal,
} from 'antd';
import { SaveOutlined, ReloadOutlined, ExperimentOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import apiClient from '@/services/api';
import { useScope } from '@/contexts/ScopeContext';
import { usePermission } from '@/hooks/usePermission';
import type { SmtpConfig, NotificationConfig as NotificationConfigType } from '@/types';
import './configuration.css';

const NotificationConfig = () => {
  const { t } = useTranslation();
  const [smtpForm] = Form.useForm();
  const [notifForm] = Form.useForm();
  const [smtpConfig, setSmtpConfig] = useState<SmtpConfig | null>(null);
  const [, setNotifConfig] = useState<NotificationConfigType | null>(null);
  const [smtpLoading, setSmtpLoading] = useState(false);
  const [notifLoading, setNotifLoading] = useState(false);
  const [smtpTesting, setSmtpTesting] = useState(false);
  const [notifTesting, setNotifTesting] = useState(false);

  const { effectiveCustomerId: customerId } = useScope();
  const { isSuperAdmin, isTenantAdmin } = usePermission();
  const canManageSmtp = isSuperAdmin || isTenantAdmin;

  const loadSmtpConfig = useCallback(async () => {
    if (!canManageSmtp) return;
    try {
      setSmtpLoading(true);
      const resp = await apiClient.get<SmtpConfig>('/api/notification-config/smtp/default', {
        suppressGlobalError: true,
      });
      if (resp.data.configured) {
        setSmtpConfig(resp.data);
        smtpForm.setFieldsValue({ ...resp.data, password: undefined });
      } else {
        setSmtpConfig(null);
        smtpForm.resetFields();
        smtpForm.setFieldsValue({
          port: 587,
          encryption: 'STARTTLS',
          active: true,
          isDefault: true,
        });
      }
    } catch {
      console.log('No SMTP config found');
    } finally {
      setSmtpLoading(false);
    }
  }, [canManageSmtp, smtpForm]);

  const loadNotifConfig = useCallback(async () => {
    if (!customerId) return;
    try {
      setNotifLoading(true);
      const resp = await apiClient.get<NotificationConfigType>(
        `/api/v1/customers/${customerId}/notification-config`,
        { suppressGlobalError: true }
      );
      setNotifConfig(resp.data);
      notifForm.setFieldsValue({
        ...resp.data,
        quietHoursStart: resp.data.quietHoursStart?.slice(0, 5),
        quietHoursEnd: resp.data.quietHoursEnd?.slice(0, 5),
      });
    } catch {
      console.log('No notification config found');
    } finally {
      setNotifLoading(false);
    }
  }, [customerId, notifForm]);

  useEffect(() => {
    loadSmtpConfig();
    loadNotifConfig();
  }, [loadNotifConfig, loadSmtpConfig]);

  const handleSmtpSave = async (values: Record<string, unknown>) => {
    try {
      await apiClient.put('/api/notification-config/smtp/default', values);
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

  const handleSmtpTest = async () => {
    if (!smtpConfig?.id) {
      message.warning(t('notificationConfig.saveSmtpBeforeTest'));
      return;
    }
    try {
      setSmtpTesting(true);
      const response = await apiClient.post<{ success: boolean; message: string }>(
        `/api/notification-config/smtp/${smtpConfig.id}/test`,
        undefined,
        { suppressGlobalError: true }
      );
      if (response.data.success) {
        message.success(t('notificationConfig.smtpTestSucceeded'));
      } else {
        message.error(t('notificationConfig.smtpTestFailed'));
      }
    } catch {
      message.error(t('notificationConfig.smtpTestFailed'));
    } finally {
      setSmtpTesting(false);
    }
  };

  const handleNotifTest = async () => {
    if (!customerId) return;
    try {
      setNotifTesting(true);
      const values = await notifForm.validateFields();
      await apiClient.put(`/api/v1/customers/${customerId}/notification-config`, values, {
        suppressGlobalError: true,
      });
      const response = await apiClient.post<{
        success: boolean;
        status: string;
        succeeded: number;
        failed: number;
        results: Array<{ channel: string; target: string; success: boolean; message: string }>;
      }>(`/api/v1/customers/${customerId}/notification-config/test`, undefined, {
        suppressGlobalError: true,
      });
      const detail = response.data.results
        .map((result) => `${result.channel} · ${result.target}: ${result.success ? 'OK' : result.message}`)
        .join('\n');
      Modal[response.data.success ? 'success' : 'warning']({
        title: response.data.success
          ? t('notificationConfig.notificationTestSucceeded')
          : t('notificationConfig.notificationTestPartial'),
        content: <div style={{ whiteSpace: 'pre-line' }}>{detail}</div>,
      });
      loadNotifConfig();
    } catch {
      message.error(t('notificationConfig.notificationTestFailed'));
    } finally {
      setNotifTesting(false);
    }
  };

  return (
    <div className="tds-config-page tds-config-notifications">
      {canManageSmtp && (
        <Card className="tds-config-panel" variant="borderless" loading={smtpLoading}>
          <Alert
            className="tds-config-note"
            message={t('notificationConfig.smtpAlert')}
            type="info"
            showIcon
          />
          <Form
            className="tds-config-form tds-config-form--wide"
            form={smtpForm}
            layout="vertical"
            onFinish={handleSmtpSave}
          >
            <Row gutter={16}>
              <Col xs={24} sm={16}>
                <Form.Item
                  label={t('notificationConfig.smtpHost')}
                  name="host"
                  rules={[{ required: true, message: t('notificationConfig.validationSmtpHost') }]}
                >
                  <Input placeholder="smtp.corp.internal" />
                </Form.Item>
              </Col>
              <Col xs={24} sm={8}>
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
              <Col xs={24} sm={12}>
                <Form.Item label={t('common.username')} name="username">
                  <Input autoComplete="off" spellCheck={false} placeholder="user@corp.internal" />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <Form.Item
                  label={t('common.password')}
                  name="password"
                  rules={[{
                    required: !smtpConfig?.passwordConfigured,
                    message: t('notificationConfig.validationPassword'),
                  }]}
                >
                  <Input.Password placeholder="••••••" />
                </Form.Item>
              </Col>
            </Row>

            <Row gutter={16}>
              <Col xs={24} sm={12}>
                <Form.Item
                  label={t('notificationConfig.fromAddress')}
                  name="fromAddress"
                  rules={[{ required: true, message: t('notificationConfig.validationFromAddress') }]}
                >
                  <Input placeholder="noreply@corp.internal" />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
                <Form.Item label={t('notificationConfig.fromName')} name="fromName">
                  <Input placeholder={t('app.title')} />
                </Form.Item>
              </Col>
            </Row>

            <Form.Item label={t('notificationConfig.encryption')} name="encryption" initialValue="STARTTLS">
              <Select
                options={[
                  { label: 'STARTTLS', value: 'STARTTLS' },
                  { label: 'TLS', value: 'TLS' },
                  { label: 'SSL', value: 'SSL' },
                  { label: t('notificationConfig.noEncryption'), value: 'NONE' },
                ]}
              />
            </Form.Item>

            <Row gutter={16}>
              <Col xs={24} sm={12}>
                <Form.Item label={t('common.enabled')} name="active" valuePropName="checked" initialValue={true}>
                  <Switch checkedChildren={t('common.enabled')} unCheckedChildren={t('common.disabled')} />
                </Form.Item>
              </Col>
              <Col xs={24} sm={12}>
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
              <Space className="tds-config-actions" wrap>
                <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
                  {t('notificationConfig.saveSmtp')}
                </Button>
                <Button
                  icon={<ExperimentOutlined />}
                  loading={smtpTesting}
                  disabled={!smtpConfig?.id || smtpConfig.active === false}
                  onClick={handleSmtpTest}
                >
                  {t('notificationConfig.testSmtp')}
                </Button>
                <Button icon={<ReloadOutlined />} onClick={loadSmtpConfig}>
                  {t('common.refresh')}
                </Button>
              </Space>
            </Form.Item>
          </Form>
        </Card>
      )}

      <Card className="tds-config-panel" variant="borderless" loading={notifLoading}>
        {!customerId ? (
          <Alert
            className="tds-config-note"
            type="warning"
            showIcon
            message={t('notificationConfig.noCustomerSelected')}
            description={t('notificationConfig.noCustomerSelectedDesc')}
          />
        ) : (
          <>
            <Alert
              className="tds-config-note"
              message={t('notificationConfig.currentCustomerNotice', { customerId })}
              type="info"
              showIcon
            />
            <Form
              className="tds-config-form tds-config-form--wide"
              form={notifForm}
              layout="vertical"
              onFinish={handleNotifSave}
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

              <Form.Item
                label={t('notificationConfig.smsRecipients')}
                name="smsRecipients"
                tooltip={t('notificationConfig.smsRecipientsTooltip')}
              >
                <Select
                  mode="tags"
                  placeholder={t('notificationConfig.smsRecipientsPlaceholder')}
                  tokenSeparators={[',', ';']}
                />
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
                <Input type="url" inputMode="url" autoComplete="off" spellCheck={false} placeholder="https://hooks.slack.com/services/…" />
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
                <Input type="url" inputMode="url" autoComplete="off" spellCheck={false} placeholder="https://your-webhook-endpoint.com/alerts" />
              </Form.Item>

              <Divider />

              <Row gutter={16}>
                <Col xs={24} sm={12}>
                  <Form.Item
                    label={t('notificationConfig.minimumSeverity')}
                    name="minSeverityLevel"
                    initialValue="MEDIUM"
                  >
                    <Select
                      options={['INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((value) => ({
                        value,
                        label: value,
                      }))}
                    />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item
                    label={t('notificationConfig.enabledSeverities')}
                    name="notifyOnSeverities"
                    initialValue={['MEDIUM', 'HIGH', 'CRITICAL']}
                  >
                    <Select
                      mode="multiple"
                      options={['INFO', 'LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((value) => ({
                        value,
                        label: value,
                      }))}
                    />
                  </Form.Item>
                </Col>
              </Row>

              <Row gutter={16}>
                <Col xs={24} sm={12}>
                  <Form.Item
                    label={t('notificationConfig.rateLimiting')}
                    name="enableRateLimiting"
                    valuePropName="checked"
                    initialValue={true}
                  >
                    <Switch checkedChildren={t('common.on')} unCheckedChildren={t('common.off')} />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={12}>
                  <Form.Item
                    label={t('notificationConfig.maxPerHour')}
                    name="maxNotificationsPerHour"
                    initialValue={100}
                    rules={[{ required: true }]}
                  >
                    <InputNumber min={1} max={10000} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item
                label={t('notificationConfig.quietHours')}
                name="quietHoursEnabled"
                valuePropName="checked"
                initialValue={false}
              >
                <Switch checkedChildren={t('common.on')} unCheckedChildren={t('common.off')} />
              </Form.Item>

              <Row gutter={16}>
                <Col xs={24} sm={8}>
                  <Form.Item label={t('notificationConfig.quietStart')} name="quietHoursStart">
                    <Input type="time" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={8}>
                  <Form.Item label={t('notificationConfig.quietEnd')} name="quietHoursEnd">
                    <Input type="time" />
                  </Form.Item>
                </Col>
                <Col xs={24} sm={8}>
                  <Form.Item
                    label={t('notificationConfig.timezone')}
                    name="quietHoursTimezone"
                    initialValue="Asia/Shanghai"
                  >
                    <Select
                      options={[
                        { value: 'Asia/Shanghai', label: 'Asia/Shanghai' },
                        { value: 'UTC', label: 'UTC' },
                        { value: 'Asia/Hong_Kong', label: 'Asia/Hong_Kong' },
                        { value: 'Asia/Singapore', label: 'Asia/Singapore' },
                      ]}
                    />
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item
                label={t('notificationConfig.configurationEnabled')}
                name="isActive"
                valuePropName="checked"
                initialValue={true}
              >
                <Switch checkedChildren={t('common.enabled')} unCheckedChildren={t('common.disabled')} />
              </Form.Item>

              <Form.Item>
                <Space className="tds-config-actions" wrap>
                  <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
                    {t('notificationConfig.savePreference')}
                  </Button>
                  <Button
                    icon={<ExperimentOutlined />}
                    loading={notifTesting}
                    onClick={handleNotifTest}
                  >
                    {t('notificationConfig.testNotification')}
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
    </div>
  );
};

export default NotificationConfig;
