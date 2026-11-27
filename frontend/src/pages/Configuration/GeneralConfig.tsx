import {
  Alert,
  Button,
  Form,
  InputNumber,
  Switch,
  message,
} from 'antd';
import { SaveOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { useScope } from '@/contexts/ScopeContext';
import './configuration.css';

const GeneralConfig = () => {
  const { t } = useTranslation();
  const [basicForm] = Form.useForm();
  const { effectiveCustomerId: customerId } = useScope();

  const handleBasicSave = (values: {
    auto_refresh: boolean;
    refresh_interval: number;
  }) => {
    localStorage.setItem('auto_refresh', String(values.auto_refresh));
    localStorage.setItem('refresh_interval', String(values.refresh_interval));
    message.success(t('generalConfig.messageSaved'));
  };

  return (
    <div className="tds-config-page tds-config-general">
      <Alert
        className="tds-config-note"
        message={t('generalConfig.alertMessage')}
        type="info"
        showIcon
      />

      <div className="tds-config-general__grid">
        <section className="tds-config-panel tds-config-panel--form">
          <Form
            className="tds-config-form"
            form={basicForm}
            layout="vertical"
            onFinish={handleBasicSave}
            initialValues={{
              auto_refresh: localStorage.getItem('auto_refresh') !== 'false',
              refresh_interval:
                Number(localStorage.getItem('refresh_interval')) || 30,
            }}
          >
            <Form.Item
              label={t('generalConfig.autoRefresh')}
              name="auto_refresh"
              valuePropName="checked"
              tooltip={t('generalConfig.autoRefreshTooltip')}
            >
              <Switch
                checkedChildren={t('common.on')}
                unCheckedChildren={t('common.off')}
              />
            </Form.Item>

            <Form.Item
              label={t('generalConfig.refreshInterval')}
              name="refresh_interval"
              rules={[
                {
                  required: true,
                  message: t('generalConfig.validationRefreshIntervalRequired'),
                },
              ]}
              tooltip={t('generalConfig.refreshIntervalTooltip')}
            >
              <InputNumber min={10} max={300} step={5} />
            </Form.Item>

            <div className="tds-config-actions">
              <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
                {t('generalConfig.save')}
              </Button>
            </div>
          </Form>
        </section>

        <aside className="tds-config-panel tds-config-environment">
          <div className="tds-config-panel__header">
            <h2>{t('generalConfig.currentEnv')}</h2>
          </div>
          <dl className="tds-config-facts">
            <div>
              <dt>{t('generalConfig.apiBaseUrl')}</dt>
              <dd><code>{import.meta.env.VITE_API_BASE_URL || '/api'}</code></dd>
            </div>
            <div>
              <dt>{t('generalConfig.currentCustomerId')}</dt>
              <dd><code>{customerId || t('common.all')}</code></dd>
            </div>
            <div>
              <dt>{t('generalConfig.appVersion')}</dt>
              <dd><code>{import.meta.env.VITE_APP_VERSION || 'dev'}</code></dd>
            </div>
          </dl>
        </aside>
      </div>
    </div>
  );
};

export default GeneralConfig;
