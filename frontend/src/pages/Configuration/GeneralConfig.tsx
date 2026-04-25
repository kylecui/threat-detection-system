import { Card, Form, Input, InputNumber, Button, Switch, message, Alert, Divider, Descriptions, Tag } from 'antd';
import { UserOutlined, SaveOutlined } from '@ant-design/icons';
import { useTranslation } from 'react-i18next';
import { getCustomerId } from '@/services/api';

const GeneralConfig = () => {
  const { t } = useTranslation();
  const [basicForm] = Form.useForm();
  const customerId = getCustomerId();

  const handleBasicSave = (values: {
    customer_id: string;
    auto_refresh: boolean;
    refresh_interval: number;
  }) => {
    localStorage.setItem('customer_id', values.customer_id);
    localStorage.setItem('auto_refresh', String(values.auto_refresh));
    localStorage.setItem('refresh_interval', String(values.refresh_interval));
    message.success(t('generalConfig.messageSaved'));
  };

  return (
    <Card bordered={false}>
      <Alert
        message={t('generalConfig.alertMessage')}
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
        }}
        style={{ maxWidth: 500 }}
      >
        <Form.Item
          label={t('generalConfig.customerIdLabel')}
          name="customer_id"
          rules={[{ required: true, message: t('generalConfig.validationCustomerIdRequired') }]}
          tooltip={t('generalConfig.customerIdTooltip')}
        >
          <Input prefix={<UserOutlined />} placeholder="demo-customer" />
        </Form.Item>

        <Form.Item
          label={t('generalConfig.autoRefresh')}
          name="auto_refresh"
          valuePropName="checked"
          tooltip={t('generalConfig.autoRefreshTooltip')}
        >
          <Switch checkedChildren={t('common.on')} unCheckedChildren={t('common.off')} />
        </Form.Item>

        <Form.Item
          label={t('generalConfig.refreshInterval')}
          name="refresh_interval"
          rules={[{ required: true, message: t('generalConfig.validationRefreshIntervalRequired') }]}
          tooltip={t('generalConfig.refreshIntervalTooltip')}
        >
          <InputNumber min={10} max={300} step={5} style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item>
          <Button type="primary" htmlType="submit" icon={<SaveOutlined />}>
            {t('generalConfig.save')}
          </Button>
        </Form.Item>
      </Form>

      <Divider />
      <Descriptions title={t('generalConfig.currentEnv')} bordered column={1} size="small">
        <Descriptions.Item label={t('generalConfig.apiBaseUrl')}>
          {import.meta.env.VITE_API_BASE_URL || '/api'}
        </Descriptions.Item>
        <Descriptions.Item label={t('generalConfig.currentCustomerId')}>
          <Tag color="blue">{customerId}</Tag>
        </Descriptions.Item>
        <Descriptions.Item label={t('generalConfig.appVersion')}>v1.0.0</Descriptions.Item>
      </Descriptions>
    </Card>
  );
};

export default GeneralConfig;
